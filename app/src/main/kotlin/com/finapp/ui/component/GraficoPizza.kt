package com.finapp.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.finapp.utils.Formatadores
import com.finapp.viewmodel.FatiaPizza
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/** Converte "#RRGGBB" em Color, com fallback cinza. */
internal fun corDeHex(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }
        .getOrDefault(Color(0xFF6B7280))

/**
 * Gráfico de pizza dos gastos por categoria.
 * Toque numa fatia mostra o detalhe (nome + valor + percentual).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GraficoPizza(
    fatias: List<FatiaPizza>,
    modifier: Modifier = Modifier,
    onVerCategoria: ((String) -> Unit)? = null
) {
    if (fatias.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Sem gastos no período",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val total = fatias.sumOf { it.valor }.toDouble()
    var selecionada by remember(fatias) { mutableIntStateOf(-1) }
    val corFuro = MaterialTheme.colorScheme.surface

    val progresso = remember { Animatable(0f) }
    LaunchedEffect(fatias) {
        progresso.snapTo(0f)
        progresso.animateTo(1f, tween(durationMillis = 800, easing = FastOutSlowInEasing))
    }

    // Resumo textual do gráfico para leitores de tela (TalkBack)
    val resumoAcessivel = remember(fatias) {
        fatias.joinToString(prefix = "Gráfico de pizza. ", separator = "; ") { fatia ->
            val percentual = fatia.valor / total * 100
            "${fatia.nome}: " +
                "${String.format(Formatadores.LOCALE_BR, "%.0f", percentual)}%, " +
                Formatadores.moeda(fatia.valor)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .semantics { contentDescription = resumoAcessivel }
                .pointerInput(fatias) {
                    detectTapGestures { toque ->
                        val centro = Offset(size.width / 2f, size.height / 2f)
                        val raio = min(size.width, size.height) / 2f
                        val dx = toque.x - centro.x
                        val dy = toque.y - centro.y
                        val distancia = sqrt(dx * dx + dy * dy)
                        // Fora da rosca não seleciona: além da borda externa
                        // ou dentro do furo central (0.55 do raio, igual ao desenho)
                        if (distancia > raio || distancia < raio * 0.55f) {
                            selecionada = -1
                            return@detectTapGestures
                        }
                        // Ângulo do toque relativo ao início do desenho (12h)
                        val angulo = (Math.toDegrees(
                            atan2(dy.toDouble(), dx.toDouble())
                        ) + 90 + 360) % 360

                        var acumulado = 0.0
                        selecionada = fatias.indexOfFirst { fatia ->
                            acumulado += fatia.valor / total * 360
                            angulo < acumulado
                        }
                    }
                }
        ) {
            val diametro = min(size.width, size.height)
            val raio = diametro / 2f
            val topoEsquerda = Offset(
                (size.width - diametro) / 2f,
                (size.height - diametro) / 2f
            )

            var anguloInicial = -90f
            fatias.forEachIndexed { indice, fatia ->
                val varredura = (fatia.valor / total * 360.0).toFloat() * progresso.value
                // Fatia selecionada fica "solta" (raio um pouco maior via stroke)
                drawArc(
                    color = corDeHex(fatia.cor).copy(
                        alpha = if (selecionada == -1 || selecionada == indice) 1f else 0.35f
                    ),
                    startAngle = anguloInicial,
                    sweepAngle = varredura,
                    useCenter = true,
                    topLeft = topoEsquerda,
                    size = Size(diametro, diametro)
                )
                anguloInicial += varredura
            }
            // Furo central (donut) para leitura mais limpa
            drawCircle(
                color = corFuro,
                radius = raio * 0.55f,
                center = Offset(size.width / 2f, size.height / 2f)
            )
        }

        // Detalhe da fatia tocada
        if (selecionada in fatias.indices) {
            val fatia = fatias[selecionada]
            val percentual = fatia.valor / total * 100
            val sufixo = if (onVerCategoria != null) " ›" else ""
            Text(
                text = "${fatia.nome} — ${Formatadores.moeda(fatia.valor)} " +
                    "(${String.format(Formatadores.LOCALE_BR, "%.1f", percentual)}%)$sufixo",
                style = MaterialTheme.typography.titleMedium,
                color = corDeHex(fatia.cor),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
                    .let { base ->
                        if (onVerCategoria != null) {
                            base.clickable { onVerCategoria(fatia.nome) }
                        } else {
                            base
                        }
                    }
            )
            if (onVerCategoria != null) {
                Text(
                    text = "Toque para ver os lançamentos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { onVerCategoria(fatia.nome) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Legenda
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            fatias.forEach { fatia ->
                val percentual = fatia.valor / total * 100
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(corDeHex(fatia.cor))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${fatia.nome} " +
                            "(${String.format(Formatadores.LOCALE_BR, "%.0f", percentual)}%) · " +
                            Formatadores.moeda(fatia.valor),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
