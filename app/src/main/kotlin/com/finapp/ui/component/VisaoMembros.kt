package com.finapp.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finapp.ui.theme.GreenPrimary
import com.finapp.ui.theme.RedExpense
import com.finapp.utils.Formatadores
import com.finapp.viewmodel.MembrosViewModel
import com.finapp.viewmodel.ResumoMembro

/**
 * Visão "Membros" da Casa: resumo do mês por membro + lançamentos
 * pessoais de quem compartilha. Somente leitura — cada um edita os
 * seus no próprio aparelho.
 */
@Composable
fun VisaoMembros(viewModel: MembrosViewModel = hiltViewModel()) {
    val resumos by viewModel.resumos.collectAsStateWithLifecycle()
    val transacoes by viewModel.transacoesMes.collectAsStateWithLifecycle()
    val membros by viewModel.membros.collectAsStateWithLifecycle()
    val filtro by viewModel.filtroMembro.collectAsStateWithLifecycle()
    val compartilhando by viewModel.compartilhando.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        if (!compartilhando) {
            Text(
                text = "Você não está compartilhando seus lançamentos — ative em " +
                    "Configurações › Casa Compartilhada para aparecer aqui também.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (resumos.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Nenhum lançamento de membro este mês",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Cada membro escolhe compartilhar os próprios " +
                        "lançamentos nas Configurações",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        // ---------- Resumo do mês por membro ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            resumos.forEach { resumo -> CardMembro(resumo) }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---------- Filtro por membro ----------
        if (membros.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filtro == null,
                    onClick = { viewModel.filtrarMembro(null) },
                    label = { Text("Todos") }
                )
                membros.forEach { nome ->
                    FilterChip(
                        selected = filtro == nome,
                        onClick = {
                            viewModel.filtrarMembro(if (filtro == nome) null else nome)
                        },
                        label = { Text(nome) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(
            text = "LANÇAMENTOS DO MÊS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(transacoes, key = { it.uuid }) { transacao ->
                // Somente leitura: sem swipe nem clique de edição
                TransacaoItem(transacao = transacao)
            }
        }
    }
}

@Composable
private fun CardMembro(resumo: ResumoMembro) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = resumo.nome,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row {
                Column {
                    Text(
                        text = "Ganhos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = Formatadores.moeda(resumo.ganhos),
                        style = MaterialTheme.typography.bodyLarge,
                        color = GreenPrimary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Gastos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = Formatadores.moeda(resumo.gastos),
                        style = MaterialTheme.typography.bodyLarge,
                        color = RedExpense
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Saldo: ${Formatadores.moeda(resumo.saldo)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
