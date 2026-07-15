package com.finapp.data.notif

import org.junit.Assert.assertEquals
import org.junit.Test

class VencimentosTest {

    // ---------- PeriodoDia: faixas de manhã/tarde/noite ----------

    @Test
    fun `madrugada e manha caem na faixa da manha`() {
        assertEquals(PeriodoDia.MANHA, PeriodoDia.deHora(0))
        assertEquals(PeriodoDia.MANHA, PeriodoDia.deHora(8))
        assertEquals(PeriodoDia.MANHA, PeriodoDia.deHora(11))
    }

    @Test
    fun `meio-dia ate as 17h e tarde`() {
        assertEquals(PeriodoDia.TARDE, PeriodoDia.deHora(12))
        assertEquals(PeriodoDia.TARDE, PeriodoDia.deHora(13))
        assertEquals(PeriodoDia.TARDE, PeriodoDia.deHora(17))
    }

    @Test
    fun `18h em diante e noite`() {
        assertEquals(PeriodoDia.NOITE, PeriodoDia.deHora(18))
        assertEquals(PeriodoDia.NOITE, PeriodoDia.deHora(19))
        assertEquals(PeriodoDia.NOITE, PeriodoDia.deHora(23))
    }

    // ---------- mensagemPrazo ----------

    @Test
    fun `prazos futuros viram vence hoje, amanha ou em N dias`() {
        assertEquals("vence hoje", mensagemPrazo(0))
        assertEquals("vence amanhã", mensagemPrazo(1))
        assertEquals("vence em 2 dias", mensagemPrazo(2))
        assertEquals("vence em 5 dias", mensagemPrazo(DIAS_AVISO_VENCIMENTO))
    }

    @Test
    fun `vencimento passado vira atrasado com plural correto`() {
        assertEquals("atrasou há 1 dia", mensagemPrazo(-1))
        assertEquals("atrasou há 3 dias", mensagemPrazo(-3))
        assertEquals("atrasou há 30 dias", mensagemPrazo(-30))
    }
}
