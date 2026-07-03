package com.finapp.data.io

import com.finapp.data.db.entities.Categoria
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.Transacao
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

/** Resultado da leitura de um arquivo de importação (CSV ou JSON). */
data class DadosImportados(
    val transacoes: List<Transacao>,
    val categorias: List<Categoria>
)

/**
 * Parser puro (sem Android) dos formatos de importação — testável em JUnit.
 * Valores monetários são convertidos para centavos.
 */
@Singleton
class ParserImportacao @Inject constructor() {

    private val formatoBr = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /** Detecta o formato pelo conteúdo (JSON começa com '{'). */
    fun lerTexto(texto: String, perfil: Perfil): DadosImportados =
        if (texto.trimStart().startsWith("{")) lerJson(texto, perfil)
        else lerCsv(texto, perfil)

    // ---------- CSV ----------

    private fun lerCsv(texto: String, perfil: Perfil): DadosImportados {
        val linhas = texto.lineSequence().filter { it.isNotBlank() }.toList()
        require(linhas.isNotEmpty()) { "arquivo vazio" }

        val corpo = if (linhas.first().lowercase().startsWith("data")) linhas.drop(1) else linhas
        val transacoes = corpo.mapIndexed { indice, linha ->
            val campos = dividirCsv(linha)
            require(campos.size >= 5) { "linha ${indice + 2} tem menos de 5 colunas" }
            Transacao(
                data = parseData(campos[0], indice + 2),
                descricao = campos[1].trim(),
                valor = parseValorCentavos(campos[2], indice + 2),
                tipo = parseTipo(campos[3]),
                categoria = campos[4].trim().ifBlank { "Outros" },
                perfil = perfil
            )
        }
        return DadosImportados(transacoes, emptyList())
    }

    /** Divide uma linha CSV respeitando campos entre aspas. */
    private fun dividirCsv(linha: String): List<String> {
        val campos = mutableListOf<String>()
        val atual = StringBuilder()
        var dentroDeAspas = false
        var i = 0
        while (i < linha.length) {
            val c = linha[i]
            when {
                c == '"' && dentroDeAspas && i + 1 < linha.length && linha[i + 1] == '"' -> {
                    atual.append('"'); i++
                }
                c == '"' -> dentroDeAspas = !dentroDeAspas
                c == ',' && !dentroDeAspas -> {
                    campos.add(atual.toString()); atual.clear()
                }
                else -> atual.append(c)
            }
            i++
        }
        campos.add(atual.toString())
        return campos
    }

    // ---------- JSON ----------

    private fun lerJson(texto: String, perfil: Perfil): DadosImportados {
        val raiz = runCatching { JSONObject(texto) }
            .getOrElse { throw IllegalArgumentException("JSON malformado") }

        val arrTransacoes = raiz.optJSONArray("transacoes")
            ?: throw IllegalArgumentException("JSON sem a lista \"transacoes\"")

        val transacoes = (0 until arrTransacoes.length()).map { i ->
            val obj = arrTransacoes.getJSONObject(i)
            val centavos = (obj.getDouble("valor") * 100).roundToLong()
            require(centavos > 0) { "transação ${i + 1} com valor inválido" }
            Transacao(
                data = parseData(obj.getString("data"), i + 1),
                descricao = obj.optString("descricao", ""),
                valor = centavos,
                tipo = parseTipo(obj.getString("tipo")),
                categoria = obj.optString("categoria", "Outros").ifBlank { "Outros" },
                perfil = perfil
            )
        }

        val arrCategorias = raiz.optJSONArray("categorias")
        val categorias = if (arrCategorias == null) emptyList() else {
            (0 until arrCategorias.length()).map { i ->
                val obj = arrCategorias.getJSONObject(i)
                Categoria(
                    nome = obj.getString("nome"),
                    tipo = parseTipo(obj.getString("tipo")),
                    cor = obj.optString("cor", "#6B7280"),
                    perfil = perfil,
                    arquivada = obj.optBoolean("arquivada", false)
                )
            }
        }
        return DadosImportados(transacoes, categorias)
    }

    // ---------- Parsers de campo ----------

    /** Aceita dd/MM/yyyy e yyyy-MM-dd. */
    private fun parseData(texto: String, linha: Int): LocalDate {
        val limpo = texto.trim()
        return runCatching { LocalDate.parse(limpo, formatoBr) }
            .recoverCatching { LocalDate.parse(limpo) }
            .getOrElse { throw IllegalArgumentException("data inválida na linha $linha: \"$limpo\"") }
    }

    /** Aceita "1234.56", "1234,56" e "R$ 1.234,56". Retorna centavos. */
    private fun parseValorCentavos(texto: String, linha: Int): Long {
        var limpo = texto.trim().removePrefix("R$").trim().replace(" ", "")
        if (limpo.contains(',')) {
            limpo = limpo.replace(".", "").replace(',', '.')
        }
        val reais = limpo.toDoubleOrNull()
            ?: throw IllegalArgumentException("valor inválido na linha $linha: \"$texto\"")
        val centavos = (reais * 100).roundToLong()
        require(centavos > 0) { "valor deve ser maior que zero na linha $linha" }
        return centavos
    }

    private fun parseTipo(texto: String): TipoTransacao =
        if (texto.trim().uppercase().contains("GANHO")) TipoTransacao.GANHO
        else TipoTransacao.GASTO
}
