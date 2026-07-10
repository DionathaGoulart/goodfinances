package com.finapp.ui.component

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.finapp.data.db.entities.Cartao
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.Transacao
import com.finapp.ui.theme.GreenPrimary
import com.finapp.ui.theme.RedExpense
import com.finapp.utils.Formatadores
import com.finapp.viewmodel.TransacaoViewModel
import kotlinx.coroutines.launch
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
    tipoInicial: TipoTransacao? = null,
    viewModel: TransacaoViewModel = hiltViewModel()
) {
    val edicao = transacaoParaEditar != null
    val categoriaOriginal = transacaoParaEditar?.categoria

    var tipo by remember {
        mutableStateOf(transacaoParaEditar?.tipo ?: tipoInicial ?: TipoTransacao.GANHO)
    }
    // Valor em centavos, como texto (máscara de moeda BR)
    var digitos by remember {
        mutableStateOf(transacaoParaEditar?.valor?.toString() ?: "")
    }
    var textoCategoria by remember { mutableStateOf(categoriaOriginal ?: "Outros") }
    var descricao by remember { mutableStateOf(transacaoParaEditar?.descricao ?: "") }
    var data by remember { mutableStateOf(transacaoParaEditar?.data ?: LocalDate.now()) }
    var repetirMensalmente by remember { mutableStateOf(false) }
    var parcelas by remember { mutableStateOf(1) }
    var proLabore by remember { mutableStateOf(false) }
    // Forma de pagamento (só gasto novo): dinheiro/débito ou crédito num cartão
    var pagamentoCredito by remember { mutableStateOf(false) }
    var cartaoSelecionado by remember { mutableStateOf<Cartao?>(null) }
    val cartoes by viewModel.cartoes.collectAsStateWithLifecycle()

    var erroValor by remember { mutableStateOf<String?>(null) }
    var erroCategoria by remember { mutableStateOf<String?>(null) }
    var dropdownAberto by remember { mutableStateOf(false) }
    var datePickerAberto by remember { mutableStateOf(false) }

    // ---------- Nota fiscal / comprovante (todos os contextos) ----------
    val perfilDados by viewModel.perfil.collectAsStateWithLifecycle()
    val notaOriginal = transacaoParaEditar?.notaFiscal ?: ""
    var notaFiscal by remember { mutableStateOf(notaOriginal) }
    var fotoPendente by remember { mutableStateOf<Pair<String, Uri>?>(null) }
    val escopo = rememberCoroutineScope()

    // Troca o anexo, apagando o arquivo anterior se ele foi criado agora
    fun definirNota(nome: String) {
        if (notaFiscal.isNotBlank() && notaFiscal != notaOriginal) {
            viewModel.apagarNota(notaFiscal)
        }
        notaFiscal = nome
    }

    // Fechar sem salvar descarta o anexo recém-criado (evita arquivo órfão)
    fun fecharDescartando() {
        if (notaFiscal.isNotBlank() && notaFiscal != notaOriginal) {
            viewModel.apagarNota(notaFiscal)
        }
        onFechar()
    }

    val anexarGaleria = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            escopo.launch {
                runCatching { viewModel.anexarNota(it) }.onSuccess(::definirNota)
            }
        }
    }
    val anexarArquivo = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            escopo.launch {
                runCatching { viewModel.anexarNota(it) }.onSuccess(::definirNota)
            }
        }
    }
    val tirarFoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { sucesso ->
        fotoPendente?.let { (nome, _) ->
            if (sucesso) {
                // Foto vira PDF; se a conversão falhar, fica a imagem mesmo
                escopo.launch {
                    val nomeFinal = runCatching { viewModel.converterFotoParaPdf(nome) }
                        .getOrDefault(nome)
                    definirNota(nomeFinal)
                }
            } else {
                viewModel.apagarNota(nome)
            }
        }
        fotoPendente = null
    }

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
        onDismissRequest = ::fecharDescartando,
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
                IconButton(onClick = ::fecharDescartando) {
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

            // ---------- Categoria (seleção apenas — sem digitação) ----------
            ExposedDropdownMenuBox(
                expanded = dropdownAberto,
                onExpandedChange = { dropdownAberto = it }
            ) {
                OutlinedTextField(
                    value = textoCategoria,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    label = { Text("Categoria") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownAberto)
                    },
                    singleLine = true,
                    isError = erroCategoria != null,
                    supportingText = { erroCategoria?.let { Text(it) } }
                )
                ExposedDropdownMenu(
                    expanded = dropdownAberto && categorias.isNotEmpty(),
                    onDismissRequest = { dropdownAberto = false }
                ) {
                    categorias.forEach { categoria ->
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

            // ---------- Data (campo inteiro clicável — abre o calendário) ----------
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = Formatadores.dataCurta(data),
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Data") },
                    readOnly = true,
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = "Escolher data"
                        )
                    }
                )
                // Overlay transparente: readOnly não repassa toques ao campo
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { datePickerAberto = true }
                )
            }

            // ---------- Nota fiscal / comprovante (todos os contextos) ----------
            run {
                Spacer(modifier = Modifier.height(12.dp))
                if (notaFiscal.isBlank()) {
                    Text(
                        text = "Nota fiscal ou comprovante (opcional)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val destino = viewModel.criarDestinoFoto()
                                fotoPendente = destino
                                tirarFoto.launch(destino.second)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Câmera")
                        }
                        OutlinedButton(
                            onClick = {
                                anexarGaleria.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Galeria")
                        }
                        OutlinedButton(
                            onClick = {
                                anexarArquivo.launch(arrayOf("image/*", "application/pdf"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("PDF")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = " Nota fiscal anexada",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.abrirNota(notaFiscal) }) {
                            Text("Ver")
                        }
                        TextButton(onClick = { definirNota("") }) {
                            Text("Remover")
                        }
                    }
                }
            }

            // ---------- Forma de pagamento (só gasto novo) ----------
            if (!edicao && tipo == TipoTransacao.GASTO) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Forma de pagamento",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            pagamentoCredito = false
                            cartaoSelecionado = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!pagamentoCredito) corAcento
                            else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (!pagamentoCredito) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    ) {
                        Text("Dinheiro/Débito")
                    }
                    Button(
                        onClick = {
                            pagamentoCredito = true
                            if (cartaoSelecionado == null) {
                                cartaoSelecionado = cartoes.firstOrNull()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (pagamentoCredito) corAcento
                            else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (pagamentoCredito) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    ) {
                        Text("Crédito")
                    }
                }

                if (pagamentoCredito) {
                    if (cartoes.isEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Nenhum cartão cadastrado. Adicione um em " +
                                "Configurações › Finanças › Cartões.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        var menuCartaoAberto by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { menuCartaoAberto = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(cartaoSelecionado?.nome ?: "Escolher cartão")
                            }
                            DropdownMenu(
                                expanded = menuCartaoAberto,
                                onDismissRequest = { menuCartaoAberto = false }
                            ) {
                                cartoes.forEach { cartao ->
                                    DropdownMenuItem(
                                        text = { Text(cartao.nome) },
                                        onClick = {
                                            cartaoSelecionado = cartao
                                            menuCartaoAberto = false
                                        }
                                    )
                                }
                            }
                        }
                        cartaoSelecionado?.let { cartao ->
                            val vencimento = viewModel.previsaoVencimento(cartao, data)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (parcelas > 1) {
                                    "1ª parcela na fatura que vence " +
                                        Formatadores.dataCurta(vencimento)
                                } else {
                                    "Cai na fatura que vence " +
                                        Formatadores.dataCurta(vencimento)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ---------- Parcelamento (só gasto novo) ----------
            if (!edicao && tipo == TipoTransacao.GASTO) {
                Spacer(modifier = Modifier.height(8.dp))
                var menuParcelasAberto by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Parcelamento",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        OutlinedButton(onClick = { menuParcelasAberto = true }) {
                            Text(if (parcelas == 1) "À vista" else "${parcelas}x")
                        }
                        DropdownMenu(
                            expanded = menuParcelasAberto,
                            onDismissRequest = { menuParcelasAberto = false }
                        ) {
                            (1..12).forEach { n ->
                                DropdownMenuItem(
                                    text = { Text(if (n == 1) "À vista" else "${n}x") },
                                    onClick = {
                                        parcelas = n
                                        menuParcelasAberto = false
                                    }
                                )
                            }
                        }
                    }
                }
                if (parcelas > 1) {
                    val totalDigitado = digitos.toLongOrNull() ?: 0L
                    val valorParcela = totalDigitado / parcelas
                    Text(
                        text = if (totalDigitado > 0L) {
                            "Total de ${Formatadores.moeda(totalDigitado)} dividido em " +
                                "${parcelas}x de ${Formatadores.moeda(valorParcela)} " +
                                "(um lançamento por mês)."
                        } else {
                            "Digite o valor TOTAL da compra — ele será dividido " +
                                "em $parcelas lançamentos mensais."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---------- Pró-labore (modo misto, aba Empresa, gasto novo) ----------
            if (!edicao && perfilDados == Perfil.MEI_NEGOCIO &&
                tipo == TipoTransacao.GASTO && parcelas == 1
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = proLabore,
                        onCheckedChange = { proLabore = it }
                    )
                    Text(
                        text = "Pró-labore: lançar também como ganho no Pessoal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---------- Repetir mensalmente (só para nova transação à vista) ----------
            if (!edicao && parcelas == 1 && !pagamentoCredito) {
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
                        fecharDescartando()
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
                    val usaCredito = !edicao && tipo == TipoTransacao.GASTO && pagamentoCredito
                    when {
                        valorCentavos <= 0L -> erroValor = "Informe um valor maior que zero"
                        categoriaFinal == null -> erroCategoria = "Escolha uma categoria da lista"
                        usaCredito && cartaoSelecionado == null ->
                            erroValor = "Cadastre um cartão para usar crédito"
                        else -> {
                            if (edicao) {
                                viewModel.editarTransacao(
                                    transacaoParaEditar!!.copy(
                                        valor = valorCentavos,
                                        tipo = tipo,
                                        categoria = categoriaFinal,
                                        descricao = descricao.trim(),
                                        data = data,
                                        notaFiscal = notaFiscal
                                    )
                                )
                                // Nota substituída/removida: apaga o arquivo antigo
                                if (notaOriginal.isNotBlank() && notaOriginal != notaFiscal) {
                                    viewModel.apagarNota(notaOriginal)
                                }
                            } else {
                                viewModel.adicionarTransacao(
                                    valorCentavos = valorCentavos,
                                    tipo = tipo,
                                    categoria = categoriaFinal,
                                    descricao = descricao,
                                    data = data,
                                    repetirMensalmente = repetirMensalmente,
                                    notaFiscal = notaFiscal,
                                    parcelas = parcelas,
                                    lancarProLaborePessoal = proLabore,
                                    cartao = if (usaCredito) cartaoSelecionado else null
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
