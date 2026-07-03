package com.finapp.data.sync

import android.content.Context
import androidx.core.content.edit
import com.finapp.data.db.CategoriaDao
import com.finapp.data.db.TransacaoDao
import com.finapp.data.db.entities.Categoria
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.Transacao
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sincroniza transações e categorias do perfil CASA com o Firestore
 * (`casas/{id}/transacoes|categorias/{uuid}`).
 *
 * - PULL: snapshot listeners aplicam docs remotos no Room quando o
 *   `atualizadoEm` remoto é mais novo ("última edição vence").
 * - PUSH: observa a última modificação local e sobe (com debounce) as
 *   linhas alteradas desde a marca por casa. Escritas offline ficam na
 *   fila durável do Firestore, então o push é fire-and-forget.
 * - Deleções viajam como tombstones (`deletado = true`), nunca como
 *   remoção de documento.
 */
@OptIn(FlowPreview::class)
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext context: Context,
    private val casaManager: CasaManager,
    private val transacaoDao: TransacaoDao,
    private val categoriaDao: CategoriaDao
) {
    private val db = FirebaseFirestore.getInstance()
    private val prefs = context.getSharedPreferences("finapp_prefs", Context.MODE_PRIVATE)
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val trabalhos = mutableListOf<Job>()
    private val listeners = mutableListOf<ListenerRegistration>()

    /** Chamado uma vez, no Application. Liga/desliga conforme a casa ativa. */
    fun iniciar() {
        escopo.launch {
            casaManager.carregarCasa()
            casaManager.casa.collect { casa ->
                pararSync()
                if (casa != null) iniciarSync(casa.id)
            }
        }
    }

    private fun iniciarSync(casaId: String) {
        // ---------- PULL (tempo real) ----------
        listeners += transacoesRef(casaId).addSnapshotListener { snapshot, erro ->
            if (erro != null || snapshot == null) return@addSnapshotListener
            val docs = snapshot.documentChanges
                .filter { it.type != DocumentChange.Type.REMOVED }
                .map { it.document }
            if (docs.isNotEmpty()) {
                escopo.launch { docs.forEach { aplicarTransacaoRemota(it) } }
            }
        }
        listeners += categoriasRef(casaId).addSnapshotListener { snapshot, erro ->
            if (erro != null || snapshot == null) return@addSnapshotListener
            val docs = snapshot.documentChanges
                .filter { it.type != DocumentChange.Type.REMOVED }
                .map { it.document }
            if (docs.isNotEmpty()) {
                escopo.launch { docs.forEach { aplicarCategoriaRemota(it) } }
            }
        }

        // ---------- PUSH (reativo, com debounce) ----------
        trabalhos += escopo.launch {
            transacaoDao.observarUltimaModificacao(Perfil.CASA)
                .debounce(1_500)
                .collect { runCatching { empurrarTransacoes(casaId) } }
        }
        trabalhos += escopo.launch {
            categoriaDao.observarUltimaModificacao(Perfil.CASA)
                .debounce(1_500)
                .collect { runCatching { empurrarCategorias(casaId) } }
        }
    }

    private fun pararSync() {
        listeners.forEach { it.remove() }
        listeners.clear()
        trabalhos.forEach { it.cancel() }
        trabalhos.clear()
    }

    // ---------- PUSH ----------

    private suspend fun empurrarTransacoes(casaId: String) {
        val chave = "sync_marca_transacoes_$casaId"
        val marca = prefs.getLong(chave, 0L)
        val modificadas = transacaoDao.listarModificadas(Perfil.CASA, marca)
        if (modificadas.isEmpty()) return

        modificadas.chunked(400).forEach { lote ->
            val batch = db.batch()
            lote.forEach { t ->
                batch.set(
                    transacoesRef(casaId).document(t.uuid),
                    mapOf(
                        "valor" to t.valor,
                        "tipo" to t.tipo.name,
                        "categoria" to t.categoria,
                        "descricao" to t.descricao,
                        "data" to t.data.toEpochDay(),
                        "atualizadoEm" to t.atualizadoEm,
                        "deletado" to t.deletado,
                        "criadoPor" to t.criadoPor
                    )
                )
            }
            // Fire-and-forget: offline, a escrita fica na fila durável do SDK
            batch.commit()
        }
        prefs.edit { putLong(chave, modificadas.maxOf { it.atualizadoEm }) }
    }

    private suspend fun empurrarCategorias(casaId: String) {
        val chave = "sync_marca_categorias_$casaId"
        val marca = prefs.getLong(chave, 0L)
        val modificadas = categoriaDao.listarModificadas(Perfil.CASA, marca)
        if (modificadas.isEmpty()) return

        modificadas.chunked(400).forEach { lote ->
            val batch = db.batch()
            lote.forEach { c ->
                batch.set(
                    categoriasRef(casaId).document(c.uuid),
                    mapOf(
                        "nome" to c.nome,
                        "tipo" to c.tipo.name,
                        "cor" to c.cor,
                        "arquivada" to c.arquivada,
                        "atualizadoEm" to c.atualizadoEm,
                        "deletado" to c.deletado
                    )
                )
            }
            batch.commit()
        }
        prefs.edit { putLong(chave, modificadas.maxOf { it.atualizadoEm }) }
    }

    // ---------- PULL ----------

    private suspend fun aplicarTransacaoRemota(doc: DocumentSnapshot) {
        val remotaAtualizadaEm = doc.getLong("atualizadoEm") ?: return
        val local = transacaoDao.obterPorUuid(doc.id)
        // Última edição vence; empate = mantém o local (evita eco)
        if (local != null && local.atualizadoEm >= remotaAtualizadaEm) return

        val transacao = Transacao(
            id = local?.id ?: 0,
            uuid = doc.id,
            valor = doc.getLong("valor") ?: return,
            tipo = parseTipo(doc.getString("tipo")) ?: return,
            categoria = doc.getString("categoria") ?: "Outros",
            descricao = doc.getString("descricao") ?: "",
            data = LocalDate.ofEpochDay(doc.getLong("data") ?: return),
            perfil = Perfil.CASA,
            atualizadoEm = remotaAtualizadaEm,
            deletado = doc.getBoolean("deletado") ?: false,
            criadoPor = doc.getString("criadoPor") ?: ""
        )
        if (local == null) transacaoDao.inserir(transacao) else transacaoDao.atualizar(transacao)
    }

    private suspend fun aplicarCategoriaRemota(doc: DocumentSnapshot) {
        val remotaAtualizadaEm = doc.getLong("atualizadoEm") ?: return
        val local = categoriaDao.obterPorUuid(doc.id)
        if (local != null && local.atualizadoEm >= remotaAtualizadaEm) return

        val categoria = Categoria(
            id = local?.id ?: 0,
            uuid = doc.id,
            nome = doc.getString("nome") ?: return,
            tipo = parseTipo(doc.getString("tipo")) ?: return,
            cor = doc.getString("cor") ?: "#6B7280",
            perfil = Perfil.CASA,
            arquivada = doc.getBoolean("arquivada") ?: false,
            atualizadoEm = remotaAtualizadaEm,
            deletado = doc.getBoolean("deletado") ?: false
        )
        if (local == null) categoriaDao.inserir(categoria) else categoriaDao.atualizar(categoria)
    }

    // ---------- Auxiliares ----------

    private fun transacoesRef(casaId: String): CollectionReference =
        db.collection("casas").document(casaId).collection("transacoes")

    private fun categoriasRef(casaId: String): CollectionReference =
        db.collection("casas").document(casaId).collection("categorias")

    private fun parseTipo(nome: String?): TipoTransacao? =
        runCatching { TipoTransacao.valueOf(nome ?: "") }.getOrNull()
}
