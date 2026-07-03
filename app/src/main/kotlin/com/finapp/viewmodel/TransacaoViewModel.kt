package com.finapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finapp.data.PerfilManager
import com.finapp.data.db.entities.Categoria
import com.finapp.data.db.entities.Frequencia
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.Transacao
import com.finapp.data.db.entities.TransacaoRecorrente
import com.finapp.data.repository.FinanceRepository
import com.finapp.data.sync.CasaManager
import com.finapp.utils.Intervalo
import com.finapp.utils.PeriodoFiltro
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransacaoViewModel @Inject constructor(
    private val repository: FinanceRepository,
    perfilManager: PerfilManager,
    private val casaManager: CasaManager
) : ViewModel() {

    /** Balde de dados efetivo (no MEI, acompanha a aba Pessoal/Negócio). */
    val perfil: StateFlow<Perfil> = perfilManager.perfilDados

    private val _filtro = MutableStateFlow(PeriodoFiltro.MES)
    val filtro: StateFlow<PeriodoFiltro> = _filtro.asStateFlow()

    private val _periodoCustom = MutableStateFlow<Intervalo?>(null)
    val periodoCustom: StateFlow<Intervalo?> = _periodoCustom.asStateFlow()

    private val _busca = MutableStateFlow("")
    val busca: StateFlow<String> = _busca.asStateFlow()

    private val _mensagens = MutableSharedFlow<String>()
    val mensagens: SharedFlow<String> = _mensagens

    /** Última transação deletada, para o "desfazer" do swipe. */
    private var ultimaDeletada: Transacao? = null

    /**
     * Lista reativa: refaz a query quando perfil/filtro/período mudam
     * e aplica a busca por descrição em memória.
     */
    val transacoes: StateFlow<List<Transacao>> =
        combine(perfil, _filtro, _periodoCustom) { p, f, custom ->
            Triple(p, f, custom)
        }
            .flatMapLatest { (p, f, custom) ->
                val intervalo = f.intervalo() ?: custom
                if (intervalo != null) {
                    repository.observarTransacoesPeriodo(p, intervalo.inicio, intervalo.fim)
                } else {
                    repository.observarTransacoes(p)
                }
            }
            .combine(_busca) { lista, termo ->
                if (termo.isBlank()) lista
                else lista.filter { it.descricao.contains(termo, ignoreCase = true) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Categorias ativas por tipo — alimentam o dropdown do modal. */
    val categoriasGanho: StateFlow<List<Categoria>> = perfil
        .flatMapLatest { repository.observarCategoriasAtivas(it, TipoTransacao.GANHO) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categoriasGasto: StateFlow<List<Categoria>> = perfil
        .flatMapLatest { repository.observarCategoriasAtivas(it, TipoTransacao.GASTO) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------- Filtros e busca ----------

    fun filtrar(filtro: PeriodoFiltro) {
        _filtro.value = filtro
    }

    fun definirPeriodoCustom(inicio: LocalDate, fim: LocalDate) {
        _periodoCustom.value = Intervalo(inicio, fim)
        _filtro.value = PeriodoFiltro.PERSONALIZADO
    }

    fun buscar(termo: String) {
        _busca.value = termo
    }

    // ---------- CRUD ----------

    /**
     * [valorCentavos] em centavos. Se [repetirMensalmente], cria também uma
     * recorrência mensal a partir do mês seguinte.
     */
    fun adicionarTransacao(
        valorCentavos: Long,
        tipo: TipoTransacao,
        categoria: String,
        descricao: String,
        data: LocalDate,
        repetirMensalmente: Boolean = false
    ) {
        if (valorCentavos <= 0L) {
            emitir("Informe um valor maior que zero")
            return
        }
        viewModelScope.launch {
            // No perfil Casa, registra quem lançou (aparece para os outros membros)
            val autor = if (perfil.value == Perfil.CASA) {
                casaManager.usuario.value?.nome.orEmpty()
            } else {
                ""
            }
            runCatching {
                repository.inserirTransacao(
                    Transacao(
                        valor = valorCentavos,
                        tipo = tipo,
                        categoria = categoria,
                        descricao = descricao.trim(),
                        data = data,
                        perfil = perfil.value,
                        criadoPor = autor
                    )
                )
                if (repetirMensalmente) {
                    repository.inserirRecorrente(
                        TransacaoRecorrente(
                            valor = valorCentavos,
                            tipo = tipo,
                            categoria = categoria,
                            descricao = descricao.trim(),
                            frequencia = Frequencia.MENSAL,
                            proximoLancamento = data.plusMonths(1),
                            perfil = perfil.value
                        )
                    )
                }
            }
                .onSuccess {
                    emitir(
                        if (repetirMensalmente) "Transação adicionada (repete todo mês)"
                        else "Transação adicionada"
                    )
                }
                .onFailure { emitir("Erro ao adicionar transação") }
        }
    }

    fun editarTransacao(transacao: Transacao) {
        if (transacao.valor <= 0L) {
            emitir("Informe um valor maior que zero")
            return
        }
        viewModelScope.launch {
            runCatching { repository.atualizarTransacao(transacao) }
                .onSuccess { emitir("Transação atualizada") }
                .onFailure { emitir("Erro ao atualizar transação") }
        }
    }

    fun deletarTransacao(transacao: Transacao) {
        viewModelScope.launch {
            runCatching { repository.deletarTransacao(transacao) }
                .onSuccess { ultimaDeletada = transacao }
                .onFailure { emitir("Erro ao deletar transação") }
        }
    }

    fun desfazerDelete() {
        val transacao = ultimaDeletada ?: return
        ultimaDeletada = null
        viewModelScope.launch {
            // Deleção é lógica: restaurar = limpar o tombstone
            runCatching { repository.restaurarTransacao(transacao) }
                .onFailure { emitir("Erro ao restaurar transação") }
        }
    }

    private fun emitir(mensagem: String) {
        viewModelScope.launch { _mensagens.emit(mensagem) }
    }
}
