package com.finapp.viewmodel

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finapp.data.PerfilManager
import com.finapp.data.db.entities.Perfil
import com.finapp.data.sync.Casa
import com.finapp.data.sync.CasaManager
import com.finapp.data.sync.UsuarioCasa
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val casaManager: CasaManager,
    private val perfilManager: PerfilManager
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
        casaManager.sairDaConta()
        // Se estava usando a Casa, volta para o perfil pessoal
        if (perfilManager.perfilAtivo.value == Perfil.CASA) {
            perfilManager.mudarPerfil(Perfil.PESSOA_FISICA)
        }
        emitir("Desconectado")
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
            if (perfilManager.perfilAtivo.value == Perfil.CASA) {
                perfilManager.mudarPerfil(Perfil.PESSOA_FISICA)
            }
            emitir("Você saiu da casa")
        }
    }

    private fun executar(bloco: suspend () -> Unit) {
        viewModelScope.launch {
            _ocupado.value = true
            runCatching { bloco() }
                .onFailure { erro ->
                    when (erro) {
                        is GetCredentialCancellationException -> Unit // usuário cancelou
                        is IllegalArgumentException, is IllegalStateException ->
                            emitir(erro.message ?: "Erro")
                        else -> emitir("Erro de conexão — verifique a internet")
                    }
                }
            _ocupado.value = false
        }
    }

    private fun emitir(mensagem: String) {
        viewModelScope.launch { _mensagens.emit(mensagem) }
    }
}
