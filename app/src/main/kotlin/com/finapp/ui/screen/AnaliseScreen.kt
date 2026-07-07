package com.finapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finapp.data.db.entities.TipoEmpresa
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.Transacao
import com.finapp.ui.component.GraficoBarras
import com.finapp.ui.component.GraficoLinha
import com.finapp.ui.component.GraficoPizza
import com.finapp.ui.component.TransacaoItem
import com.finapp.ui.theme.BluAccent
import com.finapp.ui.theme.OrangeAlert
import com.finapp.ui.theme.RedExpense
import com.finapp.utils.Formatadores
import com.finapp.utils.PeriodoFiltro
import com.finapp.viewmodel.AnaliseViewModel
import com.finapp.viewmodel.Fatura
import com.finapp.viewmodel.Insight
import com.finapp.viewmodel.OrcamentoCategoria
import com.finapp.viewmodel.PainelFiscal
import com.finapp.viewmodel.TipoInsight
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.TextStyle
import kotlin.math.roundToInt

/** Qual card de estatística está com o detalhamento aberto. */
private enum class DetalheEstatistica { GASTO_MEDIO, MAIOR_GASTO, MAIOR_GANHO, CATEGORIA_TOP }

/** Tela de Análise: gráficos e estatísticas do período. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnaliseScreen(viewModel: AnaliseViewModel = hiltViewModel()) {
    val filtro by viewModel.filtro.collectAsStateWithLifecycle()
    val fatias by viewModel.somasPorCategoria.collectAsStateWithLifecycle()
    val tipoCategoria by viewModel.tipoCategoria.collectAsStateWithLifecycle()
    val series by viewModel.seriesMensais.collectAsStateWithLifecycle()
    val estatisticas by viewModel.estatisticas.collectAsStateWithLifecycle()
    val painelFiscal by viewModel.painelFiscal.collectAsStateWithLifecycle()
    val tipoEmpresa by viewModel.tipoEmpresa.collectAsStateWithLifecycle()
    val orcamentos by viewModel.orcamentos.collectAsStateWithLifecycle()
    val transacoesPeriodo by viewModel.transacoesDoPeriodo.collectAsStateWithLifecycle()
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    val faturas by viewModel.faturas.collectAsStateWithLifecycle()

    var rangePickerAberto by remember { mutableStateOf(false) }
    var detalheAberto by remember { mutableStateOf<DetalheEstatistica?>(null) }
    var categoriaDetalhe by remember { mutableStateOf<String?>(null) }
    var faturaDetalhe by remember { mutableStateOf<Fatura?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "ANÁLISE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ---------- Filtro de período ----------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PeriodoFiltro.entries.forEach { opcao ->
                FilterChip(
                    selected = filtro == opcao,
                    onClick = {
                        if (opcao == PeriodoFiltro.PERSONALIZADO) {
                            rangePickerAberto = true
                        } else {
                            viewModel.alterarPeriodo(opcao)
                        }
                    },
                    label = { Text(opcao.rotulo) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BluAccent,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ---------- Insights do mês (variação vs mês anterior) ----------
        if (insights.isNotEmpty()) {
            SecaoInsights(insights = insights)
            Spacer(modifier = Modifier.height(20.dp))
        }

        // ---------- Painel fiscal (só nos contextos de empresa) ----------
        painelFiscal?.let { painel ->
            PainelFiscalCard(painel = painel, tipoEmpresa = tipoEmpresa)
            Spacer(modifier = Modifier.height(20.dp))
        }

        // ---------- Orçamentos do mês (categorias com teto definido) ----------
        if (orcamentos.isNotEmpty()) {
            SecaoOrcamentos(orcamentos = orcamentos)
            Spacer(modifier = Modifier.height(20.dp))
        }

        // ---------- Faturas em aberto dos cartões ----------
        if (faturas.isNotEmpty()) {
            SecaoFaturas(faturas = faturas, onVerFatura = { faturaDetalhe = it })
            Spacer(modifier = Modifier.height(20.dp))
        }

        // ---------- Pizza: ganhos/gastos por categoria ----------
        val tituloPizza = if (tipoCategoria == TipoTransacao.GASTO) {
            "GASTOS POR CATEGORIA"
        } else {
            "GANHOS POR CATEGORIA"
        }
        SecaoGrafico(titulo = tituloPizza) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = tipoCategoria == TipoTransacao.GASTO,
                    onClick = { viewModel.alterarTipoCategoria(TipoTransacao.GASTO) },
                    label = { Text("Gastos") }
                )
                FilterChip(
                    selected = tipoCategoria == TipoTransacao.GANHO,
                    onClick = { viewModel.alterarTipoCategoria(TipoTransacao.GANHO) },
                    label = { Text("Ganhos") }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            GraficoPizza(
                fatias = fatias,
                onVerCategoria = { categoriaDetalhe = it }
            )
        }

        // ---------- Linha: ganhos vs gastos (6 meses) ----------
        SecaoGrafico(titulo = "GANHOS VS GASTOS — ÚLTIMOS 6 MESES") {
            GraficoLinha(series = series)
        }

        // ---------- Barras: comparativo mensal ----------
        SecaoGrafico(titulo = "COMPARATIVO MENSAL") {
            GraficoBarras(series = series)
        }

        // ---------- Estatísticas rápidas ----------
        Text(
            text = "ESTATÍSTICAS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EstatisticaCard(
                titulo = "Gasto Médio Diário",
                valor = Formatadores.moeda(estatisticas.gastoMedioDiario),
                modifier = Modifier.weight(1f),
                onClick = { detalheAberto = DetalheEstatistica.GASTO_MEDIO }
            )
            EstatisticaCard(
                titulo = "Maior Gasto",
                valor = estatisticas.maiorGasto?.let { Formatadores.moeda(it.valor) } ?: "—",
                detalhe = estatisticas.maiorGasto?.categoria,
                modifier = Modifier.weight(1f),
                onClick = { detalheAberto = DetalheEstatistica.MAIOR_GASTO }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EstatisticaCard(
                titulo = "Maior Ganho",
                valor = estatisticas.maiorGanho?.let { Formatadores.moeda(it.valor) } ?: "—",
                detalhe = estatisticas.maiorGanho?.categoria,
                modifier = Modifier.weight(1f),
                onClick = { detalheAberto = DetalheEstatistica.MAIOR_GANHO }
            )
            EstatisticaCard(
                titulo = "Categoria Top Gasto",
                valor = estatisticas.categoriaMaiorGasto ?: "—",
                modifier = Modifier.weight(1f),
                onClick = { detalheAberto = DetalheEstatistica.CATEGORIA_TOP }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // ---------- Detalhamento de uma estatística (bottom sheet) ----------
    detalheAberto?.let { qual ->
        DetalheEstatisticaSheet(
            qual = qual,
            transacoes = transacoesPeriodo,
            gastoMedioDiario = estatisticas.gastoMedioDiario,
            onFechar = { detalheAberto = null }
        )
    }

    // ---------- Lançamentos de uma categoria (toque na fatia da pizza) ----------
    categoriaDetalhe?.let { categoria ->
        CategoriaDetalheSheet(
            categoria = categoria,
            transacoes = transacoesPeriodo,
            tipo = tipoCategoria,
            onFechar = { categoriaDetalhe = null }
        )
    }

    // ---------- Itens de uma fatura (toque no cartão) ----------
    faturaDetalhe?.let { fatura ->
        FaturaDetalheSheet(fatura = fatura, onFechar = { faturaDetalhe = null })
    }

    // ---------- Período customizado ----------
    if (rangePickerAberto) {
        val estadoRange = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { rangePickerAberto = false },
            confirmButton = {
                TextButton(
                    enabled = estadoRange.selectedEndDateMillis != null,
                    onClick = {
                        val inicioMillis = estadoRange.selectedStartDateMillis
                        val fimMillis = estadoRange.selectedEndDateMillis
                        if (inicioMillis != null && fimMillis != null) {
                            viewModel.definirPeriodoCustom(
                                inicio = Instant.ofEpochMilli(inicioMillis)
                                    .atZone(ZoneOffset.UTC).toLocalDate(),
                                fim = Instant.ofEpochMilli(fimMillis)
                                    .atZone(ZoneOffset.UTC).toLocalDate()
                            )
                        }
                        rangePickerAberto = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { rangePickerAberto = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DateRangePicker(
                state = estadoRange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                title = {
                    Text(
                        text = "Selecione o período",
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                    )
                }
            )
        }
    }
}

/** Limite anual de faturamento do MEI, em centavos (R$ 81.000,00). */
private const val LIMITE_MEI_CENTAVOS = 8_100_000L

