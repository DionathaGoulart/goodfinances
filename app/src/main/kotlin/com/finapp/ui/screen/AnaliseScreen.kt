package com.finapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finapp.data.db.entities.TipoEmpresa
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.ui.component.GraficoBarras
import com.finapp.ui.component.GraficoLinha
import com.finapp.ui.component.GraficoPizza
import com.finapp.ui.theme.BluAccent
import com.finapp.ui.theme.OrangeAlert
import com.finapp.ui.theme.RedExpense
import com.finapp.utils.Formatadores
import com.finapp.utils.PeriodoFiltro
import com.finapp.viewmodel.AnaliseViewModel
import com.finapp.viewmodel.OrcamentoCategoria
import com.finapp.viewmodel.PainelFiscal
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.TextStyle
import kotlin.math.roundToInt

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

    var rangePickerAberto by remember { mutableStateOf(false) }

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
            GraficoPizza(fatias = fatias)
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
                modifier = Modifier.weight(1f)
            )
            EstatisticaCard(
                titulo = "Maior Gasto",
                valor = estatisticas.maiorGasto?.let { Formatadores.moeda(it.valor) } ?: "—",
                detalhe = estatisticas.maiorGasto?.categoria,
                modifier = Modifier.weight(1f)
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
                modifier = Modifier.weight(1f)
            )
            EstatisticaCard(
                titulo = "Categoria Top Gasto",
                valor = estatisticas.categoriaMaiorGasto ?: "—",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
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

@Composable
private fun EstatisticaCard(
    titulo: String,
    valor: String,
    modifier: Modifier = Modifier,
    detalhe: String? = null
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = valor,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
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
