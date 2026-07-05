package com.finapp

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.FragmentActivity
import com.finapp.data.AparenciaManager
import com.finapp.data.PerfilManager
import com.finapp.data.SegurancaManager
import com.finapp.data.db.entities.ehEmpresa
import com.finapp.ui.FinanApp
import com.finapp.ui.theme.FinanAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// FragmentActivity (e não ComponentActivity): exigência do BiometricPrompt
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var aparenciaManager: AparenciaManager

    @Inject
    lateinit var perfilManager: PerfilManager

    @Inject
    lateinit var segurancaManager: SegurancaManager

    /** Sessão desbloqueada (reinicia a cada recriação da Activity). */
    private val desbloqueado = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Widget "Novo lançamento": abre direto no modal de nova transação.
        // Consome o extra para a recriação da Activity não reabrir o modal.
        val abrirLancamento =
            intent?.getBooleanExtra(EXTRA_ABRIR_LANCAMENTO, false) == true
        intent?.removeExtra(EXTRA_ABRIR_LANCAMENTO)
        setContent {
            val escala by aparenciaManager.escalaFonte.collectAsState()
            val corPessoal by aparenciaManager.corPessoal.collectAsState()
            val corEmpresa by aparenciaManager.corEmpresa.collectAsState()
            val perfilDados by perfilManager.perfilDados.collectAsState()
            val bloqueioAtivado by segurancaManager.bloqueioAtivado.collectAsState()

            // Contexto de empresa (CNPJ ou aba Empresa do modo misto) usa a
            // cor da empresa; o resto (pessoal e Casa), a cor pessoal.
            val corAlvo = if (perfilDados.ehEmpresa) corEmpresa else corPessoal
            val cor by animateColorAsState(
                targetValue = Color(corAlvo.hex.toColorInt()),
                animationSpec = tween(durationMillis = 400),
                label = "corTema"
            )

            FinanAppTheme(
                escalaFonte = escala.fator,
                corPrimaria = cor
            ) {
                // Sem biometria/PIN configurado no aparelho, não trava (fail-open)
                if (bloqueioAtivado && !desbloqueado.value && podeAutenticar()) {
                    TelaBloqueio(onDesbloquear = ::autenticar)
                    LaunchedEffect(Unit) { autenticar() }
                } else {
                    FinanApp(abrirLancamento = abrirLancamento)
                }
            }
        }
    }

    private fun podeAutenticar(): Boolean =
        BiometricManager.from(this)
            .canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private fun autenticar() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    desbloqueado.value = true
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear FinanApp")
            .setSubtitle("Use sua biometria ou o bloqueio do aparelho")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }

    companion object {
        const val EXTRA_ABRIR_LANCAMENTO = "abrir_lancamento"
    }
}

/** Tela mostrada enquanto o app está travado pela biometria. */
@Composable
private fun TelaBloqueio(onDesbloquear: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "FinanApp bloqueado",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onDesbloquear) {
            Text("Desbloquear")
        }
    }
}
