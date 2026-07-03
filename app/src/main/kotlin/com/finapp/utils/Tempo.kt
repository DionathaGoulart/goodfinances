package com.finapp.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Emite a data atual e re-emite à meia-noite. Garante que "ganhos do mês",
 * filtros e séries continuem corretos se o app ficar aberto na virada
 * do dia/mês.
 */
fun fluxoDataAtual(): Flow<LocalDate> = flow {
    while (true) {
        val hoje = LocalDate.now()
        emit(hoje)
        val proximaMeiaNoite = hoje.plusDays(1).atStartOfDay()
        val espera = Duration.between(LocalDateTime.now(), proximaMeiaNoite)
            .toMillis()
            .coerceAtLeast(1_000)
        // +1s de folga para garantir que já viramos o dia
        delay(espera + 1_000)
    }
}.distinctUntilChanged()
