package com.finapp.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.Transacao
import com.finapp.ui.theme.GreenPrimary
import com.finapp.ui.theme.RedExpense
import com.finapp.utils.Formatadores

/**
 * Linha de transação: ícone, descrição, categoria, valor e data.
 * [corCategoria] pinta o ícone com a cor da categoria (reconhecimento rápido
 * do tipo de gasto); sem ela, cai no verde/vermelho de ganho/gasto.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransacaoItem(
    transacao: Transacao,
    modifier: Modifier = Modifier,
    mostrarData: Boolean = true,
    corFundo: Color? = null,
    corCategoria: Color? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null
) {
    val cor = when (transacao.tipo) {
        TipoTransacao.GANHO -> GreenPrimary
        TipoTransacao.GASTO -> RedExpense
    }
    // O valor continua verde/vermelho (semântico); o ícone leva a categoria
    val corIcone = corCategoria ?: cor
    val icone = when (transacao.tipo) {
        TipoTransacao.GANHO -> Icons.Filled.ArrowUpward
        TipoTransacao.GASTO -> Icons.Filled.ArrowDownward
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(corFundo ?: MaterialTheme.colorScheme.background)
            .let {
                when {
                    onClick != null || onLongClick != null -> it.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onClickLabel = onClickLabel,
                        onLongClick = onLongClick,
                        onLongClickLabel = onLongClickLabel
                    )
                    else -> it
                }
            }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(corIcone.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icone,
                // O sinal +/− junto ao valor já comunica o tipo no nó mesclado
                contentDescription = null,
                tint = corIcone,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transacao.descricao.ifBlank { transacao.categoria },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // No perfil Casa mostra quem lançou (primeiro nome)
            val autor = transacao.criadoPor.substringBefore(' ')
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (autor.isBlank()) transacao.categoria
                    else "${transacao.categoria} · por $autor",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (transacao.notaFiscal.isNotBlank()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = "Nota fiscal anexada",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
                // Escondido da visão Membros da casa
                if (transacao.oculto) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.VisibilityOff,
                        contentDescription = "Escondido da visão Membros",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
                // Pendente: tem data para pagar, ainda não desconta do saldo
                if (!transacao.pago) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = "Aguardando pagamento",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                // Compra no crédito: mostra o dia em que a compra foi feita
                if (transacao.cartaoUuid.isNotBlank()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.CreditCard,
                        contentDescription = "Compra no crédito",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    transacao.dataCompra?.let { compra ->
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = Formatadores.dataCurta(compra),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            val sinal = if (transacao.tipo == TipoTransacao.GASTO) "-" else "+"
            Text(
                text = "$sinal ${Formatadores.moeda(transacao.valor)}",
                style = MaterialTheme.typography.titleMedium,
                // Pendente fica esmaecido: ainda não saiu do saldo
                // (alpha alto o bastante para manter contraste legível;
                // o ícone de relógio já sinaliza a pendência)
                color = if (transacao.pago) cor else cor.copy(alpha = 0.85f)
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
 * Linha de transação com menu de contexto ao segurar o dedo: Editar,
 * Marcar como pago/pendente, Excluir e — quando [podeEsconder] —
 * Esconder/Reexibir da visão Membros.
 * [podeEditar] false (lançamento de outro membro da casa) desabilita
 * editar/excluir e o toque chama [onBloqueado].
 */
@Composable
fun TransacaoLinha(
    transacao: Transacao,
    podeEditar: Boolean,
    podeEsconder: Boolean,
    onEditar: (Transacao) -> Unit,
    onExcluir: (Transacao) -> Unit,
    onAlternarOculto: (Transacao) -> Unit,
    modifier: Modifier = Modifier,
    mostrarData: Boolean = true,
    corFundo: Color? = null,
    corCategoria: Color? = null,
    onAlternarPago: ((Transacao) -> Unit)? = null,
    onBloqueado: () -> Unit = {}
) {
    var menuAberto by remember { mutableStateOf(false) }
    val temMenu = podeEditar || podeEsconder

    Box(modifier = modifier) {
        TransacaoItem(
            transacao = transacao,
            mostrarData = mostrarData,
            corFundo = corFundo,
            corCategoria = corCategoria,
            onClick = { if (podeEditar) onEditar(transacao) else onBloqueado() },
            onClickLabel = if (podeEditar) "Editar" else "Ver opções",
            onLongClick = if (temMenu) {
                { menuAberto = true }
            } else {
                null
            },
            onLongClickLabel = if (temMenu) "Abrir menu de ações" else null
        )

        DropdownMenu(
            expanded = menuAberto,
            onDismissRequest = { menuAberto = false }
        ) {
            if (podeEditar) {
                DropdownMenuItem(
                    text = { Text("Editar") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
                    },
                    onClick = {
                        menuAberto = false
                        onEditar(transacao)
                    }
                )
            }
            if (podeEditar && onAlternarPago != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (transacao.pago) "Marcar como pendente"
                            else "Marcar como pago"
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (transacao.pago) {
                                Icons.Filled.Schedule
                            } else {
                                Icons.Filled.CheckCircle
                            },
                            contentDescription = null
                        )
                    },
                    onClick = {
                        menuAberto = false
                        onAlternarPago(transacao)
                    }
                )
            }
            if (podeEsconder) {
                DropdownMenuItem(
                    text = { Text(if (transacao.oculto) "Reexibir" else "Esconder") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (transacao.oculto) {
                                Icons.Filled.Visibility
                            } else {
                                Icons.Filled.VisibilityOff
                            },
                            contentDescription = null
                        )
                    },
                    onClick = {
                        menuAberto = false
                        onAlternarOculto(transacao)
                    }
                )
            }
            if (podeEditar) {
                DropdownMenuItem(
                    text = { Text("Excluir") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            tint = RedExpense
                        )
                    },
                    onClick = {
                        menuAberto = false
                        onExcluir(transacao)
                    }
                )
            }
        }
    }
}
