package com.finapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finapp.data.PerfilManager
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.Transacao
import com.finapp.data.io.BackupManager
import com.finapp.data.repository.FinanceRepository
import com.finapp.data.sync.CasaManager
import com.finapp.utils.PeriodoFiltro
import com.finapp.utils.fluxoDataAtual
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: FinanceRepository,
    private val perfilManager: PerfilManager,
    private val backupManager: BackupManager,
    casaManager: CasaManager
) : ViewModel() {

    /** True quando o usuário está numa casa (mostra o indicador de sync). */
    val casaConectada: StateFlow<Boolean> = casaManager.casa
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Perfil escolhido pelo usuário (define layout do dashboard, cor do FAB). */
    val perfil: StateFlow<Perfil> = perfilManager.perfilAtivo

    /** Aba ativa do MEI (Pessoal/Negócio). */
    val contextoMei = perfilManager.contextoMei

    /** Balde de dados efetivo: MEI_PESSOAL/MEI_NEGOCIO quando o perfil é MEI. */
    private val perfilDados: StateFlow<Perfil> = perfilManager.perfilDados

    /** Mensagens transitórias para a UI (toasts/snackbars). */
    private val _mensagens = MutableSharedFlow<String>()
    val mensagens: SharedFlow<String> = _mensagens

    /** Data atual — re-emite à meia-noite para o "mês" nunca ficar velho. */
    private val dataAtual = fluxoDataAtual()
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())

    val saldoTotal: StateFlow<Long> = perfilDados
        .flatMapLatest { repository.observarSaldoTotal(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val ganhosMes: StateFlow<Long> =
        combine(perfilDados, dataAtual) { p, hoje -> p to hoje }
            .flatMapLatest { (p, hoje) ->
                val mes = PeriodoFiltro.MES.intervalo(hoje)!!
                repository.observarGanhos(p, mes.inicio, mes.fim)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val gastosMes: StateFlow<Long> =
        combine(perfilDados, dataAtual) { p, hoje -> p to hoje }
            .flatMapLatest { (p, hoje) ->
                val mes = PeriodoFiltro.MES.intervalo(hoje)!!
                repository.observarGastos(p, mes.inicio, mes.fim)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val ultimasTransacoes: StateFlow<List<Transacao>> = perfilDados
        .flatMapLatest { repository.observarUltimasTransacoes(it, limite = 10) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Backup automático semanal (se ativado nas Configurações)
        viewModelScope.launch {
            runCatching { backupManager.executarSeNecessario() }
        }
        // Ao entrar (ou trocar de perfil/aba): garante categorias padrão
        // e lança transações recorrentes vencidas.
        viewModelScope.launch {
            perfilDados.collect { p ->
                runCatching {
                    repository.garantirCategoriasPadrao(p)
                    repository.processarRecorrentesVencidas()
                }.onFailure {
                    _mensagens.emit("Erro ao preparar dados do perfil")
                }
            }
        }
    }

    fun mudarPerfil(novo: Perfil) = perfilManager.mudarPerfil(novo)

    fun mudarContextoMei(contexto: com.finapp.data.db.entities.ContextoMei) =
        perfilManager.mudarContextoMei(contexto)

    /** Deleta com suporte a desfazer: a UI mostra snackbar e pode chamar [restaurarTransacao]. */
    fun deletarTransacao(transacao: Transacao) {
        viewModelScope.launch {
            runCatching { repository.deletarTransacao(transacao) }
                .onFailure { _mensagens.emit("Erro ao deletar transação") }
        }
    }

    fun restaurarTransacao(transacao: Transacao) {
        viewModelScope.launch {
            // Deleção é lógica: restaurar = limpar o tombstone
            runCatching { repository.restaurarTransacao(transacao) }
                .onFailure { _mensagens.emit("Erro ao restaurar transação") }
        }
    }
}
