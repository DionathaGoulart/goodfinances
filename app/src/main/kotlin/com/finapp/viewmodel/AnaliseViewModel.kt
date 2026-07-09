package com.finapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finapp.data.PerfilManager
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoEmpresa
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.Transacao
import com.finapp.data.db.entities.ehEmpresa
import com.finapp.data.repository.FinanceRepository
import com.finapp.utils.Intervalo
import com.finapp.utils.PeriodoFiltro
import com.finapp.utils.fluxoDataAtual
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/**
 * Fatia do gráfico de pizza: categoria (ou cartão), total em centavos e cor.
 * [cartaoUuid] preenchido = fatia que agrupa as compras no crédito daquele
 * cartão (débito/dinheiro seguem separados por categoria).
 */
data class FatiaPizza(
    val nome: String,
    val valor: Long,
    val cor: String,
    val cartaoUuid: String = ""
)

/** Ganhos e gastos consolidados de um mês, em centavos (gráficos de linha e barras). */
data class ValorMensal(
    val mes: YearMonth,
    val ganhos: Long,
    val gastos: Long
)

/** Cards de estatísticas rápidas da tela de Análise. Valores em centavos. */
data class Estatisticas(
    val gastoMedioDiario: Long = 0L,
    val maiorGasto: Transacao? = null,
    val maiorGanho: Transacao? = null,
    val categoriaMaiorGasto: String? = null
)

/** Painel fiscal dos contextos de empresa. Valores em centavos. */
data class PainelFiscal(
    val faturamentoAno: Long,
    val faturamentoMes: Long,
    val hoje: LocalDate
)

/** Orçamento mensal de uma categoria de gasto vs o gasto do mês. Centavos. */
data class OrcamentoCategoria(
    val nome: String,
    val cor: String,
    val gastoMes: Long,
    val orcamento: Long
)

/** Fatura de um cartão: total e itens que vencem numa mesma data. Centavos. */
data class Fatura(
    val cartaoNome: String,
    val cartaoCor: String,
    val vencimento: LocalDate,
    val total: Long,
    val itens: List<Transacao>
)

/** Direção de um insight — muda a cor/ícone na UI. */
enum class TipoInsight { ALTA, BAIXA, INFO }

