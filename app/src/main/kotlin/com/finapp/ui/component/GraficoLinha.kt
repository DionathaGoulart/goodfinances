package com.finapp.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.finapp.ui.theme.GreenPrimary
import com.finapp.ui.theme.RedExpense
import com.finapp.utils.Formatadores
import com.finapp.viewmodel.ValorMensal
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Gráfico de linha: ganhos (verde) vs gastos (vermelho) nos últimos 6 meses.
 * Animação de entrada (2s ease-out) e toque num mês mostra tooltip com valores.
 */
@Composable
fun GraficoLinha(
    series: List<ValorMensal>,
    modifier: Modifier = Modifier
) {
    if (series.isEmpty()) return

    var mesSelecionado by remember(series) { mutableIntStateOf(-1) }

    val progresso = remember { Animatable(0f) }
    LaunchedEffect(series) {
        progresso.snapTo(0f)
        progresso.animateTo(1f, tween(durationMillis = 2_000, easing = LinearOutSlowInEasing))
    }

    val formatoMes = remember { DateTimeFormatter.ofPattern("MMM", Formatadores.LOCALE_BR) }
    val maiorValor = remember(series) {
        maxOf(series.maxOf { it.ganhos }, series.maxOf { it.gastos }, 1L).toDouble()
    }
    val corGrade = MaterialTheme.colorScheme.outline
    // Resumo textual do gráfico para leitores de tela (TalkBack)
    val resumoAcessivel = remember(series) {
        series.joinToString(prefix = "Gráfico de linha. ", separator = "; ") { mes ->
            "${mes.mes.format(formatoMes).uppercase(Formatadores.LOCALE_BR)}: " +
                "ganhos ${Formatadores.moeda(mes.ganhos)}, " +
                "gastos ${Formatadores.moeda(mes.gastos)}"
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Tooltip do mês tocado
        Box(modifier = Modifier.fillMaxWidth().height(24.dp)) {
            if (mesSelecionado in series.indices) {
                val mes = series[mesSelecionado]
                Text(
                    text = "${mes.mes.format(formatoMes).uppercase(Formatadores.LOCALE_BR)}: " +
                        "↑ ${Formatadores.moeda(mes.ganhos)}   ↓ ${Formatadores.moeda(mes.gastos)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(vertical = 8.dp)
                .pointerInput(series) {
                    detectTapGestures { toque ->
                        val passo = size.width / (series.size - 1).coerceAtLeast(1)
                        mesSelecionado = (toque.x / passo).roundToInt()
                            .coerceIn(series.indices.first, series.indices.last)
                    }
                }
                .semantics { contentDescription = resumoAcessivel }
        ) {
            val largura = size.width
            val altura = size.height
            val passo = largura / (series.size - 1).coerceAtLeast(1)

            // Grid horizontal sutil
            repeat(5) { i ->
                val y = altura * i / 4f
                drawLine(
                    color = corGrade.copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(largura, y),
                    strokeWidth = 1f
                )
            }

            fun pontos(seletor: (ValorMensal) -> Long): List<Offset> =
                series.mapIndexed { i, mes ->
                    Offset(
                        x = i * passo,
                        y = altura - (seletor(mes).toDouble() / maiorValor * altura).toFloat()
                    )
                }

            fun desenharLinha(pontos: List<Offset>, cor: androidx.compose.ui.graphics.Color) {
                val caminho = Path().apply {
                    pontos.forEachIndexed { i, p ->
                        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                }
                // Revela da esquerda para a direita conforme a animação avança
                clipRect(right = largura * progresso.value) {
                    drawPath(
                        path = caminho,
                        color = cor,
                        style = Stroke(width = 5f, cap = StrokeCap.Round)
                    )
                    pontos.forEachIndexed { i, p ->
                        drawCircle(
                            color = cor,
                            radius = if (i == mesSelecionado) 12f else 7f,
                            center = p
                        )
                    }
                }
            }

            desenharLinha(pontos { it.ganhos }, GreenPrimary)
            desenharLinha(pontos { it.gastos }, RedExpense)
        }

        // Rótulos dos meses (eixo X)
        Row(modifier = Modifier.fillMaxWidth()) {
            series.forEach { mes ->
                Text(
                    text = mes.mes.format(formatoMes).uppercase(Formatadores.LOCALE_BR),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
