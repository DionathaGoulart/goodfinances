package com.finapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finapp.data.db.entities.ContaAgendada
import com.finapp.data.db.entities.Meta
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.ui.theme.GreenPrimary
import com.finapp.ui.theme.OrangeAlert
import com.finapp.ui.theme.RedExpense
import com.finapp.utils.Formatadores
import com.finapp.viewmodel.PlanejamentoViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Paleta para escolher a cor de uma meta. */
private val CORES_META = listOf(
    "#10B981", "#3B82F6", "#8B5CF6", "#EF4444", "#F59E0B", "#EC4899", "#14B8A6"
)

/** Tela de planejamento: metas de economia + contas a pagar/receber. */
@Composable
fun PlanejamentoScreen(viewModel: PlanejamentoViewModel = hiltViewModel()) {
    val metas by viewModel.metas.collectAsStateWithLifecycle()
    val contas by viewModel.contasPendentes.collectAsStateWithLifecycle()
    val categorias by viewModel.categorias.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.mensagens.collect { snackbarHostState.showSnackbar(it) }
    }

    var criarMetaAberto by remember { mutableStateOf(false) }
    var criarContaAberto by remember { mutableStateOf(false) }
    var metaAporte by remember { mutableStateOf<Meta?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ---------- Metas de economia ----------
            CabecalhoSecao(titulo = "METAS", onAdicionar = { criarMetaAberto = true })
            Spacer(modifier = Modifier.height(8.dp))
            if (metas.isEmpty()) {
                TextoVazio("Nenhuma meta. Crie um objetivo e acompanhe o progresso.")
            } else {
                metas.forEachIndexed { indice, meta ->
                    if (indice > 0) Spacer(modifier = Modifier.height(8.dp))
                    MetaCard(
                        meta = meta,
                        onAportar = { metaAporte = meta },
                        onRemover = { viewModel.removerMeta(meta) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ---------- Contas a pagar/receber ----------
            CabecalhoSecao(titulo = "CONTAS A PAGAR/RECEBER", onAdicionar = { criarContaAberto = true })
            Spacer(modifier = Modifier.height(8.dp))
            if (contas.isEmpty()) {
                TextoVazio("Nenhuma conta agendada. Cadastre um boleto ou valor a receber.")
            } else {
                contas.forEachIndexed { indice, conta ->
                    if (indice > 0) Spacer(modifier = Modifier.height(8.dp))
                    ContaCard(
                        conta = conta,
                        onPagar = { viewModel.pagarConta(conta) },
                        onRemover = { viewModel.removerConta(conta) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (criarMetaAberto) {
        CriarMetaDialog(
            onConfirmar = { nome, valor, prazo, cor ->
                viewModel.criarMeta(nome, valor, prazo, cor)
                criarMetaAberto = false
            },
            onFechar = { criarMetaAberto = false }
        )
    }

    metaAporte?.let { meta ->
        AporteDialog(
            meta = meta,
            onConfirmar = { delta ->
                viewModel.aportarMeta(meta, delta)
                metaAporte = null
            },
            onFechar = { metaAporte = null }
        )
    }

    if (criarContaAberto) {
        CriarContaDialog(
            categorias = categorias.map { it.nome to it.tipo },
            onConfirmar = { descricao, valor, tipo, categoria, vencimento ->
                viewModel.criarConta(descricao, valor, tipo, categoria, vencimento)
                criarContaAberto = false
            },
            onFechar = { criarContaAberto = false }
        )
    }
}

@Composable
private fun CabecalhoSecao(titulo: String, onAdicionar: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onAdicionar) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = "Adicionar")
        }
    }
}

@Composable
private fun TextoVazio(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun MetaCard(meta: Meta, onAportar: () -> Unit, onRemover: () -> Unit) {
    val cor = runCatching { Color(meta.cor.toColorInt()) }
        .getOrDefault(MaterialTheme.colorScheme.primary)
    val fracao = if (meta.valorAlvo <= 0) 0f
    else (meta.valorGuardado.toDouble() / meta.valorAlvo).toFloat()
    val concluida = fracao >= 1f
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(cor)
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = meta.nome,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(fracao * 100).roundToInt().coerceAtMost(100)}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (concluida) GreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { fracao.coerceIn(0f, 1f) },
                color = if (concluida) GreenPrimary else cor,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${Formatadores.moeda(meta.valorGuardado)} de " +
                    Formatadores.moeda(meta.valorAlvo) +
                    (meta.prazo?.let { " · até ${Formatadores.dataCurta(it)}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAportar,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) { Text("Guardar / retirar") }
                OutlinedButton(
                    onClick = onRemover,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedExpense)
                ) { Text("Remover") }
            }
        }
    }
}

@Composable
private fun ContaCard(conta: ContaAgendada, onPagar: () -> Unit, onRemover: () -> Unit) {
    val hoje = LocalDate.now()
    val dias = ChronoUnit.DAYS.between(hoje, conta.vencimento)
    val corPrazo = when {
        dias < 0 -> RedExpense
        dias <= 3 -> OrangeAlert
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val textoPrazo = when {
        dias < 0 -> "venceu há ${-dias} ${if (dias == -1L) "dia" else "dias"}"
        dias == 0L -> "vence hoje"
        dias == 1L -> "vence amanhã"
        else -> "vence em $dias dias"
    }
    val corValor = if (conta.tipo == TipoTransacao.GASTO) RedExpense else GreenPrimary
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = conta.descricao,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${conta.categoria} · $textoPrazo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = corPrazo
                    )
                }
                Text(
                    text = Formatadores.moeda(conta.valor),
                    style = MaterialTheme.typography.titleMedium,
                    color = corValor
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPagar,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) { Text(if (conta.tipo == TipoTransacao.GASTO) "Marcar paga" else "Marcar recebida") }
                OutlinedButton(
                    onClick = onRemover,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedExpense)
                ) { Text("Remover") }
            }
        }
    }
}

