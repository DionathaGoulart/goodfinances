package com.finapp.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.Transacao
import com.finapp.data.db.entities.ehEmpresa
import com.finapp.ui.component.LucroCard
import com.finapp.ui.component.ResumoCard
import com.finapp.ui.component.SaldoCard
import com.finapp.ui.component.TransacaoItemDismissivel
import com.finapp.ui.component.TransacaoModal
import com.finapp.ui.component.TransferenciaDialog
import com.finapp.ui.component.VisaoMembros
import com.finapp.ui.theme.GreenPrimary
import com.finapp.ui.theme.RedExpense
import com.finapp.utils.Formatadores
import com.finapp.viewmodel.HomeViewModel
import com.finapp.viewmodel.ResumoMesAnterior
import com.finapp.viewmodel.TransacaoViewModel
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/**
 * Dashboard: saldo, resumo do mês e últimas transações.
 * [abrirModalInicial] = chegada pelo widget de lançamento rápido;
 * [onLancamentoConsumido] avisa o chamador que o modal já abriu (one-shot).
 */
@Composable
fun HomeScreen(
    abrirModalInicial: Boolean = false,
    onLancamentoConsumido: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    transacaoViewModel: TransacaoViewModel = hiltViewModel()
) {
    val saldo by viewModel.saldoTotal.collectAsStateWithLifecycle()
    val ganhos by viewModel.ganhosMes.collectAsStateWithLifecycle()
    val gastos by viewModel.gastosMes.collectAsStateWithLifecycle()
    val ultimas by viewModel.ultimasTransacoes.collectAsStateWithLifecycle()
    val perfilDados by viewModel.perfilDados.collectAsStateWithLifecycle()
    val contextos by viewModel.contextos.collectAsStateWithLifecycle()
    val casaConectada by viewModel.casaConectada.collectAsStateWithLifecycle()
    val syncPessoalAtivo by viewModel.syncPessoalAtivo.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val escopo = rememberCoroutineScope()

    var modalAberto by remember { mutableStateOf(abrirModalInicial) }
    var transacaoEmEdicao by remember { mutableStateOf<Transacao?>(null) }
    // Consome o pedido do widget para não reabrir ao voltar pra aba
    LaunchedEffect(Unit) {
        if (abrirModalInicial) onLancamentoConsumido()
    }
    // Toque no saldo alterna: total <-> mês
    var mostrandoSaldoMes by remember { mutableStateOf(false) }
    // Sub-visão da Casa: carteira conjunta ("Da casa") ou finanças dos membros
    var visaoMembros by rememberSaveable { mutableStateOf(false) }
    val mostrandoMembros = perfilDados == Perfil.CASA && visaoMembros
    // Transferência entre contextos (Pessoal / Empresa / Casa)
    var transferenciaAberta by remember { mutableStateOf(false) }

    // Mensagens dos ViewModels (sucesso do modal, erros etc.) viram snackbar
    LaunchedEffect(Unit) {
        viewModel.mensagens.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        transacaoViewModel.mensagens.collect { snackbarHostState.showSnackbar(it) }
    }

    // Reativa: vira sozinha à meia-noite (fluxoDataAtual)
    val dataAtual by viewModel.dataAtual.collectAsStateWithLifecycle()
    val hoje = remember(dataAtual) {
        dataAtual
            .format(DateTimeFormatter.ofPattern("EEE, d 'DE' MMMM", Formatadores.LOCALE_BR))
            .uppercase(Formatadores.LOCALE_BR)
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // A visão Membros é somente leitura — sem botões de ação
            if (!mostrandoMembros) {
                Column(horizontalAlignment = Alignment.End) {
                    // Transferir entre contextos (só quando há mais de um)
                    if (contextos.size > 1) {
                        SmallFloatingActionButton(
                            onClick = { transferenciaAberta = true },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SwapHoriz,
                                contentDescription = "Transferir entre contextos"
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    FloatingActionButton(
                        onClick = {
                            transacaoEmEdicao = null
                            modalAberto = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Adicionar transação"
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                // Deslizar para os lados troca o contexto (Pessoal/Empresa/Casa).
                // Filhos com gesto horizontal próprio (swipe-delete, chips com
                // scroll) continuam com prioridade — aqui só chega o que sobra.
                .pointerInput(contextos, perfilDados) {
                    if (contextos.size > 1) {
                        var arrasto = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { arrasto = 0f },
                            onDragEnd = {
                                val limiar = 64.dp.toPx()
                                val indice = contextos.indexOf(perfilDados)
                                val destino = when {
                                    arrasto <= -limiar -> indice + 1
                                    arrasto >= limiar -> indice - 1
                                    else -> -1
                                }
                                if (destino in contextos.indices) {
                                    viewModel.mudarContexto(contextos[destino])
                                }
                            }
                        ) { _, deslocamento -> arrasto += deslocamento }
                    }
                }
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
                // Nuvem indica sync ativo: da Casa ou dos dados pessoais
                val sincronizando = if (perfilDados == Perfil.CASA) {
                    casaConectada
                } else {
                    syncPessoalAtivo
                }
                if (sincronizando) {
                    Icon(
                        imageVector = Icons.Filled.CloudDone,
                        contentDescription = "Sincronizado com a nuvem",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.CenterVertically)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = perfilDados.rotulo.uppercase(Formatadores.LOCALE_BR),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Abas de contexto: Pessoal | Empresa | Casa (conforme o modo)
            if (contextos.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                TabRow(
                    selectedTabIndex = contextos.indexOf(perfilDados).coerceAtLeast(0),
                    containerColor = Color.Transparent
                ) {
                    contextos.forEach { contexto ->
                        Tab(
                            selected = perfilDados == contexto,
                            onClick = { viewModel.mudarContexto(contexto) },
                            text = { Text(contexto.rotulo) }
                        )
                    }
                }
            }

            // Casa tem duas visões: carteira conjunta e finanças dos membros
            if (perfilDados == Perfil.CASA) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !visaoMembros,
                        onClick = { visaoMembros = false },
                        label = { Text("Da casa") }
                    )
                    FilterChip(
                        selected = visaoMembros,
                        onClick = { visaoMembros = true },
                        label = { Text("Membros") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (mostrandoMembros) {
                VisaoMembros()
                return@Column
            }

            // Fechamento do mês anterior (primeiros dias do mês, dispensável)
            val resumoMes by viewModel.resumoMesAnterior.collectAsStateWithLifecycle()
            resumoMes?.let { resumo ->
                ResumoMesCard(resumo = resumo, onDispensar = viewModel::dispensarResumo)
                Spacer(modifier = Modifier.height(12.dp))
            }

            SaldoCard(
                saldo = if (mostrandoSaldoMes) ganhos - gastos else saldo,
                rotulo = if (mostrandoSaldoMes) "SALDO DO MÊS · TOQUE P/ TOTAL"
                else "SALDO TOTAL · TOQUE P/ MÊS",
                onClick = { mostrandoSaldoMes = !mostrandoSaldoMes }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Contexto de empresa usa vocabulário próprio: Receita / Despesa + Lucro
            val cnpj = perfilDados.ehEmpresa
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
                        val podeEditar = viewModel.podeEditar(transacao)
                        TransacaoItemDismissivel(
                            transacao = transacao,
                            permitirSwipe = podeEditar,
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
                                if (podeEditar) {
                                    transacaoEmEdicao = transacao
                                    modalAberto = true
                                } else {
                                    escopo.launch {
                                        snackbarHostState.showSnackbar(
                                            "Só quem lançou pode editar esta transação"
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // ---------- Atualização disponível (GitHub Releases) ----------
    val atualizacao by viewModel.atualizacao.collectAsStateWithLifecycle()
    atualizacao?.let { nova ->
        val contexto = LocalContext.current
        AlertDialog(
            onDismissRequest = viewModel::dispensarAtualizacao,
            title = { Text("Nova versão ${nova.versao} disponível") },
            text = {
                Text(
                    if (nova.notas.isBlank()) {
                        "Uma atualização do FinanApp está pronta para baixar."
                    } else {
                        nova.notas.take(600)
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        contexto.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(nova.urlDownload))
                        )
                        viewModel.dispensarAtualizacao()
                    }
                ) {
                    Text("Baixar")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dispensarAtualizacao) {
                    Text("Agora não")
                }
            }
        )
    }

    if (transferenciaAberta) {
        TransferenciaDialog(
            origem = perfilDados,
            destinos = contextos - perfilDados,
            onTransferir = transacaoViewModel::transferir,
            onFechar = { transferenciaAberta = false }
        )
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

/** Fechamento do mês anterior: ganhos, gastos e saldo, com botão de dispensar. */
@Composable
private fun ResumoMesCard(resumo: ResumoMesAnterior, onDispensar: () -> Unit) {
    val nomeMes = resumo.mes.month
        .getDisplayName(TextStyle.FULL, Formatadores.LOCALE_BR)
        .replaceFirstChar { it.uppercase(Formatadores.LOCALE_BR) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SEU $nomeMes FECHOU",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDispensar) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dispensar resumo"
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
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
                Column(modifier = Modifier.weight(1f)) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Saldo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = Formatadores.moeda(resumo.saldo),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (resumo.saldo >= 0) GreenPrimary else RedExpense
                    )
                }
            }
            if (resumo.saldo > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Você economizou ${Formatadores.moeda(resumo.saldo)} em $nomeMes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
