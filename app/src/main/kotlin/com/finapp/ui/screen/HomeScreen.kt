package com.finapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finapp.data.db.entities.ContextoMei
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.Transacao
import com.finapp.ui.component.LucroCard
import com.finapp.ui.component.ResumoCard
import com.finapp.ui.component.SaldoCard
import com.finapp.ui.component.TransacaoItemDismissivel
import com.finapp.ui.component.TransacaoModal
import com.finapp.ui.theme.BluAccent
import com.finapp.ui.theme.GreenPrimary
import com.finapp.utils.Formatadores
import com.finapp.viewmodel.HomeViewModel
import com.finapp.viewmodel.TransacaoViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Dashboard: saldo, resumo do mês e últimas transações. */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    transacaoViewModel: TransacaoViewModel = hiltViewModel()
) {
    val saldo by viewModel.saldoTotal.collectAsStateWithLifecycle()
    val ganhos by viewModel.ganhosMes.collectAsStateWithLifecycle()
    val gastos by viewModel.gastosMes.collectAsStateWithLifecycle()
    val ultimas by viewModel.ultimasTransacoes.collectAsStateWithLifecycle()
    val perfil by viewModel.perfil.collectAsStateWithLifecycle()
    val contextoMei by viewModel.contextoMei.collectAsStateWithLifecycle()
    val casaConectada by viewModel.casaConectada.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val escopo = rememberCoroutineScope()

    var modalAberto by remember { mutableStateOf(false) }
    var transacaoEmEdicao by remember { mutableStateOf<Transacao?>(null) }
    // Toque no saldo alterna: total <-> mês
    var mostrandoSaldoMes by remember { mutableStateOf(false) }

    // Mensagens dos ViewModels (sucesso do modal, erros etc.) viram snackbar
    LaunchedEffect(Unit) {
        viewModel.mensagens.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        transacaoViewModel.mensagens.collect { snackbarHostState.showSnackbar(it) }
    }

    val hoje = remember {
        LocalDate.now()
            .format(DateTimeFormatter.ofPattern("EEE, d 'DE' MMMM", Formatadores.LOCALE_BR))
            .uppercase(Formatadores.LOCALE_BR)
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    transacaoEmEdicao = null
                    modalAberto = true
                },
                containerColor = when (perfil) {
                    Perfil.CNPJ -> BluAccent
                    else -> GreenPrimary
                },
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Adicionar transação")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Header minimalista: data + perfil ativo
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = hoje,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                // No perfil Casa, nuvem indica que o sync está ativo
                if (perfil == Perfil.CASA && casaConectada) {
                    Icon(
                        imageVector = Icons.Filled.CloudDone,
                        contentDescription = "Sincronizando com a Casa",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.CenterVertically)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = perfil.rotulo.uppercase(Formatadores.LOCALE_BR),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // MEI: abas internas Pessoal | Negócio (dados separados)
            if (perfil == Perfil.MEI) {
                Spacer(modifier = Modifier.height(12.dp))
                TabRow(
                    selectedTabIndex = contextoMei.ordinal,
                    containerColor = Color.Transparent
                ) {
                    ContextoMei.entries.forEach { contexto ->
                        Tab(
                            selected = contextoMei == contexto,
                            onClick = { viewModel.mudarContextoMei(contexto) },
                            text = { Text(contexto.rotulo) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SaldoCard(
                saldo = if (mostrandoSaldoMes) ganhos - gastos else saldo,
                rotulo = if (mostrandoSaldoMes) "SALDO DO MÊS · TOQUE P/ TOTAL"
                else "SALDO TOTAL · TOQUE P/ MÊS",
                onClick = { mostrandoSaldoMes = !mostrandoSaldoMes }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // CNPJ usa vocabulário de empresa: Receita / Despesa + Lucro
            val cnpj = perfil == Perfil.CNPJ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ResumoCard(
                    tipo = TipoTransacao.GANHO,
                    valor = ganhos,
                    modifier = Modifier.weight(1f),
                    rotuloCustom = if (cnpj) "Receita" else null
                )
                ResumoCard(
                    tipo = TipoTransacao.GASTO,
                    valor = gastos,
                    modifier = Modifier.weight(1f),
                    rotuloCustom = if (cnpj) "Despesa" else null
                )
            }

            if (cnpj) {
                Spacer(modifier = Modifier.height(12.dp))
                LucroCard(lucro = ganhos - gastos)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "ÚLTIMAS TRANSAÇÕES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (ultimas.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Nenhuma transação ainda",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Toque em + para adicionar a primeira",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(ultimas, key = { it.id }) { transacao ->
                        TransacaoItemDismissivel(
                            transacao = transacao,
                            onDeletar = { deletada ->
                                viewModel.deletarTransacao(deletada)
                                escopo.launch {
                                    val resultado = snackbarHostState.showSnackbar(
                                        message = "Transação deletada",
                                        actionLabel = "Desfazer",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (resultado == SnackbarResult.ActionPerformed) {
                                        viewModel.restaurarTransacao(deletada)
                                    }
                                }
                            },
                            onClick = {
                                transacaoEmEdicao = transacao
                                modalAberto = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (modalAberto) {
        TransacaoModal(
            onFechar = { modalAberto = false },
            transacaoParaEditar = transacaoEmEdicao,
            onDeletar = { deletada ->
                transacaoViewModel.deletarTransacao(deletada)
                escopo.launch {
                    val resultado = snackbarHostState.showSnackbar(
                        message = "Transação deletada",
                        actionLabel = "Desfazer",
                        duration = SnackbarDuration.Short
                    )
                    if (resultado == SnackbarResult.ActionPerformed) {
                        transacaoViewModel.desfazerDelete()
                    }
                }
            },
            viewModel = transacaoViewModel
        )
    }
}
