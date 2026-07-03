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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finapp.ui.component.GraficoBarras
import com.finapp.ui.component.GraficoLinha
import com.finapp.ui.component.GraficoPizza
import com.finapp.ui.theme.BluAccent
import com.finapp.utils.Formatadores
import com.finapp.utils.PeriodoFiltro
import com.finapp.viewmodel.AnaliseViewModel
import java.time.Instant
import java.time.ZoneOffset

/** Tela de Análise: gráficos e estatísticas do período. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnaliseScreen(viewModel: AnaliseViewModel = hiltViewModel()) {
    val filtro by viewModel.filtro.collectAsStateWithLifecycle()
    val fatias by viewModel.gastosPorCategoria.collectAsStateWithLifecycle()
    val series by viewModel.seriesMensais.collectAsStateWithLifecycle()
    val estatisticas by viewModel.estatisticas.collectAsStateWithLifecycle()

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

        // ---------- Pizza: gastos por categoria ----------
        SecaoGrafico(titulo = "GASTOS POR CATEGORIA") {
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
