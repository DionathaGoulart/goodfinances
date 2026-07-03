package com.finapp.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.Transacao
import com.finapp.ui.theme.GreenPrimary
import com.finapp.ui.theme.RedExpense
import com.finapp.utils.Formatadores

/** Linha de transação: ícone, descrição, categoria, valor e data. */
@Composable
fun TransacaoItem(
    transacao: Transacao,
    modifier: Modifier = Modifier,
    mostrarData: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val cor = when (transacao.tipo) {
        TipoTransacao.GANHO -> GreenPrimary
        TipoTransacao.GASTO -> RedExpense
    }
    val icone = when (transacao.tipo) {
        TipoTransacao.GANHO -> Icons.Filled.ArrowUpward
        TipoTransacao.GASTO -> Icons.Filled.ArrowDownward
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 12.dp, horizontal = 4.dp),
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
                imageVector = icone,
                contentDescription = transacao.tipo.name,
                tint = cor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transacao.descricao.ifBlank { transacao.categoria },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            // No perfil Casa mostra quem lançou (primeiro nome)
            val autor = transacao.criadoPor.substringBefore(' ')
            Text(
                text = if (autor.isBlank()) transacao.categoria
                else "${transacao.categoria} · por $autor",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            val sinal = if (transacao.tipo == TipoTransacao.GASTO) "-" else "+"
            Text(
                text = "$sinal ${Formatadores.moeda(transacao.valor)}",
                style = MaterialTheme.typography.titleMedium,
                color = cor
            )
            if (mostrarData) {
                Text(
                    text = Formatadores.dataCurta(transacao.data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * [TransacaoItem] com swipe para a esquerda para deletar.
 * O chamador mostra o snackbar de desfazer.
 */
@Composable
fun TransacaoItemDismissivel(
    transacao: Transacao,
    onDeletar: (Transacao) -> Unit,
    modifier: Modifier = Modifier,
    mostrarData: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { valor ->
            if (valor == SwipeToDismissBoxValue.EndToStart) {
                onDeletar(transacao)
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RedExpense.copy(alpha = 0.8f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Deletar",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    ) {
        TransacaoItem(
            transacao = transacao,
            mostrarData = mostrarData,
            onClick = onClick
        )
    }
}
