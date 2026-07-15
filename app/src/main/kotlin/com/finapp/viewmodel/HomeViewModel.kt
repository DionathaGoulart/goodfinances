package com.finapp.viewmodel

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finapp.data.Atualizacao
import com.finapp.data.AtualizacaoManager
import com.finapp.data.EstadoDownload
import com.finapp.data.OnibusManager
import com.finapp.data.PerfilManager
import com.finapp.data.db.entities.Cartao
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.Transacao
import com.finapp.data.db.entities.podeSerEditadaPor
import com.finapp.data.io.BackupManager
import com.finapp.data.io.NotaFiscalManager
import com.finapp.data.repository.FinanceRepository
import com.finapp.data.sync.CasaManager
import com.finapp.data.sync.SyncManager
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
import kotlinx.coroutines.flow.asStateFlow
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

/** Soma dos orçamentos por categoria vs gasto do mês (centavos). */
data class OrcamentoMes(
    val gasto: Long,
    val teto: Long
) {
    val fracao: Float get() = if (teto <= 0L) 0f else (gasto.toDouble() / teto).toFloat()
    val estourado: Boolean get() = gasto > teto
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
    private val onibusManager: OnibusManager,
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

    /** True quando "compartilhar lançamentos pessoais" está ligado numa casa. */
    val compartilhandoComCasa: StateFlow<Boolean> =
        combine(syncManager.compartilharCasaAtivado, casaManager.casa) { compartilhar, casa ->
            compartilhar && casa != null
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

    /** Mês sendo visualizado na Home (navegável pelo usuário). */
    private val _mesSelecionado = MutableStateFlow(YearMonth.now())
    val mesSelecionado: StateFlow<YearMonth> = _mesSelecionado.asStateFlow()

    /** True quando o mês visualizado é o mês corrente (esconde o "voltar pra hoje"). */
    val ehMesAtual: StateFlow<Boolean> =
        combine(_mesSelecionado, dataAtual) { mes, hoje -> mes == YearMonth.from(hoje) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val saldoTotal: StateFlow<Long> = perfilDados
        .flatMapLatest { repository.observarSaldoTotal(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** Gastos atrasados (pendência com vencimento passado, qualquer mês). */
    val atrasado: StateFlow<Long> =
        combine(perfilDados, dataAtual) { p, hoje -> p to hoje }
            .flatMapLatest { (p, hoje) -> repository.observarAtrasado(p, hoje) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** A pagar no mês visualizado (gastos pendentes, centavos). */
    val aPagarMes: StateFlow<Long> =
        combine(perfilDados, _mesSelecionado) { p, mes -> p to mes }
            .flatMapLatest { (p, mes) ->
                repository.observarPendentePorTipo(
                    p, TipoTransacao.GASTO, mes.atDay(1), mes.atEndOfMonth()
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** A receber no mês visualizado (ganhos pendentes: salário, esperados). */
    val aReceberMes: StateFlow<Long> =
        combine(perfilDados, _mesSelecionado) { p, mes -> p to mes }
            .flatMapLatest { (p, mes) ->
                repository.observarPendentePorTipo(
                    p, TipoTransacao.GANHO, mes.atDay(1), mes.atEndOfMonth()
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val ganhosMes: StateFlow<Long> =
        combine(perfilDados, _mesSelecionado) { p, mes -> p to mes }
            .flatMapLatest { (p, mes) ->
                repository.observarGanhos(p, mes.atDay(1), mes.atEndOfMonth())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val gastosMes: StateFlow<Long> =
        combine(perfilDados, _mesSelecionado) { p, mes -> p to mes }
            .flatMapLatest { (p, mes) ->
                repository.observarGastos(p, mes.atDay(1), mes.atEndOfMonth())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** Transações do mês visualizado (todas, ordenadas da mais recente). */
    val transacoesDoMes: StateFlow<List<Transacao>> =
        combine(perfilDados, _mesSelecionado) { p, mes -> p to mes }
            .flatMapLatest { (p, mes) ->
                repository.observarTransacoesPeriodo(p, mes.atDay(1), mes.atEndOfMonth())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Termo digitado na busca (vazio = sem busca). */
    private val _termoBusca = MutableStateFlow("")
    val termoBusca: StateFlow<String> = _termoBusca.asStateFlow()

    fun buscar(termo: String) {
        _termoBusca.value = termo
    }

    /**
     * Resultados da busca por descrição/categoria no contexto ativo — TODOS
     * os meses, não só o exibido (achar "mercado de março" é o caso de uso).
     * Menos de 2 caracteres não busca (evita varrer tudo a cada tecla).
     */
    val resultadosBusca: StateFlow<List<Transacao>> =
        combine(perfilDados, _termoBusca) { p, termo -> p to termo.trim() }
            .flatMapLatest { (p, termo) ->
                if (termo.length < 2) {
                    flowOf(emptyList())
                } else {
                    repository.buscarTransacoes(p, termo)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Cartões do contexto — nomes/cores dos grupos de crédito da lista. */
    val cartoes: StateFlow<List<Cartao>> = perfilDados
        .flatMapLatest { repository.observarCartoes(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Cor de cada categoria (nome -> hex), incluindo arquivadas — o ícone das
     * linhas do histórico usa a cor da categoria para reconhecimento rápido.
     */
    val coresCategorias: StateFlow<Map<String, String>> = perfilDados
        .flatMapLatest { repository.observarCategorias(it) }
        .map { categorias -> categorias.associate { it.nome to it.cor } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Orçamento agregado do mês exibido: soma dos tetos por categoria vs o
     * gasto delas. Null quando nenhuma categoria tem orçamento — o card some.
     */
    val orcamentoMes: StateFlow<OrcamentoMes?> =
        combine(perfilDados, _mesSelecionado) { p, mes -> p to mes }
            .flatMapLatest { (p, mes) ->
                combine(
                    repository.observarCategorias(p),
                    repository.observarGastosPorCategoria(p, mes.atDay(1), mes.atEndOfMonth())
                ) { categorias, somas ->
                    val comTeto = categorias.filter { !it.arquivada && it.orcamentoMensal > 0L }
                    if (comTeto.isEmpty()) {
                        null
                    } else {
                        val gastoPorNome = somas.associate { it.categoria to it.total }
                        OrcamentoMes(
                            gasto = comTeto.sumOf { gastoPorNome[it.nome] ?: 0L },
                            teto = comTeto.sumOf { it.orcamentoMensal }
                        )
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Vai para o mês anterior / próximo / um mês qualquer / de volta ao atual. */
    fun mesAnterior() {
        _mesSelecionado.value = _mesSelecionado.value.minusMonths(1)
    }

    fun mesProximo() {
        _mesSelecionado.value = _mesSelecionado.value.plusMonths(1)
    }

    fun selecionarMes(mes: YearMonth) {
        _mesSelecionado.value = mes
    }

    fun irParaMesAtual() {
        _mesSelecionado.value = YearMonth.from(dataAtual.value)
    }

    private val _atualizacaoDispensada = MutableStateFlow(false)

    /** Versão nova no GitHub (null = nada a mostrar / usuário dispensou). */
    val atualizacao: StateFlow<Atualizacao?> =
        combine(atualizacaoManager.disponivel, _atualizacaoDispensada) { nova, dispensada ->
            if (dispensada) null else nova
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Progresso do download do APK novo (dialog da Home). */
    val downloadAtualizacao: StateFlow<EstadoDownload> = atualizacaoManager.download

    /** Baixa o APK da release e abre o instalador; fecha o aviso se der certo. */
    fun baixarAtualizacao() {
        val nova = atualizacao.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (atualizacaoManager.baixarEInstalar(nova)) {
                _atualizacaoDispensada.value = true
            }
        }
    }

    /** Esconde o aviso até a próxima abertura do app. */
    fun dispensarAtualizacao() {
        atualizacaoManager.limparDownload()
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
        // Desconto automático das passagens de ônibus (dias de rotina):
        // roda ao abrir o app e na virada do dia, sem visitar a aba Ônibus
        viewModelScope.launch {
            fluxoDataAtual().collect {
                runCatching { onibusManager.processarDescontosAutomaticos() }
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
        if (!podeEditar(transacao)) {
            viewModelScope.launch { _mensagens.emit("Só quem lançou pode restaurar esta transação") }
            return
        }
        viewModelScope.launch {
            // Deleção é lógica: restaurar = limpar o tombstone
            runCatching { repository.restaurarTransacao(transacao) }
                .onFailure { _mensagens.emit("Erro ao restaurar transação") }
        }
    }

    /** Marca a pendência como paga (ou reverte) — aí sim desconta do saldo. */
    fun alternarPago(transacao: Transacao) {
        if (!podeEditar(transacao)) {
            viewModelScope.launch { _mensagens.emit("Só quem lançou pode dar baixa nesta compra") }
            return
        }
        viewModelScope.launch {
            runCatching { repository.marcarTransacaoPaga(transacao, !transacao.pago) }
                .onSuccess {
                    val ehGanho = transacao.tipo == TipoTransacao.GANHO
                    _mensagens.emit(
                        when {
                            !transacao.pago && ehGanho -> "Recebimento confirmado"
                            !transacao.pago -> "Pagamento confirmado"
                            ehGanho -> "Marcado como a receber"
                            else -> "Marcado como pendente"
                        }
                    )
                }
                .onFailure { _mensagens.emit("Erro ao atualizar pagamento") }
        }
    }

    /**
     * Paga a fatura do cartão: confirma as compras pendentes do grupo.
     * Na Casa, dá baixa só nas MINHAS compras — marcar as de outro membro
     * como pagas ficaria só no meu aparelho (o push filtra por autor e as
     * regras negam), divergindo o saldo da casa para sempre.
     */
    fun pagarFatura(transacoes: List<Transacao>) {
        val pendentes = transacoes.filter { !it.pago }
        if (pendentes.isEmpty()) return
        val minhas = pendentes.filter { podeEditar(it) }
        if (minhas.isEmpty()) {
            viewModelScope.launch { _mensagens.emit("Só quem lançou pode dar baixa nessas compras") }
            return
        }
        viewModelScope.launch {
            runCatching { repository.pagarTransacoes(minhas) }
                .onSuccess {
                    _mensagens.emit(
                        if (minhas.size < pendentes.size) {
                            "Suas compras foram pagas — as dos outros membros ficam com quem lançou"
                        } else {
                            "Fatura paga — saldo atualizado"
                        }
                    )
                }
                .onFailure { _mensagens.emit("Erro ao pagar a fatura") }
        }
    }

    /** Alterna esconder/reexibir da visão Membros (só faz sentido no pessoal). */
    fun alternarOculto(transacao: Transacao) {
        if (!podeEditar(transacao)) {
            viewModelScope.launch { _mensagens.emit("Só quem lançou pode esconder esta transação") }
            return
        }
        viewModelScope.launch {
            runCatching { repository.ocultarTransacao(transacao, !transacao.oculto) }
                .onSuccess {
                    _mensagens.emit(
                        if (!transacao.oculto) "Escondido da visão Membros"
                        else "Voltou a aparecer na visão Membros"
                    )
                }
                .onFailure { _mensagens.emit("Erro ao esconder transação") }
        }
    }

    private companion object {
        const val CHAVE_RESUMO_DISPENSADO = "resumo_mes_dispensado"

        /** O card do fechamento fica visível até este dia do mês. */
        const val DIAS_MOSTRANDO_RESUMO = 7
    }
}
