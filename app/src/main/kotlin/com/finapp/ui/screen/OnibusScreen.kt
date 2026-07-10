package com.finapp.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finapp.data.ProjecaoOnibus
import com.finapp.data.db.entities.Perfil
import com.finapp.ui.theme.GreenPrimary
import com.finapp.ui.theme.RedExpense
import com.finapp.utils.Formatadores
import com.finapp.viewmodel.OnibusViewModel
import java.time.DayOfWeek
import java.time.format.TextStyle

/** Aba Ônibus: passagem, dias de uso, projeção do saldo e recarga. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnibusScreen(viewModel: OnibusViewModel = hiltViewModel()) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val projecao by viewModel.projecao.collectAsStateWithLifecycle()
    val contextos by viewModel.contextos.collectAsStateWithLifecycle()
    val perfilDados by viewModel.perfilDados.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.mensagens.collect { snackbarHostState.showSnackbar(it) }
    }

    var recargaAberta by remember { mutableStateOf(false) }

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
            Text(
                text = "ÔNIBUS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ---------- Passagem + dias + ida/volta ----------
            CartaoSecao {
                CampoMoeda(
                    valor = config.valorPassagem,
                    onValor = viewModel::definirValorPassagem,
                    rotulo = "Valor da passagem",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Dias que pega o ônibus",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { dia ->
                        FilterChip(
                            selected = dia in config.diasSemana,
                            onClick = { viewModel.alternarDia(dia) },
                            label = { Text(rotuloCurto(dia)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ida e volta",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (config.idaEVolta) "2 passagens por dia"
                            else "1 passagem por dia (só ida)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.idaEVolta,
                        onCheckedChange = viewModel::definirIdaEVolta
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---------- Saldo do cartão + projeção ----------
            CartaoSecao {
                CampoMoeda(
                    valor = config.saldoAtual,
                    onValor = viewModel::definirSaldo,
                    rotulo = "Saldo atual no cartão",
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Editar aqui não gera gasto — informe o saldo que já tem no cartão.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Usei o ônibus (fora da rotina)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.registrarUso(1) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("−1 passagem")
                    }
                    OutlinedButton(
                        onClick = { viewModel.registrarUso(2) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ida e volta")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Projecao(projecao = projecao, configurado = config.configurado)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { recargaAberta = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Recarregar")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (recargaAberta) {
        RecargaDialog(
            contextos = contextos,
            perfilAtual = perfilDados,
            onConfirmar = { valor, perfil, registrarGasto ->
                viewModel.recarregar(valor, perfil, registrarGasto)
                recargaAberta = false
            },
            onFechar = { recargaAberta = false }
        )
    }
}

/** Resumo de até quando o saldo dura. */
@Composable
private fun Projecao(projecao: ProjecaoOnibus, configurado: Boolean) {
    if (!configurado) {
        Text(
            text = "Preencha o valor da passagem e os dias para ver a projeção.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    if (projecao.passagensRestantes <= 0) {
        Text(
            text = "Saldo insuficiente para uma passagem — hora de recarregar.",
            style = MaterialTheme.typography.bodyMedium,
            color = RedExpense
        )
        return
    }
    val ultimo = projecao.ultimoDia
    Text(
        text = "${projecao.passagensRestantes} passagens · ${projecao.diasDeUso} dias de uso",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    if (ultimo != null) {
        val nomeDia = ultimo.dayOfWeek
            .getDisplayName(TextStyle.FULL, Formatadores.LOCALE_BR)
            .replaceFirstChar { it.uppercase(Formatadores.LOCALE_BR) }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Dá até $nomeDia, ${Formatadores.dataCurta(ultimo)}",
            style = MaterialTheme.typography.bodyLarge,
            color = GreenPrimary
        )
        Text(
            text = if (projecao.cobreVoltaNoUltimoDia) {
                "Nesse dia cobre ida e volta."
            } else {
                "Nesse dia cobre só a ida — leve troco para a volta."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Dialog de recarga: valor, se vira gasto e para qual contexto vai. */
@Composable
private fun RecargaDialog(
    contextos: List<Perfil>,
    perfilAtual: Perfil,
    onConfirmar: (Long, Perfil, Boolean) -> Unit,
    onFechar: () -> Unit
) {
    var valor by remember { mutableStateOf(0L) }
    var perfil by remember { mutableStateOf(perfilAtual) }
    var registrarGasto by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Recarregar ônibus") },
        text = {
            Column {
                CampoMoeda(
                    valor = valor,
                    onValor = { valor = it },
                    rotulo = "Valor da recarga",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { registrarGasto = !registrarGasto },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = registrarGasto,
                        onCheckedChange = { registrarGasto = it }
                    )
                    Text(
                        text = "Registrar como gasto",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (registrarGasto && contextos.size > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Lançar o gasto em",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        contextos.forEach { contexto ->
                            FilterChip(
                                selected = perfil == contexto,
                                onClick = { perfil = contexto },
                                label = { Text(contexto.rotulo) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (registrarGasto) {
                        "Gera um gasto em Transporte (Ônibus) e soma no saldo do cartão."
                    } else {
                        "Só soma no saldo do cartão, sem lançar gasto (ex.: já tinha carga)."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirmar(valor, perfil, registrarGasto) }) {
                Text("Recarregar")
            }
        },
        dismissButton = {
            TextButton(onClick = onFechar) { Text("Cancelar") }
        }
    )
}

/** Card de seção padrão da tela. */
@Composable
private fun CartaoSecao(conteudo: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = conteudo)
    }
}

/**
 * Campo de moeda em centavos (máscara BR): mostra sempre o valor formatado e
 * reconstrói a partir dos dígitos digitados. Cursor fixo no fim (como no
 * modal de transação) — digitar no meio não bagunça a máscara.
 */
@Composable
private fun CampoMoeda(
    valor: Long,
    onValor: (Long) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier
) {
    val exibicao = Formatadores.moeda(valor)
    OutlinedTextField(
        value = TextFieldValue(exibicao, selection = TextRange(exibicao.length)),
        onValueChange = { novo ->
            val digitos = novo.text.filter { it.isDigit() }.trimStart('0').take(12)
            onValor(digitos.toLongOrNull() ?: 0L)
        },
        label = { Text(rotulo) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

/** Rótulo curto do dia da semana em pt-BR (Seg, Ter, …). */
private fun rotuloCurto(dia: DayOfWeek): String = when (dia) {
    DayOfWeek.MONDAY -> "Seg"
    DayOfWeek.TUESDAY -> "Ter"
    DayOfWeek.WEDNESDAY -> "Qua"
    DayOfWeek.THURSDAY -> "Qui"
    DayOfWeek.FRIDAY -> "Sex"
    DayOfWeek.SATURDAY -> "Sáb"
    DayOfWeek.SUNDAY -> "Dom"
}
