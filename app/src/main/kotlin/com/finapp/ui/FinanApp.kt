package com.finapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.ui.component.TransacaoModal
import com.finapp.ui.component.TransferenciaDialog
import com.finapp.ui.screen.AnaliseScreen
import com.finapp.ui.screen.ConfigScreen
import com.finapp.ui.screen.HomeScreen
import com.finapp.ui.screen.OnibusScreen
import com.finapp.ui.screen.PerfilSelecaoScreen
import com.finapp.viewmodel.PerfilViewModel
import com.finapp.viewmodel.TransacaoViewModel

/** Destinos da Bottom Navigation (só ícones — barra compacta). */
enum class FinanDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME("home", "Home", Icons.Filled.Home),
    ANALISE("analise", "Análise", Icons.Filled.PieChart),
    ONIBUS("onibus", "Ônibus", Icons.Filled.DirectionsBus),
    CONFIG("config", "Config", Icons.Filled.Settings)
}

/** [abrirLancamento] = chegada pelo widget: abre direto o modal de nova transação. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanApp(
    abrirLancamento: Boolean = false,
    perfilViewModel: PerfilViewModel = hiltViewModel(),
    transacaoViewModel: TransacaoViewModel = hiltViewModel()
) {
    val perfilFoiEscolhido by perfilViewModel.perfilFoiEscolhido.collectAsStateWithLifecycle()

    // Primeira abertura: escolher o modo de uso antes de entrar no app
    if (!perfilFoiEscolhido) {
        PerfilSelecaoScreen(onConcluir = perfilViewModel::escolherModo)
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Estado do botão + central (ações rápidas) e dos diálogos que ele abre
    val perfilDados by transacaoViewModel.perfil.collectAsStateWithLifecycle()
    val contextos by transacaoViewModel.contextos.collectAsStateWithLifecycle()
    var menuAcoesAberto by remember { mutableStateOf(false) }
    // Modal de nova transação hospedado aqui (funciona de qualquer aba)
    var modalTipo by remember { mutableStateOf<TipoTransacao?>(null) }
    var transferenciaAberta by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        transacaoViewModel.mensagens.collect { snackbarHostState.showSnackbar(it) }
    }

    // Chegada pelo widget: abre o modal de gasto uma única vez
    var lancamentoWidget by rememberSaveable { mutableStateOf(abrirLancamento) }
    LaunchedEffect(lancamentoWidget) {
        if (lancamentoWidget) {
            modalTipo = TipoTransacao.GASTO
            lancamentoWidget = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BarraInferior(
                navController = navController,
                selecionado = { destino ->
                    currentDestination?.hierarchy?.any { it.route == destino.route } == true
                },
                onAdicionar = { menuAcoesAberto = true }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = FinanDestination.HOME.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(FinanDestination.HOME.route) { HomeScreen() }
            composable(FinanDestination.ANALISE.route) { AnaliseScreen() }
            composable(FinanDestination.ONIBUS.route) { OnibusScreen() }
            composable(FinanDestination.CONFIG.route) { ConfigScreen() }
        }
    }

    // Ações rápidas do botão +: Ganho / Gasto / Transferência
    if (menuAcoesAberto) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { menuAcoesAberto = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.navigationBarsPadding()) {
                AcaoRapida(
                    icone = Icons.Filled.ArrowUpward,
                    cor = com.finapp.ui.theme.GreenPrimary,
                    titulo = "Novo ganho",
                    onClick = {
                        menuAcoesAberto = false
                        modalTipo = TipoTransacao.GANHO
                    }
                )
                AcaoRapida(
                    icone = Icons.Filled.ArrowDownward,
                    cor = com.finapp.ui.theme.RedExpense,
                    titulo = "Novo gasto",
                    onClick = {
                        menuAcoesAberto = false
                        modalTipo = TipoTransacao.GASTO
                    }
                )
                if (contextos.size > 1) {
                    AcaoRapida(
                        icone = Icons.Filled.SwapHoriz,
                        cor = MaterialTheme.colorScheme.primary,
                        titulo = "Transferência entre contextos",
                        onClick = {
                            menuAcoesAberto = false
                            transferenciaAberta = true
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    modalTipo?.let { tipo ->
        TransacaoModal(
            onFechar = { modalTipo = null },
            tipoInicial = tipo,
            viewModel = transacaoViewModel
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

    // Dicas iniciais: mostradas uma única vez, logo após escolher o modo
    val dicasVistas by perfilViewModel.dicasVistas.collectAsStateWithLifecycle()
    if (!dicasVistas) {
        DicasIniciaisDialog(onFechar = perfilViewModel::dispensarDicas)
    }
}

/** Barra inferior compacta (só ícones) com o botão + central de ações rápidas. */
@Composable
private fun BarraInferior(
    navController: NavHostController,
    selecionado: (FinanDestination) -> Boolean,
    onAdicionar: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconeNav(FinanDestination.HOME, selecionado(FinanDestination.HOME), navController)
            IconeNav(FinanDestination.ANALISE, selecionado(FinanDestination.ANALISE), navController)
            // Botão + central
            FloatingActionButton(
                onClick = onAdicionar,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Adicionar")
            }
            IconeNav(FinanDestination.ONIBUS, selecionado(FinanDestination.ONIBUS), navController)
            IconeNav(FinanDestination.CONFIG, selecionado(FinanDestination.CONFIG), navController)
        }
    }
}

/** Ícone de navegação (sem rótulo) que troca de aba preservando estado. */
@Composable
private fun IconeNav(
    destino: FinanDestination,
    selecionado: Boolean,
    navController: NavHostController
) {
    val cor = if (selecionado) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable {
                navController.navigate(destino.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = destino.icon,
            contentDescription = destino.label,
            tint = cor
        )
    }
}

/** Linha de ação rápida do menu do botão +. */
@Composable
private fun AcaoRapida(
    icone: ImageVector,
    cor: androidx.compose.ui.graphics.Color,
    titulo: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(cor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icone, contentDescription = null, tint = cor)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = titulo,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Dica exibida no onboarding: emoji + título + descrição. */
private data class Dica(val emoji: String, val titulo: String, val descricao: String)

/** Dialog de boas-vindas com as principais funcionalidades do app. */
@Composable
private fun DicasIniciaisDialog(onFechar: () -> Unit) {
    val dicas = listOf(
        Dica("➕", "Botão + no centro", "Toque no + da barra de baixo para lançar um ganho, um gasto ou uma transferência."),
        Dica("↔️", "Deslize na Home", "Arraste para os lados para trocar entre Pessoal, Empresa e Casa."),
        Dica("💳", "Cartão e parcelas", "Cadastre cartões em Configurações e lance compras no crédito, à vista ou parceladas."),
        Dica("🚌", "Ônibus", "Configure a passagem e os dias e veja até quando o saldo do cartão dura."),
        Dica("🔔", "Avisos", "O app avisa quando o orçamento estoura, o DAS vence ou uma conta recorrente entra.")
    )
    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Bem-vindo ao GoodFinances") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                dicas.forEach { dica ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = dica.emoji, style = MaterialTheme.typography.titleLarge)
                        Column {
                            Text(
                                text = dica.titulo,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = dica.descricao,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onFechar) {
                Text("Começar")
            }
        }
    )
}
