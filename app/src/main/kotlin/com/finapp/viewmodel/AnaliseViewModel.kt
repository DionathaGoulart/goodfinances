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

/** Fatia do gráfico de pizza: categoria, total gasto (centavos) e cor cadastrada. */
data class FatiaPizza(
    val nome: String,
    val valor: Long,
    val cor: String
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

    /** Somas por categoria no período (do tipo escolhido) — gráfico de pizza. */
    val somasPorCategoria: StateFlow<List<FatiaPizza>> =
        combine(perfil, intervalo, _tipoCategoria) { p, i, t -> Triple(p, i, t) }
            .flatMapLatest { (p, i, t) ->
                combine(
                    repository.observarSomasPorCategoria(p, t, i.inicio, i.fim),
                    repository.observarCategorias(p)
                ) { somas, categorias ->
                    somas.map { soma ->
                        FatiaPizza(
                            nome = soma.categoria,
                            valor = soma.total,
                            cor = categorias.firstOrNull { it.nome == soma.categoria }?.cor
                                ?: "#6B7280"
                        )
                    }
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
