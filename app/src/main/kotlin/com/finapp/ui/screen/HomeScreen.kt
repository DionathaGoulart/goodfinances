package com.finapp.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finapp.data.EstadoDownload
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.Transacao
import com.finapp.data.db.entities.ehEmpresa
import com.finapp.ui.component.CartaoGrupoCard
import com.finapp.ui.component.LucroCard
import com.finapp.ui.component.ResumoCard
import com.finapp.ui.component.SaldoCard
import com.finapp.ui.component.TransacaoLinha
import com.finapp.ui.component.agruparPorCartao
import com.finapp.ui.component.TransacaoModal
import com.finapp.ui.component.VisaoMembros
import com.finapp.ui.theme.GreenPrimary
import com.finapp.ui.theme.RedExpense
import com.finapp.utils.Formatadores
import com.finapp.viewmodel.HomeViewModel
import com.finapp.viewmodel.ResumoMesAnterior
import com.finapp.viewmodel.TransacaoViewModel
import kotlinx.coroutines.launch
import java.time.Month
import java.time.YearMonth
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
    val pendenteMes by viewModel.pendenteMes.collectAsStateWithLifecycle()
    val ganhos by viewModel.ganhosMes.collectAsStateWithLifecycle()
    val gastos by viewModel.gastosMes.collectAsStateWithLifecycle()
    val transacoesDoMes by viewModel.transacoesDoMes.collectAsStateWithLifecycle()
    val perfilDados by viewModel.perfilDados.collectAsStateWithLifecycle()
    val contextos by viewModel.contextos.collectAsStateWithLifecycle()
    val casaConectada by viewModel.casaConectada.collectAsStateWithLifecycle()
    val syncPessoalAtivo by viewModel.syncPessoalAtivo.collectAsStateWithLifecycle()
    val compartilhando by viewModel.compartilhandoComCasa.collectAsStateWithLifecycle()
    val mesSelecionado by viewModel.mesSelecionado.collectAsStateWithLifecycle()
    val ehMesAtual by viewModel.ehMesAtual.collectAsStateWithLifecycle()
    // "Esconder" só aparece nos baldes pessoais e com compartilhamento ligado
    val podeEsconder = compartilhando && perfilDados in Perfil.BALDES_PESSOAIS

    val snackbarHostState = remember { SnackbarHostState() }
    val escopo = rememberCoroutineScope()

    var modalAberto by remember { mutableStateOf(abrirModalInicial) }
    var transacaoEmEdicao by remember { mutableStateOf<Transacao?>(null) }
    // Consome o pedido do widget para não reabrir ao voltar pra aba
    LaunchedEffect(Unit) {
        if (abrirModalInicial) onLancamentoConsumido()
    }
    // Sub-visão da Casa: carteira conjunta ("Da casa") ou finanças dos membros
    var visaoMembros by rememberSaveable { mutableStateOf(false) }
    val mostrandoMembros = perfilDados == Perfil.CASA && visaoMembros
    // Seletor de mês/ano (navegação do histórico)
    var mesPickerAberto by remember { mutableStateOf(false) }
    // Busca por descrição/categoria (lupa na barra do mês)
    var buscando by rememberSaveable { mutableStateOf(false) }

    // Estado da lista do histórico
    val listState = rememberLazyListState()
    // Trocar de mês recomeça a lista do topo
    LaunchedEffect(mesSelecionado, perfilDados) {
        listState.scrollToItem(0)
    }

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
        snackbarHost = { SnackbarHost(snackbarHostState) }
        // Adicionar/Transferir ficam no botão + central da barra inferior
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                // Deslizar para os lados troca o contexto (Pessoal/Empresa/Casa).
                // Filhos com gesto horizontal próprio (chips com scroll)
                // continuam com prioridade — aqui só chega o que sobra.
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

            // Abas de contexto: Pessoal | Empresa | Casa (conforme o modo).
            // Chips leves em vez do TabRow do Material (menos peso visual).
            if (contextos.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    contextos.forEach { contexto ->
                        FilterChip(
                            selected = perfilDados == contexto,
                            onClick = { viewModel.mudarContexto(contexto) },
                            label = { Text(contexto.rotulo) }
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

            // Navegação de mês (ou campo de busca, quando a lupa está ativa)
            if (buscando) {
                val termoBusca by viewModel.termoBusca.collectAsStateWithLifecycle()
                BarraBusca(
                    termo = termoBusca,
                    onTermo = viewModel::buscar,
                    onFechar = {
                        buscando = false
                        viewModel.buscar("")
                    }
                )
            } else {
                BarraMes(
                    mes = mesSelecionado,
                    ehMesAtual = ehMesAtual,
                    onAnterior = viewModel::mesAnterior,
                    onProximo = viewModel::mesProximo,
                    onAbrirPicker = { mesPickerAberto = true },
                    onHoje = viewModel::irParaMesAtual,
                    onBuscar = { buscando = true }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Fechamento do mês anterior (primeiros dias do mês, dispensável)
            val resumoMes by viewModel.resumoMesAnterior.collectAsStateWithLifecycle()
            // Contexto de empresa usa vocabulário próprio: Receita / Despesa + Lucro
            val cnpj = perfilDados.ehEmpresa

            // Débito/dinheiro ficam soltos; compras no crédito viram um
            // grupo expansível por cartão (toque no cabeçalho abre/fecha)
            val cartoes by viewModel.cartoes.collectAsStateWithLifecycle()
            val (gruposCartao, avulsas) = remember(transacoesDoMes, cartoes) {
                agruparPorCartao(transacoesDoMes, cartoes)
            }
            var cartoesExpandidos by rememberSaveable {
                mutableStateOf(listOf<String>())
            }

            // Cor da categoria de cada linha (reconhecimento visual rápido)
            val coresCategorias by viewModel.coresCategorias.collectAsStateWithLifecycle()
            // Orçamento do mês (só quando alguma categoria tem teto) — coletado
            // aqui no escopo @Composable; o LazyColumn abaixo apenas o consome
            val orcamento by viewModel.orcamentoMes.collectAsStateWithLifecycle()
            val linhaTransacao: @Composable (Transacao, Color?) -> Unit = { transacao, corFundo ->
                val podeEditar = viewModel.podeEditar(transacao)
                val corCategoria = coresCategorias[transacao.categoria]?.let { hex ->
                    runCatching { Color(hex.toColorInt()) }.getOrNull()
                }
                TransacaoLinha(
                    transacao = transacao,
                    podeEditar = podeEditar,
                    podeEsconder = podeEsconder,
                    corFundo = corFundo,
                    corCategoria = corCategoria,
                    // A data vem do cabeçalho do dia (ou do card do cartão)
                    mostrarData = false,
                    onEditar = {
                        transacaoEmEdicao = it
                        modalAberto = true
                    },
                    onExcluir = { deletada ->
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
                    onAlternarOculto = viewModel::alternarOculto,
                    onAlternarPago = viewModel::alternarPago,
                    onBloqueado = {
                        escopo.launch {
                            snackbarHostState.showSnackbar(
                                "Só quem lançou pode editar esta transação"
                            )
                        }
                    }
                )
            }

            // Fundo dos itens dentro do card do cartão (integra à moldura)
            val corItemCartao = MaterialTheme.colorScheme.surface
            // Avulsas agrupadas por dia (a query já vem ordenada por data)
            val avulsasPorDia = remember(avulsas) { avulsas.groupBy { it.data } }

            // Modo busca: resultados de TODOS os meses no lugar do dashboard
            if (buscando) {
                val resultados by viewModel.resultadosBusca.collectAsStateWithLifecycle()
                val termoBusca by viewModel.termoBusca.collectAsStateWithLifecycle()
                ListaBusca(
                    resultados = resultados,
                    termo = termoBusca,
                    hoje = dataAtual,
                    linha = linhaTransacao
                )
                return@Column
            }

            // Cards do dashboard + histórico num único scroll: em paisagem ou
            // com fonte grande a lista não fica espremida sob cards fixos
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                // Folga no fim da lista para as últimas transações rolarem
                // acima da barra inferior (com o botão + central)
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                resumoMes?.let { resumo ->
                    item(key = "resumo-mes-anterior") {
                        ResumoMesCard(
                            resumo = resumo,
                            onDispensar = viewModel::dispensarResumo
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                item(key = "saldo") {
                    SaldoCard(
                        saldoTotal = saldo,
                        ganhosMes = ganhos,
                        gastosMes = gastos,
                        // Pendências do mês (fatura, recorrências): quanto ainda vai
                        // sair e quanto sobra depois de pagar tudo
                        aPagarMes = pendenteMes,
                        saldoAposPagar = saldo - pendenteMes,
                        // Na empresa os cards Receita/Despesa + Lucro abaixo já detalham
                        mostrarResumoMes = !cnpj,
                        // O resumo é do mês exibido, não necessariamente o atual
                        rotuloMes = if (ehMesAtual) "Este mês" else {
                            "Em " + mesSelecionado.month
                                .getDisplayName(TextStyle.FULL, Formatadores.LOCALE_BR)
                                .replaceFirstChar { it.uppercase(Formatadores.LOCALE_BR) }
                        }
                    )
                }
                // Orçamento do mês (só quando alguma categoria tem teto)
                orcamento?.let { orc ->
                    item(key = "orcamento") {
                        Spacer(modifier = Modifier.height(12.dp))
                        OrcamentoCard(orcamento = orc)
                    }
                }
                if (cnpj) {
                    item(key = "cards-cnpj") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ResumoCard(
                                tipo = TipoTransacao.GANHO,
                                valor = ganhos,
                                modifier = Modifier.weight(1f),
                                rotuloCustom = "Receita"
                            )
                            ResumoCard(
                                tipo = TipoTransacao.GASTO,
                                valor = gastos,
                                modifier = Modifier.weight(1f),
                                rotuloCustom = "Despesa"
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        LucroCard(lucro = ganhos - gastos)
                    }
                }
                item(key = "titulo-transacoes") {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = if (ehMesAtual) "TRANSAÇÕES DO MÊS"
                        else "TRANSAÇÕES · ${rotuloMes(mesSelecionado).uppercase(Formatadores.LOCALE_BR)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (transacoesDoMes.isEmpty()) {
                    item(key = "vazio") {
                        Column(
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .padding(vertical = 48.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (ehMesAtual) "Nenhuma transação ainda"
                                else "Nenhuma transação em ${rotuloMes(mesSelecionado)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (ehMesAtual) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        transacaoEmEdicao = null
                                        modalAberto = true
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Adicionar transação")
                                }
                            }
                        }
                    }
                } else {
                    gruposCartao.forEach { grupo ->
                        val expandido = grupo.cartaoUuid in cartoesExpandidos
                        // O card inteiro (cabeçalho + compras) num único item
                        item(key = "cartao-${grupo.cartaoUuid}") {
                            CartaoGrupoCard(
                                grupo = grupo,
                                expandido = expandido,
                                onPagarFatura = { viewModel.pagarFatura(grupo.transacoes) },
                                onAlternar = {
                                    cartoesExpandidos = if (expandido) {
                                        cartoesExpandidos - grupo.cartaoUuid
                                    } else {
                                        cartoesExpandidos + grupo.cartaoUuid
                                    }
                                }
                            ) {
                                grupo.transacoes.forEach { transacao ->
                                    linhaTransacao(transacao, corItemCartao)
                                }
                            }
                        }
                    }
                    avulsasPorDia.forEach { (dia, transacoesDoDia) ->
                        item(key = "dia-${dia.toEpochDay()}") {
                            CabecalhoDia(dia = dia, hoje = dataAtual)
                        }
                        items(transacoesDoDia, key = { it.id }) { transacao ->
                            linhaTransacao(transacao, null)
                        }
                    }
                }
            }
        }
    }

    // ---------- Atualização disponível (GitHub Releases) ----------
    val atualizacao by viewModel.atualizacao.collectAsStateWithLifecycle()
    atualizacao?.let { nova ->
        val contexto = LocalContext.current
        val download by viewModel.downloadAtualizacao.collectAsStateWithLifecycle()
        val baixando = download is EstadoDownload.Baixando
        AlertDialog(
            // Sem fechar por toque fora durante o download
            onDismissRequest = {
                if (!baixando) viewModel.dispensarAtualizacao()
            },
            title = { Text("Nova versão ${nova.versao} disponível") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (val estado = download) {
                        is EstadoDownload.Baixando -> {
                            Text("Baixando atualização…")
                            val progresso = estado.progresso
                            if (progresso != null) {
                                LinearProgressIndicator(
                                    progress = { progresso },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }

                        EstadoDownload.Erro -> Text(
                            "Não foi possível baixar a atualização. " +
                                "Tente de novo ou baixe pelo navegador."
                        )

                        EstadoDownload.Ocioso -> Text(
                            if (nova.notas.isBlank()) {
                                "Uma atualização do GoodFinances está pronta para instalar."
                            } else {
                                nova.notas.take(600)
                            }
                        )
                    }
                }
            },
            confirmButton = {
                when (download) {
                    is EstadoDownload.Baixando -> Unit

                    EstadoDownload.Erro -> {
                        TextButton(
                            onClick = {
                                contexto.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(nova.urlDownload))
                                )
                                viewModel.dispensarAtualizacao()
                            }
                        ) {
                            Text("Abrir no navegador")
                        }
                        TextButton(onClick = viewModel::baixarAtualizacao) {
                            Text("Tentar de novo")
                        }
                    }

                    EstadoDownload.Ocioso -> TextButton(
                        onClick = {
                            if (nova.temApk) {
                                viewModel.baixarAtualizacao()
                            } else {
                                // Release sem APK anexado: só a página no navegador
                                contexto.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(nova.urlDownload))
                                )
                                viewModel.dispensarAtualizacao()
                            }
                        }
                    ) {
                        Text(if (nova.temApk) "Atualizar" else "Baixar")
                    }
                }
            },
            dismissButton = {
                if (!baixando) {
                    TextButton(onClick = viewModel::dispensarAtualizacao) {
                        Text("Agora não")
                    }
                }
            }
        )
    }

    if (mesPickerAberto) {
        SeletorMesAno(
            mesAtual = mesSelecionado,
            onSelecionar = {
                viewModel.selecionarMes(it)
                mesPickerAberto = false
            },
            onFechar = { mesPickerAberto = false }
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

/**
 * Resumo do orçamento do mês exibido: barra de progresso do gasto sobre a
 * soma dos tetos por categoria (o detalhe por categoria fica na Análise).
 */
@Composable
private fun OrcamentoCard(orcamento: com.finapp.viewmodel.OrcamentoMes) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "ORÇAMENTO DO MÊS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(orcamento.fracao * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (orcamento.estourado) RedExpense else GreenPrimary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { orcamento.fracao.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = if (orcamento.estourado) RedExpense else GreenPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${Formatadores.moeda(orcamento.gasto)} de " +
                    "${Formatadores.moeda(orcamento.teto)} orçados" +
                    if (orcamento.estourado) " — teto estourado" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Cabeçalho de um dia no histórico: HOJE / ONTEM / "15 DE JUN, DOMINGO". */
@Composable
private fun CabecalhoDia(dia: java.time.LocalDate, hoje: java.time.LocalDate) {
    val rotulo = when (dia) {
        hoje -> "HOJE"
        hoje.minusDays(1) -> "ONTEM"
        else -> Formatadores.dataAgrupamento(dia)
    }
    Text(
        text = rotulo,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp, start = 4.dp)
    )
}

/** Rótulo "Julho 2026" a partir de um YearMonth (pt-BR). */
private fun rotuloMes(mes: YearMonth): String {
    val nome = mes.month
        .getDisplayName(TextStyle.FULL, Formatadores.LOCALE_BR)
        .replaceFirstChar { it.uppercase(Formatadores.LOCALE_BR) }
    return "$nome ${mes.year}"
}

/**
 * Navegação do mês: ‹ Mês Ano › (toque no rótulo abre o seletor de mês/ano).
 * Fora do mês atual, um "Hoje" inline volta sem gastar uma linha extra.
 */
@Composable
private fun BarraMes(
    mes: YearMonth,
    ehMesAtual: Boolean,
    onAnterior: () -> Unit,
    onProximo: () -> Unit,
    onAbrirPicker: () -> Unit,
    onHoje: () -> Unit,
    onBuscar: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onAnterior) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = "Mês anterior"
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onAbrirPicker),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = rotuloMes(mes),
                style = MaterialTheme.typography.titleMedium,
                color = if (ehMesAtual) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            if (!ehMesAtual) {
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onHoje,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("Hoje")
                }
            }
        }
        IconButton(onClick = onProximo) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Próximo mês"
            )
        }
        IconButton(onClick = onBuscar) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Buscar transações"
            )
        }
    }
}

