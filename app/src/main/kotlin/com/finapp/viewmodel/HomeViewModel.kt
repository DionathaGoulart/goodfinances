package com.finapp.viewmodel

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finapp.data.Atualizacao
import com.finapp.data.AtualizacaoManager
import com.finapp.data.PerfilManager
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.Transacao
import com.finapp.data.db.entities.podeSerEditadaPor
import com.finapp.data.io.BackupManager
import com.finapp.data.io.NotaFiscalManager
import com.finapp.data.repository.FinanceRepository
import com.finapp.data.sync.CasaManager
import com.finapp.data.sync.SyncManager
import com.finapp.utils.PeriodoFiltro
import com.finapp.utils.fluxoDataAtual
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** Fechamento do mês anterior, mostrado na Home no começo do mês. Centavos. */
data class ResumoMesAnterior(
    val mes: YearMonth,
    val ganhos: Long,
    val gastos: Long
) {
    val saldo: Long get() = ganhos - gastos
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: FinanceRepository,
    private val perfilManager: PerfilManager,
    private val backupManager: BackupManager,
    private val notaFiscalManager: NotaFiscalManager,
    private val atualizacaoManager: AtualizacaoManager,
    syncManager: SyncManager,
    private val casaManager: CasaManager
) : ViewModel() {

    private val prefs = context.getSharedPreferences("finapp_prefs", Context.MODE_PRIVATE)

    /** True quando o usuário está numa casa (mostra o indicador de sync). */
    val casaConectada: StateFlow<Boolean> = casaManager.casa
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** True quando o sync pessoal entre aparelhos está ligado e logado. */
    val syncPessoalAtivo: StateFlow<Boolean> =
        combine(syncManager.syncPessoalAtivado, casaManager.usuario) { ativo, usuario ->
            ativo && usuario != null
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Abas de contexto da Home (Pessoal | Empresa | Casa, conforme o modo). */
    val contextos: StateFlow<List<Perfil>> = perfilManager.contextosDisponiveis

    /** Contexto/balde ativo — todas as queries seguem este flow. */
    val perfilDados: StateFlow<Perfil> = perfilManager.perfilDados

    /** Mensagens transitórias para a UI (toasts/snackbars). */
    private val _mensagens = MutableSharedFlow<String>()
    val mensagens: SharedFlow<String> = _mensagens

    /** Data atual — re-emite à meia-noite para o cabeçalho e o "mês" nunca ficarem velhos. */
    val dataAtual: StateFlow<LocalDate> = fluxoDataAtual()
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

    private val _atualizacaoDispensada = MutableStateFlow(false)

    /** Versão nova no GitHub (null = nada a mostrar / usuário dispensou). */
    val atualizacao: StateFlow<Atualizacao?> =
        combine(atualizacaoManager.disponivel, _atualizacaoDispensada) { nova, dispensada ->
            if (dispensada) null else nova
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Esconde o aviso até a próxima abertura do app. */
    fun dispensarAtualizacao() {
        _atualizacaoDispensada.value = true
    }

    private val _resumoDispensado =
        MutableStateFlow(prefs.getString(CHAVE_RESUMO_DISPENSADO, "").orEmpty())

    /**
     * Resumo do mês que fechou: aparece nos primeiros dias do mês, se o mês
     * anterior teve movimento e o card não foi dispensado.
     */
    val resumoMesAnterior: StateFlow<ResumoMesAnterior?> =
        combine(perfilDados, dataAtual, _resumoDispensado) { p, hoje, dispensado ->
            Triple(p, hoje, dispensado)
        }
            .flatMapLatest { (p, hoje, dispensado) ->
                val mesAnterior = YearMonth.from(hoje).minusMonths(1)
                if (hoje.dayOfMonth > DIAS_MOSTRANDO_RESUMO ||
                    dispensado == mesAnterior.toString()
                ) {
                    flowOf(null)
                } else {
                    combine(
                        repository.observarGanhos(p, mesAnterior.atDay(1), mesAnterior.atEndOfMonth()),
                        repository.observarGastos(p, mesAnterior.atDay(1), mesAnterior.atEndOfMonth())
                    ) { ganhos, gastos ->
                        if (ganhos == 0L && gastos == 0L) null
                        else ResumoMesAnterior(mesAnterior, ganhos, gastos)
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Esconde o resumo do mês até o próximo fechamento. */
    fun dispensarResumo() {
        val mes = YearMonth.from(dataAtual.value).minusMonths(1).toString()
        prefs.edit { putString(CHAVE_RESUMO_DISPENSADO, mes) }
        _resumoDispensado.value = mes
    }

    init {
        // Checa (no máx. 1x/dia) se há versão nova no GitHub Releases
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { atualizacaoManager.verificar() }
        }
        // Backup automático semanal (se ativado nas Configurações) — I/O de
        // arquivo fora da main thread
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { backupManager.executarSeNecessario() }
        }
        // Apaga arquivos de nota fiscal que ficaram sem transação
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                notaFiscalManager.limparOrfas(
                    repository.listarNotasFiscaisReferenciadas().toSet()
                )
            }
        }
        // Ao entrar (ou trocar de perfil/aba): garante categorias padrão
        // e lança transações recorrentes vencidas.
        viewModelScope.launch {
            perfilDados.collect { p ->
                runCatching {
                    repository.garantirCategoriasPadrao(p)
                    val usuario = casaManager.usuario.value
                    repository.processarRecorrentesVencidas(
                        autorCasa = usuario?.nome.orEmpty(),
                        autorCasaUid = usuario?.uid.orEmpty()
                    )
                }.onFailure {
                    _mensagens.emit("Erro ao preparar dados do perfil")
                }
            }
        }
    }

    /** Troca a aba ativa (Pessoal / Empresa / Casa). */
    fun mudarContexto(contexto: Perfil) = perfilManager.mudarContexto(contexto)

    /** Na Casa, só o autor do lançamento pode editar/apagar. */
    fun podeEditar(transacao: Transacao): Boolean =
        transacao.podeSerEditadaPor(
            uid = casaManager.usuario.value?.uid,
            nomeUsuario = casaManager.usuario.value?.nome
        )

    /** Deleta com suporte a desfazer: a UI mostra snackbar e pode chamar [restaurarTransacao]. */
    fun deletarTransacao(transacao: Transacao) {
        if (!podeEditar(transacao)) {
            viewModelScope.launch { _mensagens.emit("Só quem lançou pode apagar esta transação") }
            return
        }
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

    private companion object {
        const val CHAVE_RESUMO_DISPENSADO = "resumo_mes_dispensado"

        /** O card do fechamento fica visível até este dia do mês. */
        const val DIAS_MOSTRANDO_RESUMO = 7
    }
}
