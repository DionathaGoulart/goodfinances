package com.finapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finapp.data.PerfilManager
import com.finapp.data.db.entities.Categoria
import com.finapp.data.db.entities.ContaAgendada
import com.finapp.data.db.entities.Meta
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Metas de economia e contas a pagar/receber do contexto ativo. Reage à troca
 * de perfil/aba como o restante do app (via `perfilDados.flatMapLatest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlanejamentoViewModel @Inject constructor(
    private val repository: FinanceRepository,
    perfilManager: PerfilManager
) : ViewModel() {

    private val perfil: StateFlow<Perfil> = perfilManager.perfilDados

    val metas: StateFlow<List<Meta>> = perfil
        .flatMapLatest { repository.observarMetas(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val contasPendentes: StateFlow<List<ContaAgendada>> = perfil
        .flatMapLatest { repository.observarContasPendentes(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Categorias ativas do contexto — para escolher ao criar uma conta. */
    val categorias: StateFlow<List<Categoria>> = perfil
        .flatMapLatest { repository.observarCategorias(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _mensagens = MutableSharedFlow<String>()
    val mensagens: SharedFlow<String> = _mensagens

    // ---------- Metas ----------

    fun criarMeta(nome: String, valorAlvoCentavos: Long, prazo: LocalDate?, cor: String) {
        val nomeLimpo = nome.trim()
        if (nomeLimpo.isEmpty()) {
            emitir("Informe o nome da meta")
            return
        }
        if (valorAlvoCentavos <= 0L) {
            emitir("Informe um valor alvo maior que zero")
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.inserirMeta(
                    Meta(
                        nome = nomeLimpo,
                        valorAlvo = valorAlvoCentavos,
                        prazo = prazo,
                        cor = cor,
                        perfil = perfil.value
                    )
                )
            }
                .onSuccess { emitir("Meta criada") }
                .onFailure { emitir("Erro ao criar meta") }
        }
    }

    /** Aporta [deltaCentavos] (negativo para retirar) na meta. */
    fun aportarMeta(meta: Meta, deltaCentavos: Long) {
        viewModelScope.launch {
            runCatching { repository.aportarMeta(meta, deltaCentavos) }
                .onSuccess {
                    emitir(if (deltaCentavos >= 0) "Aporte registrado" else "Retirada registrada")
                }
                .onFailure { emitir("Erro ao atualizar meta") }
        }
    }

    fun removerMeta(meta: Meta) {
        viewModelScope.launch {
            runCatching { repository.deletarMeta(meta) }
                .onSuccess { emitir("Meta removida") }
                .onFailure { emitir("Erro ao remover meta") }
        }
    }

    // ---------- Contas a pagar/receber ----------

    fun criarConta(
        descricao: String,
        valorCentavos: Long,
        tipo: TipoTransacao,
        categoria: String,
        vencimento: LocalDate
    ) {
        val descLimpa = descricao.trim()
        if (descLimpa.isEmpty()) {
            emitir("Informe a descrição da conta")
            return
        }
        if (valorCentavos <= 0L) {
            emitir("Informe um valor maior que zero")
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.inserirConta(
                    ContaAgendada(
                        descricao = descLimpa,
                        valor = valorCentavos,
                        tipo = tipo,
                        categoria = categoria,
                        vencimento = vencimento,
                        perfil = perfil.value
                    )
                )
            }
                .onSuccess { emitir("Conta agendada") }
                .onFailure { emitir("Erro ao agendar conta") }
        }
    }

    /** Marca como paga: lança a transação e some da lista de pendentes. */
    fun pagarConta(conta: ContaAgendada) {
        viewModelScope.launch {
            runCatching { repository.pagarConta(conta) }
                .onSuccess {
                    emitir(
                        if (conta.tipo == TipoTransacao.GASTO) "Conta paga e lançada"
                        else "Recebimento lançado"
                    )
                }
                .onFailure { emitir("Erro ao dar baixa na conta") }
        }
    }

    fun removerConta(conta: ContaAgendada) {
        viewModelScope.launch {
            runCatching { repository.deletarConta(conta) }
                .onSuccess { emitir("Conta removida") }
                .onFailure { emitir("Erro ao remover conta") }
        }
    }

    private fun emitir(mensagem: String) {
        viewModelScope.launch { _mensagens.emit(mensagem) }
    }
}
