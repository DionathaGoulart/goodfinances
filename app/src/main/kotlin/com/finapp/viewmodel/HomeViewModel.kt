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
import com.finapp.data.db.entities.FiltroDono
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.Transacao
import com.finapp.data.db.entities.atendeFiltro
import com.finapp.data.db.entities.podeSerEditadaPor
import com.finapp.data.io.BackupManager
import com.finapp.data.io.NotaFiscalManager
import com.finapp.data.repository.FinanceRepository
import com.finapp.data.sync.CasaManager
import com.finapp.data.sync.SyncManager
import com.finapp.utils.fluxoDataAtual
import com.finapp.utils.mesclarListas
import com.finapp.utils.somarBaldes
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

/**
 * Quanto está previsto entrar e sair num mês à frente, em centavos. Sai das
 * pendências JÁ materializadas (recorrência mensal se materializa 12 meses
 * adiante e parcela de cartão nasce no mês da fatura), então não é chute:
 * é o que está agendado.
 */
data class MesPrevisto(
    val mes: YearMonth,
    val aPagar: Long,
    val aReceber: Long
) {
    /** Sobra prevista do mês (negativa = o previsto não cobre as contas). */
    val saldo: Long get() = aReceber - aPagar
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

    /** Abas de contexto da Home (Pessoal | Empresa, conforme o modo). */
    val contextos: StateFlow<List<Perfil>> = perfilManager.contextosDisponiveis

    /** Contexto/balde privado ativo — âncora de escrita e das telas mono-balde. */
    val perfilDados: StateFlow<Perfil> = perfilManager.perfilDados

    /** Baldes lidos pelas listas (pessoal + casa + espelho dos membros). */
    private val baldesVisiveis: StateFlow<List<Perfil>> = perfilManager.baldesVisiveis

    /** Baldes que entram em saldo/somas — sem o espelho dos membros. */
    private val baldesFinanceiros: StateFlow<List<Perfil>> = perfilManager.baldesFinanceiros

    /** Filtro "de quem" da lista (Tudo por padrão). */
    private val _filtroDono = MutableStateFlow<FiltroDono>(FiltroDono.Tudo)
    val filtroDono: StateFlow<FiltroDono> = _filtroDono.asStateFlow()

    fun definirFiltroDono(filtro: FiltroDono) {
        _filtroDono.value = filtro
    }

    /** Trocar de contexto/aba zera o filtro (o de Pessoal não faz sentido na Empresa). */
    private fun resetarFiltroDono() {
        _filtroDono.value = FiltroDono.Tudo
    }

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

    val saldoTotal: StateFlow<Long> = baldesFinanceiros
        .flatMapLatest { baldes -> somarBaldes(baldes) { repository.observarSaldoTotal(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** Gastos atrasados (pendência com vencimento passado, qualquer mês). */
    val atrasado: StateFlow<Long> =
        combine(baldesFinanceiros, dataAtual) { b, hoje -> b to hoje }
            .flatMapLatest { (b, hoje) ->
                somarBaldes(b) { repository.observarAtrasado(it, hoje) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** A pagar no mês visualizado (gastos pendentes, centavos). */
    val aPagarMes: StateFlow<Long> =
        combine(baldesFinanceiros, _mesSelecionado) { b, mes -> b to mes }
            .flatMapLatest { (b, mes) ->
                somarBaldes(b) {
                    repository.observarPendentePorTipo(
                        it, TipoTransacao.GASTO, mes.atDay(1), mes.atEndOfMonth()
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** A receber no mês visualizado (ganhos pendentes: salário, esperados). */
    val aReceberMes: StateFlow<Long> =
        combine(baldesFinanceiros, _mesSelecionado) { b, mes -> b to mes }
            .flatMapLatest { (b, mes) ->
                somarBaldes(b) {
                    repository.observarPendentePorTipo(
                        it, TipoTransacao.GANHO, mes.atDay(1), mes.atEndOfMonth()
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val ganhosMes: StateFlow<Long> =
        combine(baldesFinanceiros, _mesSelecionado) { b, mes -> b to mes }
            .flatMapLatest { (b, mes) ->
                somarBaldes(b) {
                    repository.observarGanhos(it, mes.atDay(1), mes.atEndOfMonth())
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val gastosMes: StateFlow<Long> =
        combine(baldesFinanceiros, _mesSelecionado) { b, mes -> b to mes }
            .flatMapLatest { (b, mes) ->
                somarBaldes(b) {
                    repository.observarGastos(it, mes.atDay(1), mes.atEndOfMonth())
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /**
     * Transações do mês visualizado, já unificadas (pessoal + casa + membros)
     * e passadas pelo filtro "de quem". Cada balde chega ordenado por conta
     * própria — o merge precisa reordenar.
     */
    val transacoesDoMes: StateFlow<List<Transacao>> =
        combine(baldesVisiveis, _mesSelecionado, _filtroDono) { b, mes, filtro ->
            Triple(b, mes, filtro)
        }
            .flatMapLatest { (b, mes, filtro) ->
                mesclarListas(b) {
                    repository.observarTransacoesPeriodo(it, mes.atDay(1), mes.atEndOfMonth())
                }.map { lista ->
                    lista.filter { it.atendeFiltro(filtro) }
                        .sortedWith(compareByDescending<Transacao> { it.data }.thenByDescending { it.id })
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Membros da casa que compartilham os lançamentos pessoais — viram chips
     * de filtro. Sai do próprio espelho: quem não compartilha não aparece.
     */
    val membrosComLancamentos: StateFlow<List<FiltroDono.Membro>> =
        baldesVisiveis
            .flatMapLatest { b ->
                if (Perfil.CASA_MEMBROS !in b) {
                    flowOf(emptyList())
                } else {
                    repository.observarTransacoes(Perfil.CASA_MEMBROS).map { lista ->
                        lista.filter { it.criadoPorUid.isNotBlank() }
                            .distinctBy { it.criadoPorUid }
                            .map { FiltroDono.Membro(it.criadoPorUid, it.criadoPor.ifBlank { "Membro" }) }
                            .sortedBy { it.nome }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * O que já está agendado para os próximos meses (incluindo o atual):
     * responde "o que eu tenho pra pagar daqui pra frente?" sem precisar
     * navegar mês a mês. Uma consulta só no intervalo inteiro, agrupada em
     * memória — 6 meses × 2 tipos em flows separados seriam 12 queries.
     */
    val proximosMeses: StateFlow<List<MesPrevisto>> =
        combine(baldesFinanceiros, dataAtual) { b, hoje -> b to hoje }
            .flatMapLatest { (b, hoje) ->
                val primeiro = YearMonth.from(hoje)
                val ultimo = primeiro.plusMonths(MESES_PREVISTOS - 1L)
                mesclarListas(b) {
                    repository.observarTransacoesPeriodo(
                        it, primeiro.atDay(1), ultimo.atEndOfMonth()
                    )
                }.map { lista ->
                    // Só pendência conta: o que já foi pago saiu do "a pagar"
                    val porMes = lista.filter { !it.pago }.groupBy { YearMonth.from(it.data) }
                    (0 until MESES_PREVISTOS).map { adiante ->
                        val mes = primeiro.plusMonths(adiante.toLong())
                        val doMes = porMes[mes].orEmpty()
                        MesPrevisto(
                            mes = mes,
                            aPagar = doMes.filter { it.tipo == TipoTransacao.GASTO }
                                .sumOf { it.valor },
                            aReceber = doMes.filter { it.tipo == TipoTransacao.GANHO }
                                .sumOf { it.valor }
                        )
                    }
                }
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
        combine(baldesVisiveis, _termoBusca) { b, termo -> b to termo.trim() }
            .flatMapLatest { (b, termo) ->
                if (termo.length < 2) {
                    flowOf(emptyList())
                } else {
                    mesclarListas(b) { repository.buscarTransacoes(it, termo) }
                        .map { lista ->
                            lista.sortedWith(
                                compareByDescending<Transacao> { it.data }
                                    .thenByDescending { it.id }
                            )
                        }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Cartões (globais) — nomes/cores dos grupos de crédito da lista. */
    val cartoes: StateFlow<List<Cartao>> = repository.observarCartoesGlobais()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Cor de cada categoria (nome -> hex), incluindo arquivadas — o ícone das
     * linhas do histórico usa a cor da categoria para reconhecimento rápido.
     */
    val coresCategorias: StateFlow<Map<String, String>> = baldesVisiveis
        .flatMapLatest { b -> mesclarListas(b) { repository.observarCategorias(it) } }
        .map { categorias -> categorias.associate { it.nome to it.cor } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Orçamento agregado do mês exibido: soma dos tetos por categoria vs o
     * gasto delas. Null quando nenhuma categoria tem orçamento — o card some.
     */
    val orcamentoMes: StateFlow<OrcamentoMes?> =
        combine(baldesFinanceiros, _mesSelecionado) { b, mes -> b to mes }
            .flatMapLatest { (b, mes) ->
                // Cada balde traz o próprio teto: o "Mercado" da Casa é um
                // orçamento à parte do "Mercado" pessoal, e os dois somam
                combine(
                    mesclarListas(b) { repository.observarCategorias(it) },
                    mesclarListas(b) {
                        repository.observarGastosPorCategoria(
                            it, mes.atDay(1), mes.atEndOfMonth()
                        )
                    }
                ) { categorias, somas ->
                    val comTeto = categorias.filter { !it.arquivada && it.orcamentoMensal > 0L }
                    if (comTeto.isEmpty()) {
                        null
                    } else {
                        // Nomes repetem entre baldes — soma tudo do mesmo nome
                        val gastoPorNome = somas
                            .groupBy { it.categoria }
                            .mapValues { (_, lista) -> lista.sumOf { it.total } }
                        OrcamentoMes(
                            // O gasto do nome já vem somado de todos os baldes:
                            // contar uma vez por NOME (não por linha) evita dobrar
                            gasto = comTeto.distinctBy { it.nome }
                                .sumOf { gastoPorNome[it.nome] ?: 0L },
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
        combine(baldesFinanceiros, dataAtual, _resumoDispensado) { b, hoje, dispensado ->
            Triple(b, hoje, dispensado)
        }
            .flatMapLatest { (b, hoje, dispensado) ->
                val mesAnterior = YearMonth.from(hoje).minusMonths(1)
                if (hoje.dayOfMonth > DIAS_MOSTRANDO_RESUMO ||
                    dispensado == mesAnterior.toString()
                ) {
                    flowOf(null)
                } else {
                    combine(
                        somarBaldes(b) {
                            repository.observarGanhos(
                                it, mesAnterior.atDay(1), mesAnterior.atEndOfMonth()
                            )
                        },
                        somarBaldes(b) {
                            repository.observarGastos(
                                it, mesAnterior.atDay(1), mesAnterior.atEndOfMonth()
                            )
                        }
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

    /** Troca a aba ativa (Pessoal / Empresa) e volta o filtro para "Tudo". */
    fun mudarContexto(contexto: Perfil) {
        perfilManager.mudarContexto(contexto)
        resetarFiltroDono()
    }

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

        /**
         * Meses na faixa "próximos meses", contando o atual. Seis cobre o
         * horizonte útil sem passar do que a recorrência materializa (12).
         */
        const val MESES_PREVISTOS = 6
    }
}