/** Faturamento do ano/mês + limite do MEI + lembrete do DAS. */
@Composable
private fun PainelFiscalCard(painel: PainelFiscal, tipoEmpresa: TipoEmpresa?) {
    val rotulo = tipoEmpresa?.let { " — ${it.rotulo}" }.orEmpty()
    Text(
        text = "PAINEL FISCAL$rotulo",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Faturamento do ano",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = Formatadores.moeda(painel.faturamentoAno),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Faturamento do mês",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = Formatadores.moeda(painel.faturamentoMes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Limite anual: exclusivo do MEI (CNPJ não tem teto fixo)
            if (tipoEmpresa == TipoEmpresa.MEI) {
                Spacer(modifier = Modifier.height(12.dp))
                val fracao =
                    (painel.faturamentoAno.toDouble() / LIMITE_MEI_CENTAVOS).toFloat()
                val corBarra = when {
                    fracao >= 1f -> RedExpense
                    fracao >= 0.8f -> OrangeAlert
                    else -> MaterialTheme.colorScheme.primary
                }
                LinearProgressIndicator(
                    progress = { fracao.coerceIn(0f, 1f) },
                    color = corBarra,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${(fracao * 100).roundToInt()}% do limite anual do MEI " +
                        "(${Formatadores.moeda(LIMITE_MEI_CENTAVOS)})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (fracao >= 0.8f) {
                    Text(
                        text = if (fracao >= 1f) {
                            "Limite estourado — procure seu contador sobre o desenquadramento"
                        } else {
                            "Atenção: você está chegando perto do limite do MEI"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (fracao >= 1f) RedExpense else OrangeAlert
                    )
                }
            }

            // Lembrete do DAS (MEI e Simples Nacional vencem dia 20)
            Spacer(modifier = Modifier.height(12.dp))
            val guia = if (tipoEmpresa == TipoEmpresa.MEI) "DAS-MEI" else "DAS (Simples Nacional)"
            val mesNome = painel.hoje.month
                .getDisplayName(TextStyle.FULL, Formatadores.LOCALE_BR)
            val dia = painel.hoje.dayOfMonth
            val (textoDas, corDas) = when {
                dia == 20 -> "$guia de $mesNome vence HOJE" to RedExpense
                dia < 20 -> {
                    val texto = "$guia de $mesNome vence dia 20 — faltam ${20 - dia} dias"
                    texto to if (20 - dia <= 5) OrangeAlert else {
                        null // cor padrão aplicada abaixo
                    }
                }
                else -> "$guia de $mesNome venceu dia 20" to null
            }
            Text(
                text = textoDas,
                style = MaterialTheme.typography.bodyMedium,
                color = corDas ?: MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Barras de progresso das categorias de gasto com orçamento definido. */
@Composable
private fun SecaoOrcamentos(orcamentos: List<OrcamentoCategoria>) {
    Text(
        text = "ORÇAMENTOS DO MÊS",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            orcamentos.forEachIndexed { indice, orcamento ->
                if (indice > 0) Spacer(modifier = Modifier.height(14.dp))

                val fracao = (orcamento.gastoMes.toDouble() / orcamento.orcamento).toFloat()
                val corBarra = when {
                    fracao >= 1f -> RedExpense
                    fracao >= 0.8f -> OrangeAlert
                    else -> runCatching { Color(orcamento.cor.toColorInt()) }
                        .getOrDefault(MaterialTheme.colorScheme.primary)
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = orcamento.nome,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${Formatadores.moeda(orcamento.gastoMes)} / " +
                            Formatadores.moeda(orcamento.orcamento),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (fracao >= 1f) RedExpense
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { fracao.coerceIn(0f, 1f) },
                    color = corBarra,
                    modifier = Modifier.fillMaxWidth()
                )
                if (fracao >= 1f) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Estourou em " +
                            Formatadores.moeda(orcamento.gastoMes - orcamento.orcamento),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RedExpense
                    )
                }
            }
        }
    }
}

@Composable
private fun SecaoGrafico(
    titulo: String,
    conteudo: @Composable () -> Unit
) {
    Text(
        text = titulo,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            conteudo()
        }
    }
    Spacer(modifier = Modifier.height(20.dp))
}

/** Altura fixa para os 4 cards de estatística ficarem idênticos. */
private val ALTURA_CARD_ESTATISTICA = 120.dp

/** Linha "nome ......... valor" do detalhamento, com % opcional. */
@Composable
private fun LinhaValor(
    nome: String,
    valor: String,
    modifier: Modifier = Modifier,
    fracao: Float? = null,
    corValor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = nome,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (fracao != null) {
            Text(
                text = "${(fracao * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
        Text(
            text = valor,
            style = MaterialTheme.typography.bodyLarge,
            color = corValor
        )
    }
}

/** Detalhamento de um card de estatística (aberto ao tocar no card). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetalheEstatisticaSheet(
    qual: DetalheEstatistica,
    transacoes: List<Transacao>,
    gastoMedioDiario: Long,
    onFechar: () -> Unit
) {
    val estado = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onFechar, sheetState = estado) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            val gastos = remember(transacoes) {
                transacoes.filter { it.tipo == TipoTransacao.GASTO }
            }
            val ganhos = remember(transacoes) {
                transacoes.filter { it.tipo == TipoTransacao.GANHO }
            }

            when (qual) {
                DetalheEstatistica.GASTO_MEDIO -> {
                    TituloDetalhe("Gasto médio diário")
                    val total = gastos.sumOf { it.valor }
                    LinhaValor("Média por dia", Formatadores.moeda(gastoMedioDiario))
                    LinhaValor(
                        "Total gasto no período",
                        Formatadores.moeda(total),
                        corValor = RedExpense
                    )
                    if (gastos.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SubtituloDetalhe("Dias em que mais gastou")
                        gastos.groupBy { it.data }
                            .map { (dia, lista) -> dia to lista.sumOf { it.valor } }
                            .sortedByDescending { it.second }
                            .take(10)
                            .forEach { (dia, soma) ->
                                LinhaValor(
                                    Formatadores.dataCurta(dia),
                                    Formatadores.moeda(soma)
                                )
                            }
                    }
                }

                DetalheEstatistica.MAIOR_GASTO -> {
                    TituloDetalhe("Maiores gastos")
                    ListaTransacoes(gastos.sortedByDescending { it.valor }.take(15))
                }

                DetalheEstatistica.MAIOR_GANHO -> {
                    TituloDetalhe("Maiores ganhos")
                    ListaTransacoes(ganhos.sortedByDescending { it.valor }.take(15))
                }

                DetalheEstatistica.CATEGORIA_TOP -> {
                    TituloDetalhe("Gastos por categoria")
                    val total = gastos.sumOf { it.valor }.coerceAtLeast(1)
                    val ranking = gastos.groupBy { it.categoria }
                        .map { (cat, lista) -> cat to lista.sumOf { it.valor } }
                        .sortedByDescending { it.second }
                    if (ranking.isEmpty()) {
                        Text(
                            text = "Nenhum gasto no período",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        ranking.forEach { (cat, soma) ->
                            LinhaValor(
                                nome = cat,
                                valor = Formatadores.moeda(soma),
                                fracao = soma.toDouble().toFloat() / total,
                                corValor = RedExpense
                            )
                        }
                        val topo = ranking.first().first
                        Spacer(modifier = Modifier.height(12.dp))
                        SubtituloDetalhe("Lançamentos em \"$topo\"")
                        ListaTransacoes(
                            gastos.filter { it.categoria == topo }
                                .sortedByDescending { it.valor }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TituloDetalhe(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SubtituloDetalhe(texto: String) {
    Text(
        text = texto.uppercase(Formatadores.LOCALE_BR),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun ListaTransacoes(transacoes: List<Transacao>) {
    if (transacoes.isEmpty()) {
        Text(
            text = "Nada no período",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    transacoes.forEach { TransacaoItem(transacao = it) }
}

/** Card de insights do mês: variações relevantes vs o mês anterior. */
@Composable
private fun SecaoInsights(insights: List<Insight>) {
    Text(
        text = "DO SEU MÊS",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            insights.forEachIndexed { indice, insight ->
                if (indice > 0) Spacer(modifier = Modifier.height(12.dp))
                val cor = when (insight.tipo) {
                    TipoInsight.ALTA -> RedExpense
                    TipoInsight.BAIXA -> com.finapp.ui.theme.GreenPrimary
                    TipoInsight.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val icone = when (insight.tipo) {
                    TipoInsight.ALTA -> Icons.Filled.TrendingUp
                    TipoInsight.BAIXA -> Icons.Filled.TrendingDown
                    TipoInsight.INFO -> Icons.Filled.Info
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(
                        imageVector = icone,
                        contentDescription = null,
                        tint = cor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = insight.texto,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/** Faturas em aberto: um card por fatura (cartão + vencimento + total). */
@Composable
private fun SecaoFaturas(faturas: List<Fatura>, onVerFatura: (Fatura) -> Unit) {
    Text(
        text = "FATURAS DO CARTÃO",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    faturas.forEachIndexed { indice, fatura ->
        if (indice > 0) Spacer(modifier = Modifier.height(8.dp))
        val cor = runCatching { Color(fatura.cartaoCor.toColorInt()) }
            .getOrDefault(MaterialTheme.colorScheme.primary)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onVerFatura(fatura) },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(cor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fatura.cartaoNome,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Vence ${Formatadores.dataCurta(fatura.vencimento)} · " +
                            "${fatura.itens.size} ${if (fatura.itens.size == 1) "compra" else "compras"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = Formatadores.moeda(fatura.total),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** Itens de uma fatura (aberto ao tocar no card do cartão). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FaturaDetalheSheet(fatura: Fatura, onFechar: () -> Unit) {
    val estado = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onFechar, sheetState = estado) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            TituloDetalhe(fatura.cartaoNome)
            LinhaValor(
                nome = "Vence ${Formatadores.dataCurta(fatura.vencimento)}",
                valor = Formatadores.moeda(fatura.total),
                corValor = RedExpense
            )
            Spacer(modifier = Modifier.height(8.dp))
            ListaTransacoes(fatura.itens)
        }
    }
}

/** Lançamentos de uma categoria no período (aberto ao tocar na fatia). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriaDetalheSheet(
    categoria: String,
    transacoes: List<Transacao>,
    tipo: TipoTransacao,
    onFechar: () -> Unit
) {
    val estado = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val doTipo = remember(transacoes, categoria, tipo) {
        transacoes
            .filter { it.categoria == categoria && it.tipo == tipo }
            .sortedByDescending { it.valor }
    }
    val total = remember(doTipo) { doTipo.sumOf { it.valor } }
    ModalBottomSheet(onDismissRequest = onFechar, sheetState = estado) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            TituloDetalhe(categoria)
            LinhaValor(
                nome = "Total no período",
                valor = Formatadores.moeda(total),
                corValor = if (tipo == TipoTransacao.GASTO) RedExpense
                else com.finapp.ui.theme.GreenPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            ListaTransacoes(doTipo)
        }
    }
}

@Composable
private fun EstatisticaCard(
    titulo: String,
    valor: String,
    modifier: Modifier = Modifier,
    detalhe: String? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .height(ALTURA_CARD_ESTATISTICA)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (onClick != null) {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "Ver detalhes",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = valor,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
            if (detalhe != null) {
                Text(
                    text = detalhe,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
