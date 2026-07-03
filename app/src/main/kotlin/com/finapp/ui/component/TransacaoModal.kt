package com.finapp.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.Transacao
import com.finapp.ui.theme.GreenPrimary
import com.finapp.ui.theme.RedExpense
import com.finapp.utils.Formatadores
import com.finapp.viewmodel.TransacaoViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Modal (bottom sheet) de Nova Transação / Edição.
 * Se [transacaoParaEditar] for informada, entra em modo edição: campos
 * pré-preenchidos, botão "ATUALIZAR" e opção de deletar.
 * [onDeletar] permite ao chamador tratar a deleção (snackbar com desfazer);
 * se ausente, deleta direto pelo ViewModel.
 * Feedback de sucesso/erro chega via `TransacaoViewModel.mensagens`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransacaoModal(
    onFechar: () -> Unit,
    transacaoParaEditar: Transacao? = null,
    onDeletar: ((Transacao) -> Unit)? = null,
    viewModel: TransacaoViewModel = hiltViewModel()
) {
    val edicao = transacaoParaEditar != null
    val categoriaOriginal = transacaoParaEditar?.categoria

    var tipo by remember { mutableStateOf(transacaoParaEditar?.tipo ?: TipoTransacao.GANHO) }
    // Valor em centavos, como texto (máscara de moeda BR)
    var digitos by remember {
        mutableStateOf(transacaoParaEditar?.valor?.toString() ?: "")
    }
    var textoCategoria by remember { mutableStateOf(categoriaOriginal ?: "Outros") }
    var descricao by remember { mutableStateOf(transacaoParaEditar?.descricao ?: "") }
    var data by remember { mutableStateOf(transacaoParaEditar?.data ?: LocalDate.now()) }
    var repetirMensalmente by remember { mutableStateOf(false) }

    var erroValor by remember { mutableStateOf<String?>(null) }
    var erroCategoria by remember { mutableStateOf<String?>(null) }
    var dropdownAberto by remember { mutableStateOf(false) }
    var datePickerAberto by remember { mutableStateOf(false) }

    val categorias by remember(tipo) {
        if (tipo == TipoTransacao.GANHO) viewModel.categoriasGanho else viewModel.categoriasGasto
    }.collectAsStateWithLifecycle()

    // Ao trocar Ganho/Gasto, mantém a categoria se existir no novo tipo.
    // Em edição, a categoria original vale mesmo se estiver arquivada.
    LaunchedEffect(tipo, categorias) {
        val ehOriginalValida = edicao &&
            tipo == transacaoParaEditar?.tipo &&
            textoCategoria.equals(categoriaOriginal, ignoreCase = true)
        if (!ehOriginalValida &&
            categorias.isNotEmpty() &&
            categorias.none { it.nome.equals(textoCategoria, ignoreCase = true) }
        ) {
            textoCategoria = categorias.firstOrNull { it.nome == "Outros" }?.nome
                ?: categorias.first().nome
        }
    }

    val corAcento = if (tipo == TipoTransacao.GANHO) GreenPrimary else RedExpense

    ModalBottomSheet(
        onDismissRequest = onFechar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            // ---------- Header ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (edicao) "EDITAR TRANSAÇÃO" else "NOVA TRANSAÇÃO",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onFechar) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Fechar")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ---------- Toggle Ganho / Gasto ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TipoTransacao.entries.forEach { opcao ->
                    val selecionado = tipo == opcao
                    val corOpcao =
                        if (opcao == TipoTransacao.GANHO) GreenPrimary else RedExpense
                    Button(
                        onClick = { tipo = opcao },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selecionado) corOpcao
                            else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (selecionado) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(if (opcao == TipoTransacao.GANHO) "Ganho" else "Gasto")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---------- Valor (máscara de centavos: digite 1234 -> R$ 12,34) ----------
            val exibicao = if (digitos.isEmpty()) ""
            else Formatadores.moeda(digitos.toLong())
            OutlinedTextField(
                value = TextFieldValue(exibicao, selection = TextRange(exibicao.length)),
                onValueChange = { novo ->
                    digitos = novo.text.filter(Char::isDigit).trimStart('0').take(10)
                    erroValor = null
                },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 24.sp,
                    color = corAcento
                ),
                placeholder = {
                    Text(text = "R$ 0,00", fontSize = 24.sp)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = erroValor != null,
                supportingText = { erroValor?.let { Text(it) } }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ---------- Categoria (dropdown com busca) ----------
            ExposedDropdownMenuBox(
                expanded = dropdownAberto,
                onExpandedChange = { dropdownAberto = it }
            ) {
                OutlinedTextField(
                    value = textoCategoria,
                    onValueChange = {
                        textoCategoria = it
                        erroCategoria = null
                        dropdownAberto = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryEditable),
                    label = { Text("Categoria") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownAberto)
                    },
                    singleLine = true,
                    isError = erroCategoria != null,
                    supportingText = { erroCategoria?.let { Text(it) } }
                )
                val filtradas = categorias.filter {
                    textoCategoria.isBlank() ||
                        it.nome.contains(textoCategoria, ignoreCase = true) ||
                        categorias.any { c -> c.nome.equals(textoCategoria, true) }
                }
                ExposedDropdownMenu(
                    expanded = dropdownAberto && filtradas.isNotEmpty(),
                    onDismissRequest = { dropdownAberto = false }
                ) {
                    filtradas.forEach { categoria ->
                        DropdownMenuItem(
                            text = { Text(categoria.nome) },
                            onClick = {
                                textoCategoria = categoria.nome
                                erroCategoria = null
                                dropdownAberto = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---------- Descrição (opcional, máx. 100) ----------
            OutlinedTextField(
                value = descricao,
                onValueChange = { if (it.length <= 100) descricao = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Descrição (opcional)") },
                placeholder = { Text("Ex: Salário, Almoço...") },
                supportingText = { Text("${descricao.length}/100") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ---------- Data ----------
            OutlinedTextField(
                value = Formatadores.dataCurta(data),
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Data") },
                readOnly = true,
                trailingIcon = {
                    TextButton(onClick = { datePickerAberto = true }) {
                        Text("Alterar")
                    }
                }
            )

            // ---------- Repetir mensalmente (só para nova transação) ----------
            if (!edicao) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = repetirMensalmente,
                        onCheckedChange = { repetirMensalmente = it }
                    )
                    Text(
                        text = "Repetir todo mês (a partir do mês seguinte)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---------- Ações ----------
            if (edicao) {
                OutlinedButton(
                    onClick = {
                        val transacao = transacaoParaEditar!!
                        onDeletar?.invoke(transacao) ?: viewModel.deletarTransacao(transacao)
                        onFechar()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedExpense)
                ) {
                    Text("DELETAR")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    val valorCentavos = digitos.toLongOrNull() ?: 0L
                    val nomeDigitado = textoCategoria.trim()
                    // Categoria válida: uma ativa da lista OU, em edição,
                    // a categoria original (mesmo arquivada)
                    val categoriaFinal = categorias
                        .firstOrNull { it.nome.equals(nomeDigitado, ignoreCase = true) }
                        ?.nome
                        ?: categoriaOriginal?.takeIf {
                            edicao && it.equals(nomeDigitado, ignoreCase = true)
                        }
                    when {
                        valorCentavos <= 0L -> erroValor = "Informe um valor maior que zero"
                        categoriaFinal == null -> erroCategoria = "Escolha uma categoria da lista"
                        else -> {
                            if (edicao) {
                                viewModel.editarTransacao(
                                    transacaoParaEditar!!.copy(
                                        valor = valorCentavos,
                                        tipo = tipo,
                                        categoria = categoriaFinal,
                                        descricao = descricao.trim(),
                                        data = data
                                    )
                                )
                            } else {
                                viewModel.adicionarTransacao(
                                    valorCentavos = valorCentavos,
                                    tipo = tipo,
                                    categoria = categoriaFinal,
                                    descricao = descricao,
                                    data = data,
                                    repetirMensalmente = repetirMensalmente
                                )
                            }
                            onFechar()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = corAcento,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(if (edicao) "ATUALIZAR" else "SALVAR")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ---------- DatePicker ----------
    if (datePickerAberto) {
        val estadoData = rememberDatePickerState(
            initialSelectedDateMillis = data.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { datePickerAberto = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        estadoData.selectedDateMillis?.let { millis ->
                            data = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                        }
                        datePickerAberto = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { datePickerAberto = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = estadoData)
        }
    }
}
