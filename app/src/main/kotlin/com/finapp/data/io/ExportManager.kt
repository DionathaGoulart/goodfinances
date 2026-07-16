package com.finapp.data.io

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.finapp.data.db.entities.Categoria
import com.finapp.data.db.entities.ContaAgendada
import com.finapp.data.db.entities.Meta
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.Transacao
import java.time.LocalDate
import com.finapp.utils.Formatadores
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Gera CSV, JSON, PDF e ZIP de notas e escreve em Uris do Storage Access Framework. */
@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notaFiscalManager: NotaFiscalManager
) {

    // ---------- ZIP (notas fiscais + CSV, para o imposto / contador) ----------

    /**
     * Empacota o CSV das transações (um por ano) e os arquivos de nota
     * fiscal organizados em pastas `notas/{ano}/{mês}` — pronto para
     * baixar no fim do ano e declarar o imposto.
     */
    fun exportarZip(uri: Uri, transacoes: List<Transacao>) {
        val saida = context.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("Não foi possível abrir o arquivo de destino")
        ZipOutputStream(saida.buffered()).use { zip ->
            transacoes
                .groupBy { it.data.year }
                .toSortedMap()
                .forEach { (ano, doAno) ->
                    zip.putNextEntry(ZipEntry("transacoes_$ano.csv"))
                    zip.write(gerarCsvTexto(doAno.sortedBy { it.data }).toByteArray())
                    zip.closeEntry()
                }

            transacoes.filter { it.notaFiscal.isNotBlank() }.forEach { transacao ->
                val arquivo = notaFiscalManager.arquivo(transacao.notaFiscal)
                if (arquivo.exists()) {
                    // Pasta ano/mês + nome legível: data + categoria + nome original
                    val categoria = transacao.categoria
                        .replace(Regex("[^A-Za-z0-9À-ÿ _-]"), "")
                    val mesNome = transacao.data.month
                        .getDisplayName(TextStyle.FULL, Formatadores.LOCALE_BR)
                        .replaceFirstChar { it.uppercase(Formatadores.LOCALE_BR) }
                    val pastaMes = "%02d - %s".format(transacao.data.monthValue, mesNome)
                    zip.putNextEntry(
                        ZipEntry(
                            "notas/${transacao.data.year}/$pastaMes/" +
                                "${transacao.data}_${categoria}_${arquivo.name}"
                        )
                    )
                    arquivo.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

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

    /**
     * [metas]/[contas] só são preenchidos no BACKUP (restauração completa) —
     * o export interoperável (CSV/JSON para o contador) não os inclui. As
     * chaves ficam ausentes quando as listas estão vazias, e os valores dessas
     * seções ficam em CENTAVOS (uso interno do app), diferente das transações.
     */
    fun gerarJsonTexto(
        perfil: Perfil,
        transacoes: List<Transacao>,
        categorias: List<Categoria>,
        metas: List<Meta> = emptyList(),
        contas: List<ContaAgendada> = emptyList()
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
                        // Campos de sync/vínculo — o restore precisa deles
                        if (t.criadoPor.isNotBlank()) put("criadoPor", t.criadoPor)
                        if (t.criadoPorUid.isNotBlank()) put("criadoPorUid", t.criadoPorUid)
                        if (t.transferenciaId.isNotBlank()) {
                            put("transferenciaId", t.transferenciaId)
                        }
                        if (t.notaFiscal.isNotBlank()) put("notaFiscal", t.notaFiscal)
                        // Pendência (fatura/recorrência não paga) sobrevive ao backup
                        if (!t.pago) put("pago", false)
                        // Dia em que a pendência foi paga (o histórico agrupa por ele)
                        t.dataPagamento?.let { put("dataPagamento", it.toString()) }
                        // Vínculo com o cartão e privacidade da visão Membros:
                        // sem eles o restore desagrupa as compras de crédito e
                        // re-espelha lançamentos escondidos
                        if (t.cartaoUuid.isNotBlank()) put("cartaoUuid", t.cartaoUuid)
                        t.dataCompra?.let { put("dataCompra", it.toString()) }
                        if (t.oculto) put("oculto", true)
                        // Vínculo com a recorrência: sem ele, editar/encerrar
                        // a recorrência não alcança as ocorrências restauradas
                        if (t.recorrenciaUuid.isNotBlank()) {
                            put("recorrenciaUuid", t.recorrenciaUuid)
                        }
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
            // Seções de backup (valores em CENTAVOS). Ausentes no export interop.
            if (metas.isNotEmpty()) {
                put("metas", JSONArray().also { arr ->
                    metas.forEach { m ->
                        arr.put(JSONObject().apply {
                            put("uuid", m.uuid)
                            put("nome", m.nome)
                            put("valorAlvo", m.valorAlvo)
                            put("valorGuardado", m.valorGuardado)
                            m.prazo?.let { put("prazo", it.toEpochDay()) }
                            put("cor", m.cor)
                        })
                    }
                })
            }
            if (contas.isNotEmpty()) {
                put("contas", JSONArray().also { arr ->
                    contas.forEach { c ->
                        arr.put(JSONObject().apply {
                            put("uuid", c.uuid)
                            put("descricao", c.descricao)
                            put("valor", c.valor)
                            put("tipo", c.tipo.name.lowercase())
                            put("categoria", c.categoria)
                            put("vencimento", c.vencimento.toEpochDay())
                            put("pago", c.pago)
                        })
                    }
                })
            }
        }
        return raiz.toString(2)
    }

    /** Lê a seção "metas" de um JSON de backup (centavos). Vazio se ausente. */
    fun lerMetasBackup(json: String, perfil: Perfil): List<Meta> {
        val arr = runCatching { JSONObject(json).optJSONArray("metas") }.getOrNull()
            ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Meta(
                uuid = o.optString("uuid").ifBlank { return@mapNotNull null },
                nome = o.optString("nome"),
                valorAlvo = o.optLong("valorAlvo"),
                valorGuardado = o.optLong("valorGuardado"),
                prazo = if (o.has("prazo")) LocalDate.ofEpochDay(o.getLong("prazo")) else null,
                cor = o.optString("cor", "#10B981"),
                perfil = perfil
            )
        }
    }

    /** Lê a seção "contas" de um JSON de backup (centavos). Vazio se ausente. */
    fun lerContasBackup(json: String, perfil: Perfil): List<ContaAgendada> {
        val arr = runCatching { JSONObject(json).optJSONArray("contas") }.getOrNull()
            ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val tipo = runCatching {
                TipoTransacao.valueOf(o.optString("tipo").uppercase())
            }.getOrNull() ?: return@mapNotNull null
            ContaAgendada(
                uuid = o.optString("uuid").ifBlank { return@mapNotNull null },
                descricao = o.optString("descricao"),
                valor = o.optLong("valor"),
                tipo = tipo,
                categoria = o.optString("categoria", "Outros"),
                vencimento = LocalDate.ofEpochDay(o.optLong("vencimento")),
                pago = o.optBoolean("pago", false),
                perfil = perfil
            )
        }
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
        linha("Gerado em $agora pelo GoodFinances", paintCinza)
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
