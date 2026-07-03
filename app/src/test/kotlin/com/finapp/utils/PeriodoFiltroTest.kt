package com.finapp.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PeriodoFiltroTest {

    // 2026-07-02 é uma quinta-feira
    private val hoje = LocalDate.of(2026, 7, 2)

    @Test
    fun `semana vai de segunda a domingo`() {
        val intervalo = PeriodoFiltro.SEMANA.intervalo(hoje)!!
        assertEquals(LocalDate.of(2026, 6, 29), intervalo.inicio)
        assertEquals(LocalDate.of(2026, 7, 5), intervalo.fim)
    }

    @Test
    fun `semana quando hoje e segunda comeca hoje`() {
        val segunda = LocalDate.of(2026, 6, 29)
        val intervalo = PeriodoFiltro.SEMANA.intervalo(segunda)!!
        assertEquals(segunda, intervalo.inicio)
    }

    @Test
    fun `mes cobre do dia 1 ao ultimo dia`() {
        val intervalo = PeriodoFiltro.MES.intervalo(hoje)!!
        assertEquals(LocalDate.of(2026, 7, 1), intervalo.inicio)
        assertEquals(LocalDate.of(2026, 7, 31), intervalo.fim)
    }

    @Test
    fun `mes respeita fevereiro`() {
        val intervalo = PeriodoFiltro.MES.intervalo(LocalDate.of(2026, 2, 10))!!
        assertEquals(LocalDate.of(2026, 2, 28), intervalo.fim)
    }

    @Test
    fun `ano cobre de 1 de janeiro a 31 de dezembro`() {
        val intervalo = PeriodoFiltro.ANO.intervalo(hoje)!!
        assertEquals(LocalDate.of(2026, 1, 1), intervalo.inicio)
        assertEquals(LocalDate.of(2026, 12, 31), intervalo.fim)
    }

    @Test
    fun `personalizado nao tem intervalo proprio`() {
        assertNull(PeriodoFiltro.PERSONALIZADO.intervalo(hoje))
    }
}
