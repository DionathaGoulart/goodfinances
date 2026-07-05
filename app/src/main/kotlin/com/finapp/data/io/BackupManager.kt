package com.finapp.data.io

import android.content.Context
import androidx.core.content.edit
import com.finapp.data.PerfilManager
import com.finapp.data.db.entities.Perfil
import com.finapp.data.repository.FinanceRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup automático semanal em JSON:
 * - LOCAL: Android/data/com.finapp/files/backups (4 mais recentes por perfil)
 * - NUVEM: doc privado em usuarios/{uid}/backups/{perfil} no Firestore,
 *   quando o usuário está logado — protege contra perda do aparelho.
 * O restore usa o que for mais novo entre local e nuvem.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FinanceRepository,
    private val perfilManager: PerfilManager,
    private val exportManager: ExportManager,
    private val importManager: ImportManager,
    private val driveBackupManager: DriveBackupManager
) {
    private val prefs = context.getSharedPreferences("finapp_prefs", Context.MODE_PRIVATE)
    private val auth = FirebaseAuth.getInstance()
    private val nuvem = FirebaseFirestore.getInstance()

    private val _ativado = MutableStateFlow(prefs.getBoolean(CHAVE_ATIVADO, false))
    val ativado: StateFlow<Boolean> = _ativado.asStateFlow()

    private val diretorio: File
        get() = (context.getExternalFilesDir(null) ?: context.filesDir)
            .resolve("backups")
            .apply { mkdirs() }

    fun alternar(ativo: Boolean) {
        prefs.edit { putBoolean(CHAVE_ATIVADO, ativo) }
        _ativado.value = ativo
    }

    /** Chamado ao abrir o app: faz backup se estiver ativado e o último tiver 7+ dias. */
    suspend fun executarSeNecessario() {
        if (!_ativado.value) return
        val ultimo = prefs.getLong(CHAVE_ULTIMO, 0L)
        if (System.currentTimeMillis() - ultimo < TimeUnit.DAYS.toMillis(7)) return
        executarAgora()
    }

    /**
     * Faz backup de TODOS os perfis que têm dados (não só o ativo) e
     * remove os antigos (mantém 4 por perfil). Retorna quantos criou.
     */
    suspend fun executarAgora(): Int {
        var criados = 0
        val timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"))

        Perfil.BALDES_DADOS.forEach { perfil ->
            val transacoes = repository.listarTransacoes(perfil)
            if (transacoes.isEmpty()) return@forEach

            val json = exportManager.gerarJsonTexto(
                perfil = perfil,
                transacoes = transacoes,
                categorias = repository.listarCategorias(perfil)
            )
            diretorio.resolve("${prefixo(perfil)}$timestamp.json")
                .writeText(json, Charsets.UTF_8)
            subirParaNuvem(perfil, json)
            criados++

            // Mantém apenas os 4 mais recentes DESTE perfil
            backupsDoPerfil(perfil)
                .sortedByDescending { it.lastModified() }
                .drop(MAXIMO_BACKUPS)
                .forEach { it.delete() }
        }

        prefs.edit { putLong(CHAVE_ULTIMO, System.currentTimeMillis()) }

        // Notas fiscais vão para o Drive do usuário (se ele ativou) —
        // falha silenciosa: tenta de novo no próximo backup semanal
        if (driveBackupManager.ativado.value) {
            runCatching {
                driveBackupManager.sincronizarNotas(
                    repository.listarNotasFiscaisReferenciadas()
                )
            }
        }
        return criados
    }

    /**
     * Restaura o backup mais recente DO PERFIL ATIVO — o mais novo entre
     * o arquivo local e o da nuvem (mescla, sem apagar dados atuais).
     * Retorna quantas transações foram restauradas.
     */
    suspend fun restaurarUltimo(): Int {
        val perfil = perfilManager.perfilDados.value
        val local = backupsDoPerfil(perfil).maxByOrNull { it.lastModified() }
        val daNuvem = baixarDaNuvem(perfil)

        val json = when {
            daNuvem != null && (local == null || daNuvem.criadoEm > local.lastModified()) ->
                daNuvem.json
            local != null -> local.readText(Charsets.UTF_8)
            else -> throw IllegalStateException(
                "Nenhum backup do perfil ${perfil.rotulo} (local ou na nuvem)"
            )
        }

        val dados = importManager.lerTexto(json, perfil)
        return repository.importarDados(
            transacoes = dados.transacoes,
            categorias = dados.categorias,
            perfil = perfil,
            substituir = false
        )
    }

    // ---------- Nuvem ----------

    /** Sobe o JSON para o doc privado do usuário. Fire-and-forget (fila offline). */
    private fun subirParaNuvem(perfil: Perfil, json: String) {
        val uid = auth.currentUser?.uid ?: return
        // Documento do Firestore aguenta 1 MB — acima disso, fica só o local
        if (json.length > LIMITE_NUVEM_BYTES) {
            android.util.Log.w(
                "BackupManager",
                "Backup de ${perfil.name} passou de $LIMITE_NUVEM_BYTES bytes — só local"
            )
            return
        }
        docNuvem(uid, perfil).set(
            mapOf(
                "json" to json,
                "criadoEm" to System.currentTimeMillis()
            )
        )
    }

    private suspend fun baixarDaNuvem(perfil: Perfil): BackupNuvem? {
        val uid = auth.currentUser?.uid ?: return null
        return runCatching {
            val doc = docNuvem(uid, perfil).get().await()
            val json = doc.getString("json") ?: return null
            BackupNuvem(json = json, criadoEm = doc.getLong("criadoEm") ?: 0L)
        }.getOrNull()
    }

    private fun docNuvem(uid: String, perfil: Perfil) =
        nuvem.collection("usuarios").document(uid)
            .collection("backups").document(perfil.name.lowercase())

    private data class BackupNuvem(val json: String, val criadoEm: Long)

    private fun prefixo(perfil: Perfil) = "FinanApp_backup_${perfil.name.lowercase()}_"

    /** Filtra por prefixo exato (o timestamp após o prefixo começa com dígito). */
    private fun backupsDoPerfil(perfil: Perfil): List<File> {
        val prefixo = prefixo(perfil)
        return diretorio.listFiles { f ->
            f.extension == "json" &&
                f.name.startsWith(prefixo) &&
                f.name.removePrefix(prefixo).firstOrNull()?.isDigit() == true
        }?.toList().orEmpty()
    }

    private companion object {
        const val CHAVE_ATIVADO = "backup_automatico"
        const val CHAVE_ULTIMO = "backup_ultimo_millis"
        const val MAXIMO_BACKUPS = 4
        const val LIMITE_NUVEM_BYTES = 900_000
    }
}
