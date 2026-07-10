package com.finapp.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.biometric.BiometricManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finapp.BuildConfig
import com.finapp.data.db.entities.Cartao
import com.finapp.data.db.entities.Categoria
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoEmpresa
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.ui.theme.RedExpense
import com.finapp.utils.CorApp
import com.finapp.utils.CoresCategorias
import com.finapp.utils.EscalaFonte
import com.finapp.utils.Formatadores
import com.finapp.viewmodel.CasaViewModel
import com.finapp.viewmodel.ConfigViewModel
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Configurações organizadas em seções recolhíveis:
 * Modo de uso · Finanças · Casa e sincronização · Notificações ·
 * Dados e backup · Segurança · Aparência · Sobre.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfigScreen(
    viewModel: ConfigViewModel = hiltViewModel(),
    casaViewModel: CasaViewModel = hiltViewModel()
) {
    val perfil by viewModel.perfil.collectAsStateWithLifecycle()
    val usuario by casaViewModel.usuario.collectAsStateWithLifecycle()
    val casa by casaViewModel.casa.collectAsStateWithLifecycle()
    val casaOcupado by casaViewModel.ocupado.collectAsStateWithLifecycle()
    val configuracao by viewModel.configuracao.collectAsStateWithLifecycle()
    val categorias by viewModel.categorias.collectAsStateWithLifecycle()
    val escalaFonte by viewModel.escalaFonte.collectAsStateWithLifecycle()
    val corPessoal by viewModel.corPessoal.collectAsStateWithLifecycle()
    val corEmpresa by viewModel.corEmpresa.collectAsStateWithLifecycle()
    val contexto = LocalContext.current

    val perfilEmpresa by viewModel.perfilEmpresa.collectAsStateWithLifecycle()
    val dasMensal by viewModel.dasMensal.collectAsStateWithLifecycle()
    val cartoes by viewModel.cartoes.collectAsStateWithLifecycle()

    var dialogSalarioAberto by remember { mutableStateOf(false) }
    var dialogDasAberto by remember { mutableStateOf(false) }
    var dialogCartaoAberto by remember { mutableStateOf(false) }
    var cartaoEmEdicao by remember { mutableStateOf<Cartao?>(null) }
    var dialogCategoriaAberto by remember { mutableStateOf(false) }
    var categoriaEmEdicao by remember { mutableStateOf<Categoria?>(null) }
    var confirmarLimpezaAberto by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.mensagens.collect {
            Toast.makeText(contexto, it, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) {
        casaViewModel.mensagens.collect {
            Toast.makeText(contexto, it, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "CONFIGURAÇÕES",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ---------- Modo de uso ----------
        SecaoConfig(
            titulo = "Modo de uso",
            subtitulo = perfil.rotulo
        ) {
            Perfil.PRINCIPAIS.forEach { opcao ->
                val selecionado = perfil == opcao
                Card(
                    onClick = { if (!selecionado) viewModel.mudarPerfil(opcao) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = if (selecionado) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = opcao.rotulo,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = opcao.descricao,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selecionado) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Perfil ativo",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ao trocar de modo, os dados dos demais continuam salvos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Tipo da empresa (só nos modos que têm empresa)
            if (perfil == Perfil.MEI || perfil == Perfil.CNPJ) {
                val tipoEmpresa by viewModel.tipoEmpresa.collectAsStateWithLifecycle()
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Minha empresa é:",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TipoEmpresa.entries.forEach { tipo ->
                        FilterChip(
                            selected = tipoEmpresa == tipo,
                            onClick = { viewModel.definirTipoEmpresa(tipo) },
                            label = { Text(tipo.rotulo) },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }

        // ---------- Finanças ----------
        SecaoConfig(
            titulo = "Finanças",
            subtitulo = if (perfilEmpresa != null) {
                "Salário, DAS mensal, cartões, categorias e recorrências"
            } else {
                "Salário, cartões, categorias e recorrências"
            }
        ) {
            // Salário fixo
            LinhaConfig(
                titulo = "Salário Fixo",
                subtitulo = "${Formatadores.moeda(configuracao.salarioFixo)} · " +
                    "recebe dia ${configuracao.diaRecebimento}",
                acao = "Editar",
                onAcao = { dialogSalarioAberto = true }
            )

            // DAS mensal (modos MEI/CNPJ): despesa fixa da empresa todo dia 20.
            // Visível em qualquer aba — grava no balde da empresa.
            if (perfilEmpresa != null) {
                DivisorConfig()
                LinhaConfig(
                    titulo = "DAS mensal",
                    subtitulo = if (dasMensal > 0L) {
                        "${Formatadores.moeda(dasMensal)} · despesa da empresa todo dia 20"
                    } else {
                        "Lança o imposto do MEI como despesa da empresa todo mês"
                    },
                    acao = "Editar",
                    onAcao = { dialogDasAberto = true }
                )
            }

            DivisorConfig()

            // Cartões de crédito
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cartões de crédito",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    cartaoEmEdicao = null
                    dialogCartaoAberto = true
                }) {
                    Text("+ Novo")
                }
            }
            if (cartoes.isEmpty()) {
                Text(
                    text = "Nenhum cartão. Cadastre para poder lançar compras no crédito " +
                        "(o gasto cai no mês da fatura).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                cartoes.forEach { cartao ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                cartaoEmEdicao = cartao
                                dialogCartaoAberto = true
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    runCatching { Color(cartao.cor.toColorInt()) }
                                        .getOrDefault(Color.Gray)
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cartao.nome,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "fecha dia ${cartao.diaFechamento} · " +
                                    "vence dia ${cartao.diaVencimento}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { viewModel.removerCartao(cartao) }) {
                            Text("Remover")
                        }
                    }
                }
            }

            DivisorConfig()

            // Categorias
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Categorias",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { dialogCategoriaAberto = true }) {
                    Text("+ Nova")
                }
            }

            categorias.forEach { categoria ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { categoriaEmEdicao = categoria }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                runCatching { Color(categoria.cor.toColorInt()) }
                                    .getOrDefault(Color.Gray)
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = categoria.nome,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (categoria.arquivada) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Text(
                            text = (if (categoria.tipo == TipoTransacao.GANHO) {
                                "Ganho"
                            } else {
                                "Gasto"
                            }) + if (categoria.arquivada) " · arquivada" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = {
                            if (categoria.arquivada) viewModel.reativarCategoria(categoria)
                            else viewModel.arquivarCategoria(categoria)
                        }
                    ) {
                        Text(if (categoria.arquivada) "Reativar" else "Arquivar")
                    }
                }
            }

            DivisorConfig()

            // Recorrentes
            val recorrentes by viewModel.recorrentes.collectAsStateWithLifecycle()
            Text(
                text = "Recorrentes",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (recorrentes.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Nenhuma recorrência ativa. Marque \"Repetir todo mês\" " +
                        "ao criar uma transação, ou configure o salário fixo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                recorrentes.forEach { recorrente ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = recorrente.descricao.ifBlank { recorrente.categoria },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${Formatadores.moeda(recorrente.valor)} · " +
                                    "próx. ${Formatadores.dataCurta(recorrente.proximoLancamento)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { viewModel.encerrarRecorrente(recorrente) }) {
                            Text("Encerrar")
                        }
                    }
                }
            }
        }

        // ---------- Casa compartilhada e sincronização ----------
        SecaoConfig(
            titulo = "Casa e sincronização",
            subtitulo = when {
                casa != null -> "Na Casa ${casa?.codigoConvite.orEmpty()} · " +
                    (usuario?.email ?: "")
                usuario != null -> "Conectado como ${usuario?.email.orEmpty()}"
                else -> "Carteira compartilhada e sync entre aparelhos"
            }
        ) {
            val usuarioAtual = usuario
            val casaAtual = casa

            when {
                // Não logado
                usuarioAtual == null -> {
                    Text(
                        text = "Divida uma carteira com quem mora com você. " +
                            "Entre com sua conta Google para criar ou entrar numa Casa.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { casaViewModel.entrarComGoogle(contexto) },
                        enabled = !casaOcupado,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (casaOcupado) "Conectando..." else "Entrar com Google")
                    }
                }

                // Logado, sem casa
                casaAtual == null -> {
                    Text(
                        text = "Conectado como ${usuarioAtual.email}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = casaViewModel::criarCasa,
                        enabled = !casaOcupado,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Criar uma Casa")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    var codigoDigitado by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = codigoDigitado,
                        onValueChange = {
                            codigoDigitado = it.uppercase(Locale.ROOT).take(6)
                        },
                        label = { Text("Código de convite") },
                        placeholder = { Text("Ex: A3F7KP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { casaViewModel.entrarNaCasa(codigoDigitado) },
                        enabled = !casaOcupado,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Entrar com código")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = casaViewModel::sairDaConta) {
                        Text("Sair da conta Google")
                    }
                }

                // Logado e com casa
                else -> {
                    Text(
                        text = "Código de convite",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = casaAtual.codigoConvite,
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${casaAtual.membros.size} membro(s) · " +
                            "conectado como ${usuarioAtual.email}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Entre na minha Casa no GoodFinances com o código: " +
                                        casaAtual.codigoConvite
                                )
                            }
                            contexto.startActivity(
                                Intent.createChooser(intent, "Compartilhar código")
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Compartilhar código")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "A Casa aparece como aba na tela inicial.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Visão Membros: cada um decide expor o próprio pessoal
                    val compartilhar by viewModel.compartilharCasaAtivado
                        .collectAsStateWithLifecycle()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Compartilhar meus lançamentos pessoais",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Os membros veem seus ganhos/gastos pessoais " +
                                    "na visão Membros. A empresa fica de fora.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = compartilhar,
                            onCheckedChange = viewModel::alternarCompartilharCasa
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row {
                        TextButton(
                            onClick = casaViewModel::sairDaCasa,
                            enabled = !casaOcupado
                        ) {
                            Text("Sair da Casa")
                        }
                        TextButton(onClick = casaViewModel::sairDaConta) {
                            Text("Sair da conta")
                        }
                    }
                }
            }

            // Sincronização entre aparelhos (precisa estar logado)
            if (usuario != null) {
                DivisorConfig()
                val syncPessoalAtivado by viewModel.syncPessoalAtivado
                    .collectAsStateWithLifecycle()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sincronizar entre aparelhos",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Transações e categorias de todos os modos, na sua " +
                                "conta Google. Notas fiscais ficam só neste aparelho.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = syncPessoalAtivado,
                        onCheckedChange = viewModel::alternarSyncPessoal
                    )
                }
            }
        }

        // ---------- Notificações ----------
        SecaoConfig(
            titulo = "Notificações",
            subtitulo = "Avisos de orçamento, DAS e recorrências"
        ) {
            val notificacoesAtivadas by viewModel.notificacoesAtivadas
                .collectAsStateWithLifecycle()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Avisos financeiros",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Orçamento estourando, DAS vencendo, recorrências do dia " +
                            "e lembrete de registro",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = notificacoesAtivadas,
                    onCheckedChange = viewModel::alternarNotificacoes
                )
            }
        }

        // ---------- Dados e backup ----------
        SecaoConfig(
            titulo = "Dados e backup",
            subtitulo = "Exportar, importar e backups automáticos"
        ) {
            val hoje = LocalDate.now()
            val exportCsv = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("text/csv")
            ) { viewModel.exportarCsv(it) }
            val exportJson = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json")
            ) { viewModel.exportarJson(it) }
            val exportPdf = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/pdf")
            ) { viewModel.exportarPdf(it) }
            val importar = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { viewModel.prepararImportacao(it) }
            val exportZip = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/zip")
            ) { viewModel.exportarZip(it) }

            ItemDados(
                icone = Icons.Filled.FileDownload,
                titulo = "Exportar para CSV",
                onClick = { exportCsv.launch("GoodFinances_$hoje.csv") }
            )
            ItemDados(
                icone = Icons.Filled.FileDownload,
                titulo = "Exportar para JSON",
                onClick = { exportJson.launch("GoodFinances_$hoje.json") }
            )
            ItemDados(
                icone = Icons.Filled.FileDownload,
                titulo = "Exportar Relatório PDF",
                onClick = { exportPdf.launch("GoodFinances_Relatorio_$hoje.pdf") }
            )
            ItemDados(
                icone = Icons.Filled.FileUpload,
                titulo = "Importar Dados (CSV ou JSON)",
                onClick = { importar.launch(arrayOf("*/*")) }
            )
            ItemDados(
                icone = Icons.AutoMirrored.Filled.ReceiptLong,
                titulo = "Exportar Notas Fiscais (ZIP)",
                subtitulo = "Tudo organizado por ano e mês — pronto para o imposto",
                onClick = { exportZip.launch("GoodFinances_Notas_$hoje.zip") }
            )

            DivisorConfig()

            // Backup automático
            val backupAtivado by viewModel.backupAtivado.collectAsStateWithLifecycle()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Backup Automático",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "JSON semanal · local e na nuvem (quando conectado)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = backupAtivado,
                    onCheckedChange = viewModel::alternarBackupAutomatico
                )
            }
            ItemDados(
                icone = Icons.Filled.Restore,
                titulo = "Restaurar do Backup",
                onClick = viewModel::restaurarBackup
            )

            DivisorConfig()

            // Backup das notas fiscais no Google Drive (conta logada)
            val backupDriveAtivado by viewModel.backupDriveAtivado
                .collectAsStateWithLifecycle()
            val autorizarDrive = rememberLauncherForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult()
            ) { resultado ->
                if (resultado.resultCode == android.app.Activity.RESULT_OK) {
                    viewModel.concluirAtivacaoDrive()
                }
            }
            LaunchedEffect(Unit) {
                viewModel.pedidoAutorizacaoDrive.collect { pendente ->
                    autorizarDrive.launch(
                        IntentSenderRequest.Builder(pendente.intentSender).build()
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Notas fiscais no Google Drive",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Guarda os arquivos das notas na sua conta Google (grátis)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = backupDriveAtivado,
                    onCheckedChange = viewModel::alternarBackupDrive
                )
            }
            if (backupDriveAtivado) {
                ItemDados(
                    icone = Icons.Filled.Restore,
                    titulo = "Restaurar Notas do Drive",
                    subtitulo = "Baixa as notas que não estão neste aparelho",
                    onClick = viewModel::restaurarNotasDrive
                )
            }
        }

        // ---------- Segurança ----------
        SecaoConfig(
            titulo = "Segurança",
            subtitulo = "Bloqueio por biometria"
        ) {
            val bloqueioAtivado by viewModel.bloqueioAtivado.collectAsStateWithLifecycle()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Bloqueio por biometria",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Pede digital ou PIN do aparelho ao abrir o app",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = bloqueioAtivado,
                    onCheckedChange = { ativo ->
                        val disponivel = BiometricManager.from(contexto).canAuthenticate(
                            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        ) == BiometricManager.BIOMETRIC_SUCCESS
                        if (ativo && !disponivel) {
                            Toast.makeText(
                                contexto,
                                "Configure biometria ou bloqueio de tela no Android antes",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            viewModel.alternarBloqueio(ativo)
                        }
                    }
                )
            }
        }

        // ---------- Aparência ----------
        SecaoConfig(
            titulo = "Aparência",
            subtitulo = "Tamanho da fonte e cores do tema"
        ) {
            Text(
                text = "Tema: Escuro (padrão do app)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Tamanho da Fonte",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EscalaFonte.entries.forEach { escala ->
                    FilterChip(
                        selected = escalaFonte == escala,
                        onClick = { viewModel.definirEscalaFonte(escala) },
                        label = { Text(escala.rotulo) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            SeletorCorTema(
                titulo = "Cor do tema pessoal",
                corSelecionada = corPessoal,
                onSelecionar = viewModel::definirCorPessoal
            )

            Spacer(modifier = Modifier.height(12.dp))

            SeletorCorTema(
                titulo = "Cor do tema empresa",
                corSelecionada = corEmpresa,
                onSelecionar = viewModel::definirCorEmpresa
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "O app troca de cor sozinho ao alternar entre pessoal e empresa.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---------- Sobre ----------
        SecaoConfig(
            titulo = "Sobre",
            subtitulo = "Versão ${BuildConfig.VERSION_NAME}"
        ) {
            Text(
                text = "GoodFinances — Versão ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Contato: contato@dionatha.com.br",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ---------- Zona de perigo ----------
        Button(
            onClick = { confirmarLimpezaAberto = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = RedExpense,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Text("LIMPAR TODOS OS DADOS")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // ---------- Dialog: editar salário ----------
    if (dialogSalarioAberto) {
        var salarioTexto by remember {
            mutableStateOf(
                if (configuracao.salarioFixo > 0L) {
                    String.format(Locale.US, "%.2f", configuracao.salarioFixo / 100.0)
                } else {
                    ""
                }
            )
        }
        var diaTexto by remember { mutableStateOf(configuracao.diaRecebimento.toString()) }

        AlertDialog(
            onDismissRequest = { dialogSalarioAberto = false },
            title = { Text("Salário Fixo") },
            text = {
                Column {
                    OutlinedTextField(
                        value = salarioTexto,
                        onValueChange = { salarioTexto = it },
                        label = { Text("Valor (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = diaTexto,
                        onValueChange = { diaTexto = it.filter(Char::isDigit).take(2) },
                        label = { Text("Dia de recebimento (1 a 28)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.atualizarSalario(
                            reaisParaCentavos(salarioTexto),
                            diaTexto.toIntOrNull() ?: 0
                        )
                        dialogSalarioAberto = false
                    }
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogSalarioAberto = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // ---------- Dialog: editar DAS mensal ----------
    if (dialogDasAberto) {
        var dasTexto by remember {
            mutableStateOf(
                if (dasMensal > 0L) {
                    String.format(Locale.US, "%.2f", dasMensal / 100.0)
                } else {
                    ""
                }
            )
        }
        AlertDialog(
            onDismissRequest = { dialogDasAberto = false },
            title = { Text("DAS mensal") },
            text = {
                Column {
                    OutlinedTextField(
                        value = dasTexto,
                        onValueChange = { dasTexto = it },
                        label = { Text("Valor (R$)") },
                        placeholder = { Text("Vazio = sem DAS") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Lançado automaticamente como despesa da empresa " +
                            "(categoria Impostos) todo dia 20.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.atualizarDas(reaisParaCentavos(dasTexto))
                        dialogDasAberto = false
                    }
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogDasAberto = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // ---------- Dialog: novo/editar cartão ----------
    if (dialogCartaoAberto) {
        val emEdicao = cartaoEmEdicao
        var nome by remember(emEdicao) { mutableStateOf(emEdicao?.nome ?: "") }
        var fechamento by remember(emEdicao) {
            mutableStateOf(emEdicao?.diaFechamento?.toString() ?: "")
        }
        var vencimento by remember(emEdicao) {
            mutableStateOf(emEdicao?.diaVencimento?.toString() ?: "")
        }
        var cor by remember(emEdicao) {
            mutableStateOf(emEdicao?.cor ?: CoresCategorias.TODAS.first())
        }
        AlertDialog(
            onDismissRequest = { dialogCartaoAberto = false },
            title = { Text(if (emEdicao == null) "Novo cartão" else "Editar cartão") },
            text = {
                Column {
                    OutlinedTextField(
                        value = nome,
                        onValueChange = { if (it.length <= 30) nome = it },
                        label = { Text("Nome (ex: Nubank)") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = fechamento,
                            onValueChange = { fechamento = it.filter(Char::isDigit).take(2) },
                            label = { Text("Fecha dia") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = vencimento,
                            onValueChange = { vencimento = it.filter(Char::isDigit).take(2) },
                            label = { Text("Vence dia") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Dias entre 1 e 28. Compra após o fechamento entra na " +
                            "próxima fatura.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SeletorCores(corSelecionada = cor, onSelecionar = { cor = it })
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val f = fechamento.toIntOrNull() ?: 0
                        val v = vencimento.toIntOrNull() ?: 0
                        if (emEdicao == null) {
                            viewModel.adicionarCartao(nome, f, v, cor)
                        } else {
                            viewModel.editarCartao(emEdicao, nome, f, v, cor)
                        }
                        dialogCartaoAberto = false
                    }
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogCartaoAberto = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // ---------- Dialog: nova categoria ----------
    if (dialogCategoriaAberto) {
        var nome by remember { mutableStateOf("") }
        var tipo by remember { mutableStateOf(TipoTransacao.GASTO) }
        var cor by remember { mutableStateOf(CoresCategorias.TODAS.first()) }

        AlertDialog(
            onDismissRequest = { dialogCategoriaAberto = false },
            title = { Text("Nova Categoria") },
            text = {
                Column {
                    OutlinedTextField(
                        value = nome,
                        onValueChange = { if (it.length <= 30) nome = it },
                        label = { Text("Nome") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = tipo == TipoTransacao.GASTO,
                            onClick = { tipo = TipoTransacao.GASTO },
                            label = { Text("Gasto") }
                        )
                        FilterChip(
                            selected = tipo == TipoTransacao.GANHO,
                            onClick = { tipo = TipoTransacao.GANHO },
                            label = { Text("Ganho") }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SeletorCores(corSelecionada = cor, onSelecionar = { cor = it })
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.adicionarCategoria(nome, tipo, cor)
                        dialogCategoriaAberto = false
                    }
                ) {
                    Text("Adicionar")
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogCategoriaAberto = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // ---------- Dialog: editar categoria (renomear / recolorir / orçamento) ----------
    categoriaEmEdicao?.let { categoria ->
        var nome by remember(categoria.id) { mutableStateOf(categoria.nome) }
        var cor by remember(categoria.id) { mutableStateOf(categoria.cor) }
        var orcamentoTexto by remember(categoria.id) {
            mutableStateOf(
                if (categoria.orcamentoMensal > 0L) {
                    String.format(Locale.US, "%.2f", categoria.orcamentoMensal / 100.0)
                } else {
                    ""
                }
            )
        }

        AlertDialog(
            onDismissRequest = { categoriaEmEdicao = null },
            title = { Text("Editar Categoria") },
            text = {
                Column {
                    OutlinedTextField(
                        value = nome,
                        onValueChange = { if (it.length <= 30) nome = it },
                        label = { Text("Nome") },
                        singleLine = true
                    )
                    if (categoria.tipo == TipoTransacao.GASTO) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = orcamentoTexto,
                            onValueChange = { orcamentoTexto = it },
                            label = { Text("Orçamento mensal (R$)") },
                            placeholder = { Text("Vazio = sem orçamento") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            ),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SeletorCores(corSelecionada = cor, onSelecionar = { cor = it })
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Renomear atualiza também o histórico de transações. " +
                            "Orçamentos aparecem na aba Análise.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.editarCategoria(
                            categoria,
                            nome,
                            cor,
                            novoOrcamentoCentavos = reaisParaCentavos(orcamentoTexto)
                        )
                        categoriaEmEdicao = null
                    }
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoriaEmEdicao = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // ---------- Dialog: confirmar limpeza ----------
    if (confirmarLimpezaAberto) {
        AlertDialog(
            onDismissRequest = { confirmarLimpezaAberto = false },
            title = { Text("Limpar todos os dados?") },
            text = {
                Column {
                    Text(
                        "Esta ação é irreversível. O que você quer apagar?\n\n" +
                            "• Só o contexto atual: apaga as transações da aba " +
                            "em que você está (se for a Casa, some para todos " +
                            "os membros).\n" +
                            "• Tudo deste aparelho: apaga Pessoal e Empresa " +
                            "de uma vez — inclusive contextos de modos que " +
                            "você usou antes. A carteira da Casa fica de fora."
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.limparTodosDados(todosContextos = false)
                            confirmarLimpezaAberto = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RedExpense)
                    ) {
                        Text("Apagar só o contexto atual")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.limparTodosDados(todosContextos = true)
                            confirmarLimpezaAberto = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RedExpense)
                    ) {
                        Text("Apagar tudo deste aparelho")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { confirmarLimpezaAberto = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // ---------- Dialog de importação (prévia + Mesclar/Substituir) ----------
    val previa by viewModel.previaImportacao.collectAsStateWithLifecycle()
    previa?.let { dados ->
        AlertDialog(
            onDismissRequest = viewModel::cancelarImportacao,
            title = { Text("Importar dados") },
            text = {
                Text(
                    "Encontradas ${dados.transacoes.size} transações" +
                        (if (dados.categorias.isNotEmpty()) {
                            " e ${dados.categorias.size} categorias"
                        } else "") +
                        ".\n\nMesclar: mantém os dados atuais e ignora duplicatas " +
                        "(mesma data, valor e categoria).\n" +
                        "Substituir: apaga as transações atuais do perfil antes de importar."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmarImportacao(substituir = false) }) {
                    Text("Mesclar")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.confirmarImportacao(substituir = true) }) {
                        Text("Substituir")
                    }
                    TextButton(onClick = viewModel::cancelarImportacao) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }
}

/**
 * Seção recolhível das Configurações: cabeçalho com título + resumo e
 * conteúdo que abre/fecha no toque. Começa fechada — a tela vira um índice.
 */
@Composable
private fun SecaoConfig(
    titulo: String,
    subtitulo: String,
    conteudo: @Composable () -> Unit
) {
    var expandida by rememberSaveable(titulo) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandida = !expandida }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titulo,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitulo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Icon(
                    imageVector = if (expandida) {
                        Icons.Filled.ExpandLess
                    } else {
                        Icons.Filled.ExpandMore
                    },
                    contentDescription = if (expandida) "Recolher" else "Expandir",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expandida) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    conteudo()
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

/** Separador entre blocos dentro de uma seção. */
@Composable
private fun DivisorConfig() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    )
}

/** Linha "título + subtítulo" com um botão de ação à direita. */
@Composable
private fun LinhaConfig(
    titulo: String,
    subtitulo: String,
    acao: String,
    onAcao: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitulo,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onAcao) {
            Text(acao)
        }
    }
}

/** Converte reais ("3.500,50" BR ou "3500.50") para centavos; vazio = 0, inválido = -1. */
private fun reaisParaCentavos(texto: String): Long {
    val limpo = texto.trim()
    if (limpo.isEmpty()) return 0L
    val normalizado =
        if (limpo.contains(',')) limpo.replace(".", "").replace(',', '.') else limpo
    val reais = normalizado.toDoubleOrNull() ?: return -1L
    return (reais * 100).roundToLong()
}

/** Círculos de cor para os temas pessoal e empresa (Aparência). */
@Composable
private fun SeletorCorTema(
    titulo: String,
    corSelecionada: CorApp,
    onSelecionar: (CorApp) -> Unit
) {
    Text(
        text = titulo,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CorApp.entries.forEach { cor ->
            val selecionada = corSelecionada == cor
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(cor.hex.toColorInt()))
                    .let {
                        if (selecionada) {
                            it.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            it
                        }
                    }
                    .clickable { onSelecionar(cor) }
            )
        }
    }
}

/** Grade de círculos coloridos para escolher a cor de uma categoria. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeletorCores(
    corSelecionada: String,
    onSelecionar: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CoresCategorias.TODAS.forEach { hex ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        runCatching { Color(hex.toColorInt()) }.getOrDefault(Color.Gray)
                    )
                    .let {
                        if (corSelecionada.equals(hex, ignoreCase = true)) {
                            it.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            it
                        }
                    }
                    .clickable { onSelecionar(hex) }
            )
        }
    }
}

/** Item clicável de ação da seção Dados (exportar, importar, restaurar). */
@Composable
private fun ItemDados(
    icone: ImageVector,
    titulo: String,
    onClick: () -> Unit,
    subtitulo: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = titulo,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitulo != null) {
                Text(
                    text = subtitulo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
