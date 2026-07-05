package com.finapp.data.io

import android.app.PendingIntent
import android.content.Context
import androidx.core.content.edit
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup dos ARQUIVOS de nota fiscal no Google Drive da conta logada
 * (pasta oculta do app — `appDataFolder` — que não aparece no Drive do
 * usuário nem conta contra outras pastas). Não precisa de Firebase
 * Storage/billing: usa a cota do próprio usuário.
 *
 * O escopo `drive.appdata` é autorizado uma vez via AuthorizationClient;
 * quando o consentimento é necessário, [pedirAutorizacao] devolve um
 * PendingIntent que a UI dispara. Os nomes de arquivo são UUIDs imutáveis,
 * então o sync é só "sobe o que falta lá / baixa o que falta aqui".
 *
 * Limitação de conta: o Drive é pessoal — cada membro da Casa guarda as
 * próprias notas; não existe "Drive da casa".
 */
@Singleton
class DriveBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notaFiscalManager: NotaFiscalManager
) {
    private val prefs = context.getSharedPreferences("finapp_prefs", Context.MODE_PRIVATE)

    private val _ativado = MutableStateFlow(prefs.getBoolean(CHAVE_ATIVADO, false))
    val ativado: StateFlow<Boolean> = _ativado.asStateFlow()

    fun alternar(ativo: Boolean) {
        prefs.edit { putBoolean(CHAVE_ATIVADO, ativo) }
        _ativado.value = ativo
    }

    /**
     * Pede o escopo do Drive. Retorna null se já está autorizado; senão,
     * um PendingIntent com a tela de consentimento do Google (a UI lança
     * e, no OK, chama a sincronização).
     */
    suspend fun pedirAutorizacao(): PendingIntent? {
        val resultado = Identity.getAuthorizationClient(context)
            .authorize(pedidoEscopo())
            .await()
        return if (resultado.hasResolution()) resultado.pendingIntent else null
    }

    /**
     * Sobe para o Drive as notas [referenciadas] que ainda não estão lá.
     * Retorna quantos arquivos subiram. Lança exceção sem autorização.
     */
    suspend fun sincronizarNotas(referenciadas: Collection<String>): Int =
        withContext(Dispatchers.IO) {
            val token = tokenOuErro()
            val remotas = listarRemotas(token).keys
            var enviadas = 0
            referenciadas.filter { it.isNotBlank() && it !in remotas }.forEach { nome ->
                val arquivo = notaFiscalManager.arquivo(nome)
                if (arquivo.exists()) {
                    subir(token, arquivo)
                    enviadas++
                }
            }
            enviadas
        }

    /**
     * Baixa do Drive as notas que não existem neste aparelho (troca de
     * celular / reinstalação). Retorna quantos arquivos baixou.
     */
    suspend fun restaurarNotas(): Int = withContext(Dispatchers.IO) {
        val token = tokenOuErro()
        var baixadas = 0
        listarRemotas(token).forEach { (nome, id) ->
            val destino = notaFiscalManager.arquivo(nome)
            if (!destino.exists()) {
                baixar(token, id, destino)
                baixadas++
            }
        }
        baixadas
    }

    // ---------- Autorização ----------

    private fun pedidoEscopo(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(ESCOPO_APPDATA)))
            .build()

    /** Token de acesso sem UI — se precisar de consentimento, falha. */
    private suspend fun tokenOuErro(): String {
        val resultado = Identity.getAuthorizationClient(context)
            .authorize(pedidoEscopo())
            .await()
        check(!resultado.hasResolution()) { "Google Drive não autorizado" }
        return checkNotNull(resultado.accessToken) { "Google Drive sem token de acesso" }
    }

    // ---------- Drive REST v3 (HttpURLConnection — sem SDK extra) ----------

    /** Mapa nome -> id de tudo que está na pasta do app. */
    private fun listarRemotas(token: String): Map<String, String> {
        val resultado = mutableMapOf<String, String>()
        var pagina: String? = null
        do {
            val query = buildString {
                append("spaces=appDataFolder&fields=nextPageToken,files(id,name)&pageSize=1000")
                pagina?.let { append("&pageToken=${URLEncoder.encode(it, "UTF-8")}") }
            }
            val resposta = requisicao("GET", "$URL_API/files?$query", token)
            val json = JSONObject(resposta)
            val arquivos = json.getJSONArray("files")
            for (i in 0 until arquivos.length()) {
                val arquivo = arquivos.getJSONObject(i)
                resultado[arquivo.getString("name")] = arquivo.getString("id")
            }
            pagina = json.optString("nextPageToken").ifBlank { null }
        } while (pagina != null)
        return resultado
    }

    private fun subir(token: String, arquivo: File) {
        val conexao = abrir("POST", "$URL_UPLOAD/files?uploadType=multipart", token)
        conexao.setRequestProperty("Content-Type", "multipart/related; boundary=$BOUNDARY")
        conexao.doOutput = true
        conexao.outputStream.buffered().use { saida ->
            val metadados = JSONObject()
                .put("name", arquivo.name)
                .put("parents", org.json.JSONArray().put("appDataFolder"))
                .toString()
            saida.write(
                (
                    "--$BOUNDARY\r\n" +
                        "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                        "$metadados\r\n" +
                        "--$BOUNDARY\r\n" +
                        "Content-Type: ${tipoMime(arquivo.name)}\r\n\r\n"
                    ).toByteArray()
            )
            arquivo.inputStream().use { it.copyTo(saida) }
            saida.write("\r\n--$BOUNDARY--".toByteArray())
        }
        lerResposta(conexao)
    }

    private fun baixar(token: String, id: String, destino: File) {
        val conexao = abrir("GET", "$URL_API/files/$id?alt=media", token)
        verificar(conexao)
        conexao.inputStream.use { origem ->
            destino.outputStream().use { origem.copyTo(it) }
        }
    }

    private fun requisicao(metodo: String, url: String, token: String): String {
        val conexao = abrir(metodo, url, token)
        return lerResposta(conexao)
    }

    private fun abrir(metodo: String, url: String, token: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = metodo
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 15_000
            readTimeout = 60_000
        }

    private fun lerResposta(conexao: HttpURLConnection): String {
        verificar(conexao)
        return conexao.inputStream.bufferedReader().use { it.readText() }
    }

    private fun verificar(conexao: HttpURLConnection) {
        val codigo = conexao.responseCode
        if (codigo !in 200..299) {
            val erro = conexao.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw IllegalStateException("Drive respondeu $codigo: ${erro.take(200)}")
        }
    }

    private fun tipoMime(nome: String): String = when {
        nome.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
        nome.endsWith(".png", ignoreCase = true) -> "image/png"
        else -> "image/jpeg"
    }

    private companion object {
        const val CHAVE_ATIVADO = "backup_drive_ativado"
        const val ESCOPO_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
        const val URL_API = "https://www.googleapis.com/drive/v3"
        const val URL_UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val BOUNDARY = "finapp-nota-fiscal"
    }
}
