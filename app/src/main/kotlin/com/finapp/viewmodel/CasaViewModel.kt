package com.finapp.viewmodel

import android.content.Context
import android.util.Log
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finapp.data.sync.Casa
import com.finapp.data.sync.CasaManager
import com.finapp.data.sync.UsuarioCasa
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Login Google + Casa compartilhada (Configurações). */
@HiltViewModel
class CasaViewModel @Inject constructor(
    private val casaManager: CasaManager
) : ViewModel() {

    val usuario: StateFlow<UsuarioCasa?> = casaManager.usuario
    val casa: StateFlow<Casa?> = casaManager.casa

    private val _ocupado = MutableStateFlow(false)
    val ocupado: StateFlow<Boolean> = _ocupado.asStateFlow()

    private val _mensagens = MutableSharedFlow<String>()
    val mensagens: SharedFlow<String> = _mensagens

    init {
        viewModelScope.launch { casaManager.carregarCasa() }
    }

    /** [contextoActivity] vem da UI (o seletor de contas precisa da Activity). */
    fun entrarComGoogle(contextoActivity: Context) {
        executar {
            casaManager.entrarComGoogle(contextoActivity)
            emitir("Conectado!")
        }
    }

    fun sairDaConta() {
        executar {
            casaManager.sairDaConta()
            emitir("Desconectado")
        }
    }

    fun criarCasa() {
        executar {
            val nova = casaManager.criarCasa()
            emitir("Casa criada! Código: ${nova.codigoConvite}")
        }
    }

    fun entrarNaCasa(codigo: String) {
        if (codigo.isBlank()) {
            emitir("Informe o código de convite")
            return
        }
        executar {
            casaManager.entrarNaCasa(codigo)
            emitir("Você entrou na casa!")
        }
    }

    fun sairDaCasa() {
        executar {
            casaManager.sairDaCasa()
            emitir("Você saiu da casa")
        }
    }

    private fun executar(bloco: suspend () -> Unit) {
        viewModelScope.launch {
            _ocupado.value = true
            runCatching { bloco() }
                .onFailure { erro ->
                    Log.e(TAG, "Operação da Casa falhou", erro)
                    traduzirErro(erro)?.let(::emitir)
                }
            _ocupado.value = false
        }
    }

    /** Mensagem para o usuário conforme a falha real (null = silencioso). */
    private fun traduzirErro(erro: Throwable): String? = when (erro) {
        is GetCredentialCancellationException -> null // usuário cancelou
        is NoCredentialException ->
            "Nenhuma conta Google no aparelho — adicione uma em Ajustes > Contas e tente de novo"
        is GetCredentialProviderConfigurationException ->
            "Google Play Services indisponível ou desatualizado neste aparelho"
        is GetCredentialException ->
            "Falha no login Google — configuração do app no Firebase " +
                "(confira o SHA-1 do certificado). Detalhe: ${erro.type}"
        is FirebaseNetworkException ->
            "Erro de conexão — verifique a internet"
        is FirebaseAuthException ->
            "Erro de autenticação (${erro.errorCode})"
        is FirebaseFirestoreException ->
            if (erro.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                "Erro de conexão — verifique a internet"
            } else {
                "Erro no servidor (${erro.code})"
            }
        is IOException -> "Erro de conexão — verifique a internet"
        is IllegalArgumentException, is IllegalStateException -> erro.message ?: "Erro"
        else -> "Erro inesperado: ${erro.message ?: erro.javaClass.simpleName}"
    }

    private fun emitir(mensagem: String) {
        viewModelScope.launch { _mensagens.emit(mensagem) }
    }

    private companion object {
        const val TAG = "CasaViewModel"
    }
}
