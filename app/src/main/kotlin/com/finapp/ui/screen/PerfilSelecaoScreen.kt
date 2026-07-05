package com.finapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.finapp.data.db.entities.ModoUso
import com.finapp.data.db.entities.TipoEmpresa

/**
 * Primeira abertura: mini-onboarding em 2 passos.
 * 1. "O que você quer controlar?" (só pessoal / só empresa / os dois)
 * 2. Se envolver empresa: "Sua empresa é MEI ou CNPJ?"
 */
@Composable
fun PerfilSelecaoScreen(onConcluir: (ModoUso, TipoEmpresa?) -> Unit) {
    var modoEscolhido by remember { mutableStateOf<ModoUso?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val modo = modoEscolhido
        if (modo == null) {
            Text(
                text = "O que você quer controlar?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Dá para mudar depois em Configurações",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            ModoUso.entries.forEach { opcao ->
                CardOpcao(
                    titulo = opcao.rotulo,
                    descricao = opcao.descricao,
                    onClick = {
                        if (opcao.temEmpresa) modoEscolhido = opcao
                        else onConcluir(opcao, null)
                    }
                )
            }
        } else {
            Text(
                text = "Sua empresa é MEI ou CNPJ?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Usamos isso para organizar as finanças da empresa",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            TipoEmpresa.entries.forEach { tipo ->
                CardOpcao(
                    titulo = tipo.rotulo,
                    descricao = tipo.descricao,
                    onClick = { onConcluir(modo, tipo) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { modoEscolhido = null }) {
                Text("Voltar")
            }
        }
    }
}

@Composable
private fun CardOpcao(
    titulo: String,
    descricao: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = descricao,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
