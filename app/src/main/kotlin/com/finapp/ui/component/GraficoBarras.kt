package com.finapp.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finapp.ui.theme.GreenPrimary
import com.finapp.ui.theme.RedExpense
import com.finapp.utils.Formatadores
import com.finapp.viewmodel.ValorMensal
import java.time.format.DateTimeFormatter

/**
 * Gráfico de barras: comparativo mensal de ganhos e gastos lado a lado,
 * com o valor compacto no topo de cada barra.
 */
@Composable
fun GraficoBarras(
    series: List<ValorMensal>,
    modifier: Modifier = Modifier
) {
    if (series.isEmpty()) return

    val formatoMes = remember { DateTimeFormatter.ofPattern("MMM", Formatadores.LOCALE_BR) }
    val maiorValor = remember(series) {
        maxOf(series.maxOf { it.ganhos }, series.maxOf { it.gastos }, 1L).toDouble()
    }
    val medidorTexto = rememberTextMeasurer()
    val estiloRotulo = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    // Resumo textual do gráfico para leitores de tela (TalkBack)
    val resumoAcessivel = remember(series) {
        series.joinToString(prefix = "Gráfico de barras. ", separator = "; ") { mes ->
            "${mes.mes.format(formatoMes).uppercase(Formatadores.LOCALE_BR)}: " +
                "ganhos ${Formatadores.moeda(mes.ganhos)}, " +
                "gastos ${Formatadores.moeda(mes.gastos)}"
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(top = 16.dp)
                .semantics { contentDescription = resumoAcessivel }
        ) {
            val larguraGrupo = size.width / series.size
            val larguraBarra = larguraGrupo * 0.28f
            val alturaUtil = size.height

            series.forEachIndexed { indice, mes ->
                val centroGrupo = larguraGrupo * indice + larguraGrupo / 2f

                fun barra(valor: Long, cor: androidx.compose.ui.graphics.Color, esquerda: Boolean) {
                    if (valor <= 0L) return
                    val alturaBarra = (valor.toDouble() / maiorValor * alturaUtil * 0.85f).toFloat()
                    val x = if (esquerda) centroGrupo - larguraBarra - 2f else centroGrupo + 2f
                    val topo = alturaUtil - alturaBarra

                    drawRect(
                        color = cor,
                        topLeft = Offset(x, topo),
                        size = Size(larguraBarra, alturaBarra)
                    )
                    // Valor compacto no topo da barra
                    val texto = medidorTexto.measure(
                        AnnotatedString(Formatadores.moedaCompacta(valor)),
                        style = estiloRotulo
                    )
                    drawText(
                        textLayoutResult = texto,
                        topLeft = Offset(
                            x + larguraBarra / 2f - texto.size.width / 2f,
                            (topo - texto.size.height - 2f).coerceAtLeast(0f)
                        )
                    )
                }

                barra(mes.ganhos, GreenPrimary, esquerda = true)
                barra(mes.gastos, RedExpense, esquerda = false)
            }
        }

        // Rótulos dos meses
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

        Spacer(modifier = Modifier.height(8.dp))

        // Legenda
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            LegendaItem(cor = GreenPrimary, rotulo = "Ganhos")
            Spacer(modifier = Modifier.width(16.dp))
            LegendaItem(cor = RedExpense, rotulo = "Gastos")
        }
    }
}

@Composable
private fun LegendaItem(cor: androidx.compose.ui.graphics.Color, rotulo: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(cor)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = rotulo,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
