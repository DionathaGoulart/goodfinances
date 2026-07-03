package com.finapp.data.io

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.finapp.data.db.entities.Categoria
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.Transacao
import com.finapp.utils.Formatadores
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Gera CSV, JSON e PDF dos dados e escreve em Uris do Storage Access Framework. */
@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ---------- CSV ----------

    fun gerarCsvTexto(transacoes: List<Transacao>): String = buildString {
        appendLine("data,descricao,valor,tipo,categoria")
        transacoes.forEach { t ->
            appendLine(
                listOf(
                    Formatadores.dataCurta(t.data),
                    campoCsv(t.descricao),
                    String.format(Locale.US, "%.2f", t.valor / 100.0),
                    t.tipo.name.lowercase(),
                    campoCsv(t.categoria)
                ).joinToString(",")
            )
        }
    }

    fun exportarCsv(uri: Uri, transacoes: List<Transacao>) =
        escrever(uri, gerarCsvTexto(transacoes))

    /** Aspas em campos com vírgula/aspas, padrão CSV. */
    private fun campoCsv(valor: String): String =
        if (valor.contains(',') || valor.contains('"') || valor.contains('\n')) {
            "\"${valor.replace("\"", "\"\"")}\""
        } else {
            valor
        }

    // ---------- JSON ----------

    fun gerarJsonTexto(
        perfil: Perfil,
        transacoes: List<Transacao>,
        categorias: List<Categoria>
    ): String {
        val raiz = JSONObject().apply {
            put("versao", "1.0")
            put("dataExport", LocalDateTime.now().toString())
            put("perfil", perfil.name.lowercase())
            put("transacoes", JSONArray().also { arr ->
                transacoes.forEach { t ->
                    arr.put(JSONObject().apply {
                        put("id", t.id)
                        put("uuid", t.uuid)
                        put("data", t.data.toString())
                        put("descricao", t.descricao)
                        // Exporta em reais (decimal) para interoperabilidade
                        put("valor", t.valor / 100.0)
                        put("tipo", t.tipo.name.lowercase())
                        put("categoria", t.categoria)
                    })
                }
            })
            put("categorias", JSONArray().also { arr ->
                categorias.forEach { c ->
                    arr.put(JSONObject().apply {
                        put("uuid", c.uuid)
                        put("nome", c.nome)
                        put("tipo", c.tipo.name.lowercase())
                        put("cor", c.cor)
                        put("arquivada", c.arquivada)
                    })
                }
            })
        }
        return raiz.toString(2)
    }

    fun exportarJson(
        uri: Uri,
        perfil: Perfil,
        transacoes: List<Transacao>,
        categorias: List<Categoria>
    ) = escrever(uri, gerarJsonTexto(perfil, transacoes, categorias))

    // ---------- PDF ----------

    fun exportarPdf(uri: Uri, perfil: Perfil, transacoes: List<Transacao>) {
        val documento = PdfDocument()

        val paintTitulo = Paint().apply {
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val paintSecao = Paint().apply {
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val paintCorpo = Paint().apply { textSize = 10f }
        val paintCinza = Paint().apply { textSize = 9f; color = 0xFF666666.toInt() }

        val margem = 40f
        val limiteY = 800f
        var numeroPagina = 1
        var pagina = documento.startPage(
            PdfDocument.PageInfo.Builder(595, 842, numeroPagina).create()
        )
        var y = margem + 10f

        fun novaPagina() {
            documento.finishPage(pagina)
            numeroPagina++
            pagina = documento.startPage(
                PdfDocument.PageInfo.Builder(595, 842, numeroPagina).create()
            )
            y = margem + 10f
        }

        fun linha(texto: String, paint: Paint, recuo: Float = 0f) {
            if (y > limiteY) novaPagina()
            pagina.canvas.drawText(texto, margem + recuo, y, paint)
            y += paint.textSize + 7f
        }

        val agora = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Formatadores.LOCALE_BR))

        // Cabeçalho
        linha("Relatório Financeiro — ${perfil.rotulo}", paintTitulo)
        linha("Gerado em $agora pelo FinanApp", paintCinza)
        y += 12f

        // Resumo
        val ganhos = transacoes.filter { it.tipo == TipoTransacao.GANHO }.sumOf { it.valor }
        val gastos = transacoes.filter { it.tipo == TipoTransacao.GASTO }.sumOf { it.valor }
        linha("Resumo", paintSecao)
        linha("Ganhos: ${Formatadores.moeda(ganhos)}", paintCorpo, recuo = 10f)
        linha("Gastos: ${Formatadores.moeda(gastos)}", paintCorpo, recuo = 10f)
        linha("Saldo: ${Formatadores.moeda(ganhos - gastos)}", paintCorpo, recuo = 10f)
        y += 12f

        // Gastos por categoria
        linha("Gastos por Categoria", paintSecao)
        transacoes
            .filter { it.tipo == TipoTransacao.GASTO }
            .groupBy { it.categoria }
            .mapValues { (_, lista) -> lista.sumOf { it.valor } }
            .entries
            .sortedByDescending { it.value }
            .forEach { (categoria, total) ->
                linha("$categoria: ${Formatadores.moeda(total)}", paintCorpo, recuo = 10f)
            }
        y += 12f

        // Histórico
        linha("Histórico de Transações (${transacoes.size})", paintSecao)
        transacoes.forEach { t ->
            val sinal = if (t.tipo == TipoTransacao.GASTO) "-" else "+"
            val descricao = t.descricao.ifBlank { t.categoria }.take(45)
            linha(
                "${Formatadores.dataCurta(t.data)}  $descricao (${t.categoria})  " +
                    "$sinal${Formatadores.moeda(t.valor)}",
                paintCorpo,
                recuo = 10f
            )
        }

        documento.finishPage(pagina)
        abrirSaida(uri).use { saida -> documento.writeTo(saida) }
        documento.close()
    }

    // ---------- Escrita ----------

    private fun escrever(uri: Uri, conteudo: String) {
        abrirSaida(uri).bufferedWriter(Charsets.UTF_8).use { it.write(conteudo) }
    }

    private fun abrirSaida(uri: Uri) =
        context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("Não foi possível abrir o arquivo para escrita")
}