/** Campo de valor com máscara de centavos BR (digite 1234 -> R$ 12,34). */
@Composable
private fun CampoValor(
    digitos: String,
    onDigitos: (String) -> Unit,
    rotulo: String = "Valor"
) {
    OutlinedTextField(
        value = if (digitos.isEmpty()) "" else Formatadores.moeda(digitos.toLong()),
        onValueChange = { novo ->
            onDigitos(novo.filter(Char::isDigit).trimStart('0').take(10))
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(rotulo) },
        placeholder = { Text("R$ 0,00") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}

@Composable
private fun SeletorCorMeta(selecionada: String, onSelecionar: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CORES_META.forEach { hex ->
            val cor = runCatching { Color(hex.toColorInt()) }.getOrDefault(Color.Gray)
            val selecionado = hex == selecionada
            Box(
                modifier = Modifier
                    .size(if (selecionado) 32.dp else 26.dp)
                    .clip(CircleShape)
                    .background(cor)
                    .clickable { onSelecionar(hex) },
                contentAlignment = Alignment.Center
            ) {
                if (selecionado) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }
    }
}

@Composable
private fun CriarMetaDialog(
    onConfirmar: (String, Long, LocalDate?, String) -> Unit,
    onFechar: () -> Unit
) {
    var nome by remember { mutableStateOf("") }
    var digitos by remember { mutableStateOf("") }
    var cor by remember { mutableStateOf(CORES_META.first()) }
    var prazo by remember { mutableStateOf<LocalDate?>(null) }
    var datePickerAberto by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Nova meta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { if (it.length <= 40) nome = it },
                    label = { Text("Nome (ex: Viagem)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                CampoValor(digitos = digitos, onDigitos = { digitos = it }, rotulo = "Valor alvo")
                OutlinedButton(
                    onClick = { datePickerAberto = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(prazo?.let { "Prazo: ${Formatadores.dataCurta(it)}" } ?: "Definir prazo (opcional)")
                }
                Text(
                    text = "Cor",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SeletorCorMeta(selecionada = cor, onSelecionar = { cor = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirmar(nome, digitos.toLongOrNull() ?: 0L, prazo, cor)
            }) { Text("Criar") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } }
    )

    if (datePickerAberto) {
        SeletorData(
            inicial = prazo ?: LocalDate.now(),
            onSelecionar = { prazo = it; datePickerAberto = false },
            onFechar = { datePickerAberto = false }
        )
    }
}

@Composable
private fun AporteDialog(meta: Meta, onConfirmar: (Long) -> Unit, onFechar: () -> Unit) {
    var digitos by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text(meta.nome) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Guardado: ${Formatadores.moeda(meta.valorGuardado)} de " +
                        Formatadores.moeda(meta.valorAlvo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CampoValor(digitos = digitos, onDigitos = { digitos = it }, rotulo = "Valor")
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    val v = digitos.toLongOrNull() ?: 0L
                    if (v > 0) onConfirmar(-v)
                }) { Text("Retirar") }
                TextButton(onClick = {
                    val v = digitos.toLongOrNull() ?: 0L
                    if (v > 0) onConfirmar(v)
                }) { Text("Guardar") }
            }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Fechar") } }
    )
}

@Composable
private fun CriarContaDialog(
    categorias: List<Pair<String, TipoTransacao>>,
    onConfirmar: (String, Long, TipoTransacao, String, LocalDate) -> Unit,
    onFechar: () -> Unit
) {
    var descricao by remember { mutableStateOf("") }
    var digitos by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf(TipoTransacao.GASTO) }
    var vencimento by remember { mutableStateOf(LocalDate.now().plusDays(7)) }
    var datePickerAberto by remember { mutableStateOf(false) }

    val doTipo = categorias.filter { it.second == tipo }.map { it.first }.distinct()
    var categoria by remember(tipo) { mutableStateOf(doTipo.firstOrNull() ?: "Outros") }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Nova conta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TipoTransacao.entries.forEach { opcao ->
                        val selecionado = tipo == opcao
                        Button(
                            onClick = { tipo = opcao },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selecionado) {
                                    if (opcao == TipoTransacao.GANHO) GreenPrimary else RedExpense
                                } else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selecionado) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) { Text(if (opcao == TipoTransacao.GANHO) "A receber" else "A pagar") }
                    }
                }
                OutlinedTextField(
                    value = descricao,
                    onValueChange = { if (it.length <= 60) descricao = it },
                    label = { Text("Descrição") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                CampoValor(digitos = digitos, onDigitos = { digitos = it })
                // Categoria: chips das categorias do tipo escolhido
                if (doTipo.isNotEmpty()) {
                    Text(
                        text = "Categoria",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        doTipo.chunked(3).forEach { linha ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                linha.forEach { nome ->
                                    OutlinedButton(
                                        onClick = { categoria = nome },
                                        colors = if (categoria == nome) {
                                            ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        } else {
                                            ButtonDefaults.outlinedButtonColors()
                                        }
                                    ) { Text(nome, style = MaterialTheme.typography.bodyMedium) }
                                }
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { datePickerAberto = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Vencimento: ${Formatadores.dataCurta(vencimento)}")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirmar(descricao, digitos.toLongOrNull() ?: 0L, tipo, categoria, vencimento)
            }) { Text("Agendar") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } }
    )

    if (datePickerAberto) {
        SeletorData(
            inicial = vencimento,
            onSelecionar = { vencimento = it; datePickerAberto = false },
            onFechar = { datePickerAberto = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorData(
    inicial: LocalDate,
    onSelecionar: (LocalDate) -> Unit,
    onFechar: () -> Unit
) {
    val estado = rememberDatePickerState(
        initialSelectedDateMillis = inicial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onFechar,
        confirmButton = {
            TextButton(onClick = {
                estado.selectedDateMillis?.let { millis ->
                    onSelecionar(
                        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    )
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } }
    ) {
        DatePicker(state = estado)
    }
}
