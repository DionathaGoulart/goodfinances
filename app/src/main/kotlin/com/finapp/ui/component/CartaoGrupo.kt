package com.finapp.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.finapp.data.db.entities.Cartao
import com.finapp.data.db.entities.Transacao
import com.finapp.ui.theme.GreenPrimary
import com.finapp.utils.Formatadores

/** Gastos de um cartão agrupados numa lista (nome, cor, total e itens). */
data class GrupoCartao(
    val cartaoUuid: String,
    val nome: String,
    val cor: String,
    val total: Long,
    val transacoes: List<Transacao>
) {
    /** Quanto da fatura ainda está pendente (0 = fatura paga). */
    val pendente: Long get() = transacoes.filter { !it.pago }.sumOf { it.valor }
}

/**
 * Separa uma lista de transações em (grupos por cartão, avulsas).
 * Compras no crédito viram um [GrupoCartao] por cartão; débito/dinheiro
 * seguem soltas. Cartão apagado aparece como "Cartão".
 */
fun agruparPorCartao(
    transacoes: List<Transacao>,
    cartoes: List<Cartao>
): Pair<List<GrupoCartao>, List<Transacao>> {
    val (noCartao, avulsas) = transacoes.partition { it.cartaoUuid.isNotBlank() }
    val grupos = noCartao
        .groupBy { it.cartaoUuid }
        .map { (uuid, itens) ->
            val cartao = cartoes.firstOrNull { it.uuid == uuid }
            GrupoCartao(
                cartaoUuid = uuid,
                nome = cartao?.nome ?: "Cartão",
                cor = cartao?.cor ?: "#8B5CF6",
                total = itens.sumOf { it.valor },
                transacoes = itens
            )
        }
        .sortedBy { it.nome }
    return grupos to avulsas
}

/**
 * Cartão de crédito como um card único: cabeçalho (cor + nome, nº de compras e
 * total) e, quando [expandido], as próprias compras DENTRO da mesma moldura —
 * separadas por uma divisória e recuadas, deixando claro que são daquele cartão.
 * Toque no cabeçalho alterna [expandido]; os itens vêm do slot [itens] (o
 * chamador renderiza cada [TransacaoLinha] com o fundo do card).
 * Com fatura pendente e [onPagarFatura] definido, mostra o botão que confirma
 * o pagamento de todas as compras do grupo (aí sim descontam do saldo).
 */
@Composable
fun CartaoGrupoCard(
    grupo: GrupoCartao,
    expandido: Boolean,
    onAlternar: () -> Unit,
    modifier: Modifier = Modifier,
    onPagarFatura: (() -> Unit)? = null,
    itens: @Composable ColumnScope.() -> Unit = {}
) {
    val cor = runCatching { Color(grupo.cor.toColorInt()) }
        .getOrDefault(MaterialTheme.colorScheme.primary)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onAlternar)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(cor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CreditCard,
                    contentDescription = null,
                    tint = cor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = grupo.nome,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = "${grupo.transacoes.size} " +
                        (if (grupo.transacoes.size == 1) "compra" else "compras") +
                        " no crédito",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "- ${Formatadores.moeda(grupo.total)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (expandido) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expandido) "Recolher" else "Expandir",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Fatura em aberto não desconta do saldo — o botão confirma o pagamento
        if (grupo.pendente > 0L && onPagarFatura != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 60.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Em aberto: ${Formatadores.moeda(grupo.pendente)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onPagarFatura) {
                    Text("Pagar fatura")
                }
            }
        } else if (grupo.transacoes.isNotEmpty() && grupo.pendente == 0L) {
            Text(
                text = "Fatura paga",
                style = MaterialTheme.typography.bodyMedium,
                color = GreenPrimary,
                modifier = Modifier.padding(start = 60.dp, end = 12.dp, bottom = 12.dp)
            )
        }
        // As compras do cartão, dentro da mesma moldura: divisória + recuo
        if (expandido) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                content = itens
            )
        }
    }
}
