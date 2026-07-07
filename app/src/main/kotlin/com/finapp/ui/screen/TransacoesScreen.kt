package com.finapp.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
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
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.Transacao
import com.finapp.ui.component.TransacaoLinha
import com.finapp.ui.component.TransacaoModal
import com.finapp.ui.theme.BluAccent
import com.finapp.utils.Formatadores
import com.finapp.utils.PeriodoFiltro
import com.finapp.viewmodel.TransacaoViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset

/** Histórico completo: busca, filtros de período e lista agrupada por data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransacoesScreen(viewModel: TransacaoViewModel = hiltViewModel()) {
    val transacoes by viewModel.transacoes.collectAsStateWithLifecycle()
    val filtro by viewModel.filtro.collectAsStateWithLifecycle()
    val busca by viewModel.busca.collectAsStateWithLifecycle()
    val filtroTipo by viewModel.filtroTipo.collectAsStateWithLifecycle()
    val filtroCategoria by viewModel.filtroCategoria.collectAsStateWithLifecycle()
    val membrosCasa by viewModel.membrosCasa.collectAsStateWithLifecycle()
    val filtroMembro by viewModel.filtroMembro.collectAsStateWithLifecycle()
    val categoriasGanho by viewModel.categoriasGanho.collectAsStateWithLifecycle()
    val categoriasGasto by viewModel.categoriasGasto.collectAsStateWithLifecycle()
    val perfilDados by viewModel.perfil.collectAsStateWithLifecycle()
    val compartilhando by viewModel.compartilhandoComCasa.collectAsStateWithLifecycle()
    // "Esconder" só aparece nos baldes pessoais e com compartilhamento ligado
    val podeEsconder = compartilhando && perfilDados in Perfil.BALDES_PESSOAIS

    val snackbarHostState = remember { SnackbarHostState() }
    val escopo = rememberCoroutineScope()

    var modalAberto by remember { mutableStateOf(false) }
    var transacaoEmEdicao by remember { mutableStateOf<Transacao?>(null) }
    var rangePickerAberto by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.mensagens.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "TRANSAÇÕES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ---------- Busca ----------
            OutlinedTextField(
                value = busca,
                onValueChange = viewModel::buscar,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar por descrição...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = "Buscar")
                },
                trailingIcon = {
                    if (busca.isNotEmpty()) {
                        IconButton(onClick = { viewModel.buscar("") }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Limpar busca"
                            )
                        }
                    }
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ---------- Filtros de período ----------
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PeriodoFiltro.entries.forEach { opcao ->
                    FilterChip(
                        selected = filtro == opcao,
                        onClick = {
                            if (opcao == PeriodoFiltro.PERSONALIZADO) {
                                rangePickerAberto = true
                            } else {
                                viewModel.filtrar(opcao)
                            }
                        },
                        label = { Text(opcao.rotulo) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BluAccent,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ---------- Filtros por tipo e categoria ----------
            var menuCategoriaAberto by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filtroTipo == TipoTransacao.GANHO,
                    onClick = {
                        viewModel.filtrarTipo(
                            if (filtroTipo == TipoTransacao.GANHO) null
                            else TipoTransacao.GANHO
                        )
                    },
                    label = { Text("Ganhos") }
                )
                FilterChip(
                    selected = filtroTipo == TipoTransacao.GASTO,
                    onClick = {
                        viewModel.filtrarTipo(
                            if (filtroTipo == TipoTransacao.GASTO) null
                            else TipoTransacao.GASTO
                        )
                    },
                    label = { Text("Gastos") }
                )
                Box {
                    FilterChip(
                        selected = filtroCategoria != null,
                        onClick = { menuCategoriaAberto = true },
                        label = { Text(filtroCategoria ?: "Categoria") }
                    )
                    DropdownMenu(
                        expanded = menuCategoriaAberto,
                        onDismissRequest = { menuCategoriaAberto = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Todas") },
                            onClick = {
                                viewModel.filtrarCategoria(null)
                                menuCategoriaAberto = false
                            }
                        )
                        // Respeita o filtro de tipo ativo
                        val nomes = when (filtroTipo) {
                            TipoTransacao.GANHO -> categoriasGanho
                            TipoTransacao.GASTO -> categoriasGasto
                            null -> categoriasGanho + categoriasGasto
                        }.map { it.nome }.distinct().sorted()
                        nomes.forEach { nome ->
                            DropdownMenuItem(
                                text = { Text(nome) },
                                onClick = {
                                    viewModel.filtrarCategoria(nome)
                                    menuCategoriaAberto = false
                                }
                            )
                        }
                    }
                }
            }

            // ---------- Filtro por membro (só no perfil Casa) ----------
            if (membrosCasa.size > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filtroMembro == null,
                        onClick = { viewModel.filtrarMembro(null) },
                        label = { Text("Todos") }
                    )
                    membrosCasa.forEach { nome ->
                        FilterChip(
                            selected = filtroMembro == nome,
                            onClick = {
                                viewModel.filtrarMembro(
                                    if (filtroMembro == nome) null else nome
                                )
                            },
                            label = { Text(nome.substringBefore(' ')) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ---------- Lista agrupada por data ----------
            if (transacoes.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (busca.isNotBlank()) "Nada encontrado para \"$busca\""
                        else "Nenhuma transação neste período",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val grupos = remember(transacoes) { transacoes.groupBy { it.data } }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    grupos.forEach { (data, doDia) ->
                        item(key = "header-$data") {
                            Text(
                                text = Formatadores.dataAgrupamento(data),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                            )
                        }
                        items(doDia, key = { it.id }) { transacao ->
                            val podeEditar = viewModel.podeEditar(transacao)
                            TransacaoLinha(
                                transacao = transacao,
                                mostrarData = false,
                                podeEditar = podeEditar,
                                podeEsconder = podeEsconder,
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
                                            viewModel.desfazerDelete()
                                        }
                                    }
                                },
                                onAlternarOculto = viewModel::alternarOculto,
                                onBloqueado = {
                                    escopo.launch {
                                        snackbarHostState.showSnackbar(
                                            "Só quem lançou pode editar esta transação"
                                        )
                                    }
                                }
                            )
                        }
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
                viewModel.deletarTransacao(deletada)
                escopo.launch {
                    val resultado = snackbarHostState.showSnackbar(
                        message = "Transação deletada",
                        actionLabel = "Desfazer",
                        duration = SnackbarDuration.Short
                    )
                    if (resultado == SnackbarResult.ActionPerformed) {
                        viewModel.desfazerDelete()
                    }
                }
            },
            viewModel = viewModel
        )
    }

    // ---------- Seletor de período customizado ----------
    if (rangePickerAberto) {
        val estadoRange = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { rangePickerAberto = false },
            confirmButton = {
                TextButton(
                    enabled = estadoRange.selectedEndDateMillis != null,
                    onClick = {
                        val inicioMillis = estadoRange.selectedStartDateMillis
                        val fimMillis = estadoRange.selectedEndDateMillis
                        if (inicioMillis != null && fimMillis != null) {
                            viewModel.definirPeriodoCustom(
                                inicio = Instant.ofEpochMilli(inicioMillis)
                                    .atZone(ZoneOffset.UTC).toLocalDate(),
                                fim = Instant.ofEpochMilli(fimMillis)
                                    .atZone(ZoneOffset.UTC).toLocalDate()
                            )
                        }
                        rangePickerAberto = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { rangePickerAberto = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DateRangePicker(
                state = estadoRange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                title = {
                    Text(
                        text = "Selecione o período",
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                    )
                }
            )
        }
    }
}