/**
 * Campo de busca que substitui a barra do mês: foca sozinho ao abrir;
 * o X limpa e volta para a navegação normal.
 */
@Composable
private fun BarraBusca(
    termo: String,
    onTermo: (String) -> Unit,
    onFechar: () -> Unit
) {
    val foco = remember { FocusRequester() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = termo,
            onValueChange = onTermo,
            modifier = Modifier
                .weight(1f)
                .focusRequester(foco),
            placeholder = { Text("Buscar por descrição ou categoria") },
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Search, contentDescription = null)
            },
            singleLine = true
        )
        IconButton(onClick = onFechar) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Fechar busca"
            )
        }
    }
    LaunchedEffect(Unit) { foco.requestFocus() }
}

/**
 * Resultados da busca, agrupados por dia como o histórico. [linha] reusa a
 * mesma TransacaoLinha da lista principal (editar/menu funcionam igual).
 */
@Composable
private fun ListaBusca(
    resultados: List<Transacao>,
    termo: String,
    hoje: java.time.LocalDate,
    linha: @Composable (Transacao, Color?) -> Unit
) {
    val porDia = remember(resultados) { resultados.groupBy { it.data } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        when {
            termo.trim().length < 2 -> item(key = "dica") {
                Text(
                    text = "Digite pelo menos 2 letras para buscar em todos os meses.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
            resultados.isEmpty() -> item(key = "sem-resultado") {
                Text(
                    text = "Nada encontrado para \"${termo.trim()}\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
            else -> {
                item(key = "total") {
                    Text(
                        text = "${resultados.size} lançamento(s) encontrado(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                porDia.forEach { (dia, transacoesDoDia) ->
                    item(key = "busca-dia-${dia.toEpochDay()}") {
                        CabecalhoDia(dia = dia, hoje = hoje)
                    }
                    items(transacoesDoDia, key = { it.id }) { transacao ->
                        linha(transacao, null)
                    }
                }
            }
        }
    }
}

/** Seletor de qualquer mês/ano: setas de ano + grade de 12 meses. */
@Composable
private fun SeletorMesAno(
    mesAtual: YearMonth,
    onSelecionar: (YearMonth) -> Unit,
    onFechar: () -> Unit
) {
    var ano by remember { mutableStateOf(mesAtual.year) }
    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Escolher mês") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { ano -= 1 }) {
                        Icon(
                            imageVector = Icons.Filled.ChevronLeft,
                            contentDescription = "Ano anterior"
                        )
                    }
                    Text(
                        text = ano.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    IconButton(onClick = { ano += 1 }) {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "Próximo ano"
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                for (linha in 0..3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (coluna in 0..2) {
                            val numeroMes = linha * 3 + coluna + 1
                            val ym = YearMonth.of(ano, numeroMes)
                            FilterChip(
                                selected = ym == mesAtual,
                                onClick = { onSelecionar(ym) },
                                label = {
                                    Text(
                                        Month.of(numeroMes)
                                            .getDisplayName(
                                                TextStyle.SHORT,
                                                Formatadores.LOCALE_BR
                                            )
                                            .replaceFirstChar {
                                                it.uppercase(Formatadores.LOCALE_BR)
                                            }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onFechar) { Text("Fechar") }
        }
    )
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
