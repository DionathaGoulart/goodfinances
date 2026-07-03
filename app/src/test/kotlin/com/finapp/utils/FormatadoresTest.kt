package com.finapp.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FormatadoresTest {

    /** NumberFormat pt-BR pode usar NBSP (u00A0) ou NNBSP (u202F) após "R$". */
    private fun normalizar(texto: String) =
        texto.replace(' ', ' ').replace(' ', ' ')

    @Test
    fun `moeda formata centavos no padrao brasileiro`() {
        val resultado = normalizar(Formatadores.moeda(123_456L))
        assertEquals("R$ 1.234,56", resultado)
    }

    @Test
    fun `moeda formata zero e valores negativos`() {
        assertTrue(normalizar(Formatadores.moeda(0L)).contains("0,00"))
        assertTrue(normalizar(Formatadores.moeda(-9_950L)).contains("99,50"))
    }

    @Test
    fun `moeda nao perde centavos`() {
        assertTrue(normalizar(Formatadores.moeda(1L)).contains("0,01"))
        assertTrue(normalizar(Formatadores.moeda(29L)).contains("0,29"))
    }

    @Test
    fun `moedaCompacta abrevia milhares e milhoes`() {
        assertEquals("350", Formatadores.moedaCompacta(35_000L))       // R$ 350
        assertEquals("1,3k", Formatadores.moedaCompacta(125_000L))     // R$ 1.250
        assertEquals("2,5M", Formatadores.moedaCompacta(250_000_000L)) // R$ 2,5 mi
    }

    @Test
    fun `dataCurta usa dd MM yyyy`() {
        assertEquals("02/07/2026", Formatadores.dataCurta(LocalDate.of(2026, 7, 2)))
    }
}
