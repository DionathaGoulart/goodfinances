package com.finapp.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.content.edit
import com.finapp.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Versão nova publicada no GitHub Releases. */
data class Atualizacao(
    val versao: String,
    val notas: String,
    val urlDownload: String
) {
    /** True quando a release tem APK anexado (dá para baixar e instalar direto). */
    val temApk: Boolean get() = urlDownload.endsWith(".apk", ignoreCase = true)
}

/** Progresso do download do APK novo. */
sealed interface EstadoDownload {
    data object Ocioso : EstadoDownload

    /** [progresso] em 0..1, ou null enquanto o tamanho total é desconhecido. */
    data class Baixando(val progresso: Float?) : EstadoDownload
    data object Erro : EstadoDownload
}

/**
 * Checa no GitHub Releases (repo público, sem autenticação) se existe
 * versão mais nova que a instalada. No máximo uma consulta por dia; quando
 * há atualização, [disponivel] alimenta o dialog na Home com o link do APK.
 */
@Singleton
class AtualizacaoManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("finapp_prefs", Context.MODE_PRIVATE)

    private val _disponivel = MutableStateFlow<Atualizacao?>(null)
    val disponivel: StateFlow<Atualizacao?> = _disponivel.asStateFlow()

    private val _download = MutableStateFlow<EstadoDownload>(EstadoDownload.Ocioso)
    val download: StateFlow<EstadoDownload> = _download.asStateFlow()

    /** Consulta a última release (1x/dia; [forcar] ignora a espera). */
    suspend fun verificar(forcar: Boolean = false) = withContext(Dispatchers.IO) {
        val ultima = prefs.getLong(CHAVE_ULTIMA_CHECAGEM, 0L)
        if (!forcar && System.currentTimeMillis() - ultima < INTERVALO_MILLIS) {
            return@withContext
        }
        runCatching {
            val conexao = (URL(URL_ULTIMA_RELEASE).openConnection() as HttpURLConnection)
                .apply {
                    setRequestProperty("Accept", "application/vnd.github+json")
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }
            val corpo = conexao.inputStream.bufferedReader().use { it.readText() }
            prefs.edit { putLong(CHAVE_ULTIMA_CHECAGEM, System.currentTimeMillis()) }

            val json = JSONObject(corpo)
            val versao = json.getString("tag_name").removePrefix("v").trim()

            // Link direto do APK anexado; sem asset, cai na página da release
            var url = json.optString("html_url")
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk", ignoreCase = true)) {
                        url = asset.getString("browser_download_url")
                        break
                    }
                }
            }

            if (ehMaisNova(versao, BuildConfig.VERSION_NAME)) {
                _disponivel.value = Atualizacao(
                    versao = versao,
                    notas = json.optString("body", ""),
                    urlDownload = url
                )
            }
        }
    }

    /**
     * Baixa o APK da release para o cache e dispara o instalador do sistema.
     * Progresso em [download]; retorna true se o instalador foi aberto.
     */
    suspend fun baixarEInstalar(atualizacao: Atualizacao): Boolean =
        withContext(Dispatchers.IO) {
            if (_download.value is EstadoDownload.Baixando) return@withContext false
            _download.value = EstadoDownload.Baixando(null)
            runCatching {
                val pasta = File(context.cacheDir, PASTA_ATUALIZACOES).apply { mkdirs() }
                pasta.listFiles()?.forEach { it.delete() } // APKs de versões anteriores
                val destino = File(pasta, "goodfinances-${atualizacao.versao}.apk")

                val conexao = (URL(atualizacao.urlDownload).openConnection() as HttpURLConnection)
                    .apply {
                        connectTimeout = 15_000
                        readTimeout = 30_000
                    }
                val total = conexao.contentLengthLong
                conexao.inputStream.use { entrada ->
                    destino.outputStream().use { saida ->
                        val buffer = ByteArray(TAMANHO_BUFFER)
                        var lidos = 0L
                        while (true) {
                            val n = entrada.read(buffer)
                            if (n < 0) break
                            saida.write(buffer, 0, n)
                            lidos += n
                            if (total > 0) {
                                _download.value =
                                    EstadoDownload.Baixando(lidos.toFloat() / total)
                            }
                        }
                    }
                }
                instalar(destino)
                _download.value = EstadoDownload.Ocioso
                true
            }.getOrElse {
                _download.value = EstadoDownload.Erro
                false
            }
        }

    /** Volta ao estado inicial (ex.: usuário fechou o dialog após um erro). */
    fun limparDownload() {
        _download.value = EstadoDownload.Ocioso
    }

    /** Abre o instalador do Android para o APK baixado. */
    private fun instalar(apk: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }
        )
    }

    /** Compara versões semânticas ("1.2.0" > "1.1.9"). */
    private fun ehMaisNova(remota: String, local: String): Boolean {
        val r = remota.split('.').map { it.trim().toIntOrNull() ?: 0 }
        val l = local.split('.').map { it.trim().toIntOrNull() ?: 0 }
        repeat(maxOf(r.size, l.size)) { i ->
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private companion object {
        const val URL_ULTIMA_RELEASE =
            "https://api.github.com/repos/DionathaGoulart/goodfinances/releases/latest"
        const val CHAVE_ULTIMA_CHECAGEM = "atualizacao_ultima_checagem"
        const val INTERVALO_MILLIS = 24L * 60 * 60 * 1000
        const val PASTA_ATUALIZACOES = "atualizacoes"
        const val TAMANHO_BUFFER = 64 * 1024
    }
}
