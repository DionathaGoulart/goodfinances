package com.finapp.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.ui.theme.GreenPrimary
import com.finapp.ui.theme.RedExpense
import com.finapp.utils.Formatadores

/**
 * Card principal do Dashboard: saldo total em destaque e, abaixo de uma
 * divisória, uma faixa fina com o resumo do mês (entrou/saiu) e as pendências
 * ("a pagar" → quanto sobra depois de pagar tudo).
 * [mostrarResumoMes] liga a linha "Este mês +ganhos −gastos" (contextos
 * pessoais); no contexto de empresa, os cards Receita/Despesa já cobrem isso.
 * [rotuloMes] identifica o mês do resumo ("Este mês" ou "Em Julho" ao
 * navegar no histórico).
 */
@Composable
fun SaldoCard(
    saldoTotal: Long,
    ganhosMes: Long,
    gastosMes: Long,
    modifier: Modifier = Modifier,
    rotulo: String = "SALDO TOTAL",
    aPagarMes: Long = 0L,
    saldoAposPagar: Long = 0L,
    mostrarResumoMes: Boolean = true,
    rotuloMes: String = "Este mês"
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 20.dp)
        ) {
            Text(
                text = rotulo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = Formatadores.moeda(saldoTotal),
                style = MaterialTheme.typography.displayMedium,
                color = if (saldoTotal >= 0L) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    RedExpense
                }
            )

            // Faixa de resumo do mês, só quando há algo a mostrar
            if (mostrarResumoMes || aPagarMes > 0L) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (mostrarResumoMes) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = rotuloMes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    ValorAssinado(
                        icone = Icons.Filled.ArrowUpward,
                        valor = ganhosMes,
                        cor = GreenPrimary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    ValorAssinado(
                        icone = Icons.Filled.ArrowDownward,
                        valor = gastosMes,
                        cor = RedExpense
                    )
                }
            }

            if (aPagarMes > 0L) {
                if (mostrarResumoMes) Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "A pagar ${Formatadores.moeda(aPagarMes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "sobra ${Formatadores.moeda(saldoAposPagar)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (saldoAposPagar >= 0L) GreenPrimary else RedExpense
                    )
                }
            }
        }
    }
}

/** Ícone + valor colorido (usado na faixa de resumo do mês do SaldoCard). */
@Composable
private fun ValorAssinado(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    valor: Long,
    cor: androidx.compose.ui.graphics.Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icone,
            contentDescription = null,
            tint = cor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = Formatadores.moeda(valor),
            style = MaterialTheme.typography.bodyLarge,
            color = cor
        )
    }
}

/** Card compacto de Ganhos ou Gastos do mês, em centavos (ficam lado a lado). */
@Composable
fun ResumoCard(
    tipo: TipoTransacao,
    valor: Long,
    modifier: Modifier = Modifier,
    rotuloCustom: String? = null
) {
    val (rotuloPadrao, icone, cor) = when (tipo) {
        TipoTransacao.GANHO -> Triple("Ganhos", Icons.Filled.ArrowUpward, GreenPrimary)
        TipoTransacao.GASTO -> Triple("Gastos", Icons.Filled.ArrowDownward, RedExpense)
    }
    val rotulo = rotuloCustom ?: rotuloPadrao

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icone,
                    contentDescription = rotulo,
                    tint = cor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = rotulo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = Formatadores.moeda(valor),
                style = MaterialTheme.typography.titleMedium,
                color = cor
            )
        }
    }
}

/** Card de Lucro do mês em centavos (Receita - Despesa) — exclusivo do perfil CNPJ. */
@Composable
fun LucroCard(
    lucro: Long,
    modifier: Modifier = Modifier
) {
    val cor = if (lucro >= 0L) GreenPrimary else RedExpense
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lucro do mês",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = Formatadores.moeda(lucro),
                style = MaterialTheme.typography.titleMedium,
                color = cor
            )
        }
    }
}
