package com.finapp.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.finapp.data.db.entities.Perfil
import com.finapp.utils.Formatadores

/**
 * Transferir dinheiro do contexto atual para outro (Pessoal / Empresa / Casa).
 * Vira uma saída na origem e uma entrada no destino ("Transferência").
 */
@Composable
fun TransferenciaDialog(
    origem: Perfil,
    destinos: List<Perfil>,
    onTransferir: (destino: Perfil, valorCentavos: Long, descricao: String) -> Unit,
    onFechar: () -> Unit
) {
    var destino by remember { mutableStateOf(destinos.firstOrNull()) }
    var digitos by remember { mutableStateOf("") }
    var descricao by remember { mutableStateOf("") }
    var erroValor by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Transferir de ${origem.rotulo}") },
        text = {
            Column {
                Text(
                    text = "Para onde?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    destinos.forEach { opcao ->
                        FilterChip(
                            selected = destino == opcao,
                            onClick = { destino = opcao },
                            label = { Text(opcao.rotulo) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Máscara de centavos: digite 1234 -> R$ 12,34
                val exibicao = if (digitos.isEmpty()) "" else Formatadores.moeda(digitos.toLong())
                OutlinedTextField(
                    value = TextFieldValue(exibicao, selection = TextRange(exibicao.length)),
                    onValueChange = { novo ->
                        digitos = novo.text.filter(Char::isDigit).trimStart('0').take(10)
                        erroValor = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Valor") },
                    placeholder = { Text("R$ 0,00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = erroValor != null,
                    supportingText = { erroValor?.let { Text(it) } }
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = descricao,
                    onValueChange = { if (it.length <= 100) descricao = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Descrição (opcional)") },
                    placeholder = { Text("Ex: Pró-labore, mercado do mês...") },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sai como gasto de ${origem.rotulo} e entra como ganho " +
                        "no destino, na categoria \"Transferência\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val valor = digitos.toLongOrNull() ?: 0L
                    val alvo = destino
                    if (valor <= 0L) {
                        erroValor = "Informe um valor maior que zero"
                    } else if (alvo != null) {
                        onTransferir(alvo, valor, descricao)
                        onFechar()
                    }
                }
            ) {
                Text("Transferir")
            }
        },
        dismissButton = {
            TextButton(onClick = onFechar) {
                Text("Cancelar")
            }
        }
    )
}
