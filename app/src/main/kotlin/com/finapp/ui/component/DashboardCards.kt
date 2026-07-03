package com.finapp.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
 * Card principal do Dashboard com o saldo em destaque (centavos).
 * O toque alterna entre saldo total e saldo do mês (controlado pelo chamador).
 */
@Composable
fun SaldoCard(
    saldo: Long,
    modifier: Modifier = Modifier,
    rotulo: String = "SALDO TOTAL",
    onClick: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = rotulo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = Formatadores.moeda(saldo),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
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
