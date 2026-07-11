package com.finapp.data.repository

import com.finapp.data.db.entities.Frequencia
import com.finapp.data.db.entities.TransacaoRecorrente
import java.time.LocalDate
import java.time.YearMonth

/**
 * Regras de agendamento das recorrências, extraídas em funções puras
 * (sem Android) para os testes unitários cobrirem — padrão ParserImportacao.
 */

/** Quantos meses à frente as recorrências MENSAIS são materializadas. */
const val MESES_HORIZONTE_MENSAL = 12L

/**
 * MENSAL (conta fixa, salário) materializa ocorrências até
 * [MESES_HORIZONTE_MENSAL] meses à frente — a pendência ("a pagar"/"a
 * receber") aparece ao navegar os meses futuros, com `data` no dia do
 * vencimento/recebimento, igual às parcelas de cartão. DIARIA/SEMANAL/ANUAL
 * só lançam quando o dia chega. `terminaEm` ("dura até") corta ocorrências
 * além do limite em qualquer frequência.
 */
fun deveLancarRecorrente(
    recorrente: TransacaoRecorrente,
    proxima: LocalDate,
    hoje: LocalDate
): Boolean {
    val termina = recorrente.terminaEm
    if (termina != null && proxima.isAfter(termina)) return false
    return if (recorrente.frequencia == Frequencia.MENSAL) {
        YearMonth.from(proxima) <=
            YearMonth.from(hoje).plusMonths(MESES_HORIZONTE_MENSAL)
    } else {
        !proxima.isAfter(hoje)
    }
}

/** Mesma data reancorada no [dia] do mês (coagido ao tamanho do mês). */
fun ajustarDiaNoMes(data: LocalDate, dia: Int): LocalDate {
    val mes = YearMonth.from(data)
    return mes.atDay(dia.coerceIn(1, mes.lengthOfMonth()))
}

/**
 * Data da ocorrência seguinte a [proxima]. A mensal reancora no [diaMensal]
 * desejado: plusMonths encadeado truncaria 31 -> 28 em fevereiro e a
 * recorrência ficaria presa no dia 28 para sempre.
 */
fun proximaOcorrencia(
    frequencia: Frequencia,
    diaMensal: Int,
    proxima: LocalDate
): LocalDate = when (frequencia) {
    Frequencia.DIARIA -> proxima.plusDays(1)
    Frequencia.SEMANAL -> proxima.plusWeeks(1)
    Frequencia.MENSAL -> {
        val dia = if (diaMensal in 1..31) diaMensal else proxima.dayOfMonth
        val proximoMes = YearMonth.from(proxima).plusMonths(1)
        proximoMes.atDay(dia.coerceAtMost(proximoMes.lengthOfMonth()))
    }
    Frequencia.ANUAL -> proxima.plusYears(1)
}