/** Observação proativa derivada da comparação entre o mês atual e o anterior. */
data class Insight(
    val texto: String,
    val tipo: TipoInsight
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnaliseViewModel @Inject constructor(
    repository: FinanceRepository,
    perfilManager: PerfilManager
) : ViewModel() {

    /** Balde de dados efetivo (no MEI, acompanha a aba Pessoal/Negócio). */
    val perfil: StateFlow<Perfil> = perfilManager.perfilDados

    private val _filtro = MutableStateFlow(PeriodoFiltro.MES)
    val filtro: StateFlow<PeriodoFiltro> = _filtro.asStateFlow()

    private val _periodoCustom = MutableStateFlow<Intervalo?>(null)

    /** Data atual — re-emite à meia-noite para o filtro nunca ficar velho. */
    private val dataAtual = fluxoDataAtual()
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())

    /** Intervalo efetivo do filtro atual (fallback: mês corrente). */
    private val intervalo: StateFlow<Intervalo> =
        combine(_filtro, _periodoCustom, dataAtual) { f, custom, hoje ->
            f.intervalo(hoje) ?: custom ?: PeriodoFiltro.MES.intervalo(hoje)!!
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            PeriodoFiltro.MES.intervalo()!!
        )

    /** Tipo exibido no gráfico de categorias (Gastos por padrão, alternável). */
    private val _tipoCategoria = MutableStateFlow(TipoTransacao.GASTO)
    val tipoCategoria: StateFlow<TipoTransacao> = _tipoCategoria.asStateFlow()

    /**
     * Fatias da pizza no período (do tipo escolhido). Débito/dinheiro somam
     * por categoria; compras no crédito viram uma fatia por cartão (mesma
     * separação das listas). Transferências entre baldes ficam de fora.
     */
    val somasPorCategoria: StateFlow<List<FatiaPizza>> =
        combine(perfil, intervalo, _tipoCategoria) { p, i, t -> Triple(p, i, t) }
            .flatMapLatest { (p, i, t) ->
                combine(
                    repository.observarTransacoesPeriodo(p, i.inicio, i.fim),
                    repository.observarCategorias(p),
                    repository.observarCartoes(p)
                ) { transacoes, categorias, cartoes ->
                    val doTipo = transacoes.filter {
                        it.tipo == t && it.categoria != FinanceRepository.NOME_TRANSFERENCIA
                    }
                    val (noCartao, avulsas) = doTipo.partition { it.cartaoUuid.isNotBlank() }
                    val fatiasCategoria = avulsas
                        .groupBy { it.categoria }
                        .map { (nome, itens) ->
                            FatiaPizza(
                                nome = nome,
                                valor = itens.sumOf { it.valor },
                                cor = categorias.firstOrNull { it.nome == nome }?.cor
                                    ?: "#6B7280"
                            )
                        }
                    val fatiasCartao = noCartao
                        .groupBy { it.cartaoUuid }
                        .map { (uuid, itens) ->
                            val cartao = cartoes.firstOrNull { it.uuid == uuid }
                            FatiaPizza(
                                nome = cartao?.nome ?: "Cartão",
                                valor = itens.sumOf { it.valor },
                                cor = cartao?.cor ?: "#8B5CF6",
                                cartaoUuid = uuid
                            )
                        }
                    (fatiasCategoria + fatiasCartao).sortedByDescending { it.valor }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun alterarTipoCategoria(tipo: TipoTransacao) {
        _tipoCategoria.value = tipo
    }

    /** Últimos 6 meses consolidados — gráficos de linha e barras. */
    val seriesMensais: StateFlow<List<ValorMensal>> =
        combine(perfil, dataAtual) { p, hoje -> p to hoje }
            .flatMapLatest { (p, hoje) ->
                val inicio = hoje.minusMonths(5).withDayOfMonth(1)
                val fim = hoje.with(TemporalAdjusters.lastDayOfMonth())
                repository.observarTransacoesPeriodo(p, inicio, fim)
                    .map { transacoes -> consolidarPorMes(transacoes, YearMonth.from(hoje)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Transações do período filtrado, JÁ sem as transferências entre baldes
     * (que não são ganho nem gasto). Base das estatísticas e do detalhamento
     * dos cards.
     */
    val transacoesDoPeriodo: StateFlow<List<Transacao>> =
        combine(perfil, intervalo) { p, i -> p to i }
            .flatMapLatest { (p, i) ->
                repository.observarTransacoesPeriodo(p, i.inicio, i.fim)
                    .map { lista ->
                        lista.filter { it.categoria != FinanceRepository.NOME_TRANSFERENCIA }
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Estatísticas rápidas do período filtrado. */
    val estatisticas: StateFlow<Estatisticas> =
        combine(transacoesDoPeriodo, intervalo) { transacoes, i ->
            calcularEstatisticas(transacoes, i)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Estatisticas())

    /** Tipo da empresa (MEI/CNPJ) — muda o conteúdo do painel fiscal. */
    val tipoEmpresa: StateFlow<TipoEmpresa?> = perfilManager.tipoEmpresa

    /** Painel fiscal: só nos contextos de empresa (null nos demais). */
    val painelFiscal: StateFlow<PainelFiscal?> =
        combine(perfil, dataAtual) { p, hoje -> p to hoje }
            .flatMapLatest { (p, hoje) ->
                if (!p.ehEmpresa) {
                    flowOf(null)
                } else {
                    combine(
                        repository.observarGanhos(
                            p,
                            hoje.withDayOfYear(1),
                            hoje.with(TemporalAdjusters.lastDayOfYear())
                        ),
                        repository.observarGanhos(
                            p,
                            hoje.withDayOfMonth(1),
                            hoje.with(TemporalAdjusters.lastDayOfMonth())
                        )
                    ) { ano, mes -> PainelFiscal(ano, mes, hoje) }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Categorias de gasto com orçamento definido vs o gasto do mês corrente. */
    val orcamentos: StateFlow<List<OrcamentoCategoria>> =
        combine(perfil, dataAtual) { p, hoje -> p to hoje }
            .flatMapLatest { (p, hoje) ->
                combine(
                    repository.observarGastosPorCategoria(
                        p,
                        hoje.withDayOfMonth(1),
                        hoje.with(TemporalAdjusters.lastDayOfMonth())
                    ),
                    repository.observarCategorias(p)
                ) { somas, categorias ->
                    categorias
                        .filter { it.tipo == TipoTransacao.GASTO && it.orcamentoMensal > 0 }
                        .map { categoria ->
                            OrcamentoCategoria(
                                nome = categoria.nome,
                                cor = categoria.cor,
                                gastoMes = somas
                                    .firstOrNull { it.categoria == categoria.nome }
                                    ?.total ?: 0L,
                                orcamento = categoria.orcamentoMensal
                            )
                        }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Insights proativos: compara os gastos por categoria do mês atual com o
     * mês anterior e destaca as maiores variações. Independe do filtro de
     * período (sempre mês vs mês anterior).
     */
    val insights: StateFlow<List<Insight>> =
        combine(perfil, dataAtual) { p, hoje -> p to hoje }
            .flatMapLatest { (p, hoje) ->
                val mesAtual = YearMonth.from(hoje)
                val inicio = mesAtual.minusMonths(1).atDay(1)
                val fim = mesAtual.atEndOfMonth()
                repository.observarTransacoesPeriodo(p, inicio, fim)
                    .map { transacoes -> calcularInsights(transacoes, mesAtual) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Faturas em aberto dos cartões do contexto: agrupa as compras no crédito
     * pela data de vencimento (a `data` da transação de crédito). Mostra da
     * fatura do mês corrente em diante (as anteriores já fecharam).
     */
    val faturas: StateFlow<List<Fatura>> =
        combine(perfil, dataAtual) { p, hoje -> p to hoje }
            .flatMapLatest { (p, hoje) ->
                combine(
                    repository.observarTransacoes(p),
                    repository.observarCartoes(p)
                ) { transacoes, cartoes ->
                    val corPorUuid = cartoes.associate { it.uuid to (it.nome to it.cor) }
                    val inicioMesAtual = YearMonth.from(hoje).atDay(1)
                    transacoes
                        .filter { it.cartaoUuid.isNotBlank() && !it.data.isBefore(inicioMesAtual) }
                        .groupBy { it.cartaoUuid to it.data }
                        .map { (chave, itens) ->
                            val (uuid, vencimento) = chave
                            val info = corPorUuid[uuid]
                            Fatura(
                                cartaoNome = info?.first ?: "Cartão",
                                cartaoCor = info?.second ?: "#8B5CF6",
                                vencimento = vencimento,
                                total = itens.sumOf { it.valor },
                                itens = itens.sortedByDescending { it.dataCompra ?: it.data }
                            )
                        }
                        .sortedBy { it.vencimento }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun alterarPeriodo(filtro: PeriodoFiltro) {
        _filtro.value = filtro
    }

    fun definirPeriodoCustom(inicio: LocalDate, fim: LocalDate) {
        _periodoCustom.value = Intervalo(inicio, fim)
        _filtro.value = PeriodoFiltro.PERSONALIZADO
    }

    private fun consolidarPorMes(
        transacoes: List<Transacao>,
        mesAtual: YearMonth
    ): List<ValorMensal> {
        // Transferências entre baldes não são ganho nem gasto — fora do comparativo.
        val porMes = transacoes
            .filter { it.categoria != FinanceRepository.NOME_TRANSFERENCIA }
            .groupBy { YearMonth.from(it.data) }
        return (5 downTo 0).map { atras ->
            val mes = mesAtual.minusMonths(atras.toLong())
            val doMes = porMes[mes].orEmpty()
            ValorMensal(
                mes = mes,
                ganhos = doMes.filter { it.tipo == TipoTransacao.GANHO }.sumOf { it.valor },
                gastos = doMes.filter { it.tipo == TipoTransacao.GASTO }.sumOf { it.valor }
            )
        }
    }

    /**
     * Compara gastos por categoria entre [mesAtual] e o mês anterior e monta
     * até 3 observações relevantes (variação >= 15% e >= R$ 20). Fecha com o
     * total do mês vs o anterior.
     */
    private fun calcularInsights(
        transacoes: List<Transacao>,
        mesAtual: YearMonth
    ): List<Insight> {
        val gastos = transacoes.filter {
            it.tipo == TipoTransacao.GASTO &&
                it.categoria != FinanceRepository.NOME_TRANSFERENCIA
        }
        val mesAnterior = mesAtual.minusMonths(1)
        fun somasDoMes(mes: YearMonth): Map<String, Long> = gastos
            .filter { YearMonth.from(it.data) == mes }
            .groupBy { it.categoria }
            .mapValues { (_, lista) -> lista.sumOf { it.valor } }

        val atual = somasDoMes(mesAtual)
        val anterior = somasDoMes(mesAnterior)
        if (atual.isEmpty() && anterior.isEmpty()) return emptyList()

        data class Variacao(val categoria: String, val de: Long, val para: Long) {
            val delta = para - de
            val pct = if (de == 0L) 1.0 else delta.toDouble() / de
        }

        val variacoes = (atual.keys + anterior.keys).map { cat ->
            Variacao(cat, anterior[cat] ?: 0L, atual[cat] ?: 0L)
        }.filter {
            kotlin.math.abs(it.delta) >= LIMIAR_INSIGHT_CENTAVOS &&
                (it.de == 0L || kotlin.math.abs(it.pct) >= 0.15)
        }

        val insights = mutableListOf<Insight>()

        variacoes.filter { it.delta > 0 }.maxByOrNull { it.delta }?.let { v ->
            val pct = if (v.de == 0L) null else "${(v.pct * 100).toInt()}% a mais"
            insights += Insight(
                texto = "Gastou ${pct ?: "mais"} em ${v.categoria} que no mês passado " +
                    "(${moedaBr(v.de)} → ${moedaBr(v.para)})",
                tipo = TipoInsight.ALTA
            )
        }
        variacoes.filter { it.delta < 0 }.minByOrNull { it.delta }?.let { v ->
            insights += Insight(
                texto = "Economizou em ${v.categoria}: ${moedaBr(v.de)} → ${moedaBr(v.para)}",
                tipo = TipoInsight.BAIXA
            )
        }

        val totalAtual = atual.values.sum()
        val totalAnterior = anterior.values.sum()
        if (totalAnterior > 0 && totalAtual > 0) {
            val diff = totalAtual - totalAnterior
            val pct = (diff.toDouble() / totalAnterior * 100).toInt()
            if (kotlin.math.abs(pct) >= 10) {
                insights += Insight(
                    texto = if (diff > 0) {
                        "No total, seus gastos subiram $pct% neste mês"
                    } else {
                        "No total, seus gastos caíram ${-pct}% neste mês"
                    },
                    tipo = if (diff > 0) TipoInsight.ALTA else TipoInsight.BAIXA
                )
            }
        }
        return insights.take(3)
    }

    private fun moedaBr(centavos: Long) = com.finapp.utils.Formatadores.moeda(centavos)

    private companion object {
        /** Variação mínima (R$ 20) para uma categoria virar insight. */
        const val LIMIAR_INSIGHT_CENTAVOS = 2_000L
    }

    private fun calcularEstatisticas(
        transacoes: List<Transacao>,
        intervalo: Intervalo
    ): Estatisticas {
        val gastos = transacoes.filter { it.tipo == TipoTransacao.GASTO }
        val ganhos = transacoes.filter { it.tipo == TipoTransacao.GANHO }

        // Média diária considera apenas os dias já decorridos do período
        val fimEfetivo = minOf(LocalDate.now(), intervalo.fim)
        val dias = (ChronoUnit.DAYS.between(intervalo.inicio, fimEfetivo) + 1)
            .coerceAtLeast(1)

        val maiorGasto = gastos.maxByOrNull { it.valor }
        return Estatisticas(
            gastoMedioDiario = gastos.sumOf { it.valor } / dias,
            maiorGasto = maiorGasto,
            maiorGanho = ganhos.maxByOrNull { it.valor },
            categoriaMaiorGasto = gastos
                .groupBy { it.categoria }
                .maxByOrNull { (_, lista) -> lista.sumOf { it.valor } }
                ?.key
        )
    }
}
