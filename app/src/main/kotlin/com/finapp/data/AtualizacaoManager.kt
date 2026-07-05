package com.finapp.data

import android.content.Context
import androidx.core.content.edit
import com.finapp.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Versão nova publicada no GitHub Releases. */
data class Atualizacao(
    val versao: String,
    val notas: String,
    val urlDownload: String
)

/**
 * Checa no GitHub Releases (repo público, sem autenticação) se existe
 * versão mais nova que a instalada. No máximo uma consulta por dia; quando
 * há atualização, [disponivel] alimenta o dialog na Home com o link do APK.
 */
@Singleton
class AtualizacaoManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("finapp_prefs", Context.MODE_PRIVATE)

    private val _disponivel = MutableStateFlow<Atualizacao?>(null)
    val disponivel: StateFlow<Atualizacao?> = _disponivel.asStateFlow()

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
    }
}
