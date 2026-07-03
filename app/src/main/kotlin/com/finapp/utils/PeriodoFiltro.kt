package com.finapp.utils

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Intervalo fechado de datas usado nos filtros. */
data class Intervalo(val inicio: LocalDate, val fim: LocalDate)

/** Filtros de período disponíveis nas telas de Transações e Análise. */
enum class PeriodoFiltro(val rotulo: String) {
    SEMANA("Semana"),
    MES("Mês"),
    ANO("Ano"),
    PERSONALIZADO("Custom");

    /**
     * Intervalo correspondente ao filtro. Para [PERSONALIZADO] o intervalo
     * vem do usuário, então retorna null.
     */
    fun intervalo(hoje: LocalDate = LocalDate.now()): Intervalo? = when (this) {
        SEMANA -> Intervalo(
            inicio = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
            fim = hoje.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        )
        MES -> Intervalo(
            inicio = hoje.withDayOfMonth(1),
            fim = hoje.with(TemporalAdjusters.lastDayOfMonth())
        )
        ANO -> Intervalo(
            inicio = hoje.withDayOfYear(1),
            fim = hoje.with(TemporalAdjusters.lastDayOfYear())
        )
        PERSONALIZADO -> null
    }
}
