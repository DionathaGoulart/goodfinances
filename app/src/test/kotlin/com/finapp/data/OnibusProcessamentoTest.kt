package com.finapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class OnibusProcessamentoTest {

    // Terça 07/07/2026, quarta 08/07, quinta 09/07 (todas dias úteis)
    private val terca = LocalDate.of(2026, 7, 7)
    private val quarta = LocalDate.of(2026, 7, 8)
    private val quinta = LocalDate.of(2026, 7, 9)
    private val sabado = LocalDate.of(2026, 7, 11)

    private val config = ConfigOnibus(
        valorPassagem = 500L, // R$ 5,00
        diasSemana = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
        ),
        idaEVolta = true,
        saldoAtual = 5_000L, // 10 passagens
        horaIda = LocalTime.of(6, 0),
        horaVolta = LocalTime.of(18, 0)
    )

    // ---------- calcularDescontosOnibus ----------

    @Test
    fun `antes da hora da ida nao desconta nada`() {
        val r = calcularDescontosOnibus(
            config, null, terca.minusDays(1), terca.atTime(5, 30)
        )
        assertEquals(0, r.passagensDescontadas)
        assertFalse(r.novoEstadoDia.idaUsada)
        assertFalse(r.novoEstadoDia.idaProcessada)
    }

    @Test
    fun `depois da hora da ida desconta a ida e marca`() {
        val r = calcularDescontosOnibus(
            config, null, terca.minusDays(1), terca.atTime(7, 0)
        )
        assertEquals(1, r.passagensDescontadas)
        assertTrue(r.novoEstadoDia.idaUsada)
        assertTrue(r.novoEstadoDia.idaProcessada)
        assertFalse(r.novoEstadoDia.voltaUsada)
    }

    @Test
    fun `depois da hora da volta desconta ida e volta`() {
        val r = calcularDescontosOnibus(
            config, null, terca.minusDays(1), terca.atTime(19, 0)
        )
        assertEquals(2, r.passagensDescontadas)
        assertTrue(r.novoEstadoDia.idaUsada)
        assertTrue(r.novoEstadoDia.voltaUsada)
    }

    @Test
    fun `check-in previo nao desconta de novo (idempotente)`() {
        val jaProcessado = EstadoDiaOnibus(
            data = terca, idaUsada = true, idaProcessada = true
        )
        val r = calcularDescontosOnibus(
            config, jaProcessado, terca.minusDays(1), terca.atTime(7, 0)
        )
        assertEquals(0, r.passagensDescontadas)
        // Rodar de novo à noite só desconta a volta
        val r2 = calcularDescontosOnibus(
            config, r.novoEstadoDia, r.novoUltimoDiaProcessado, terca.atTime(19, 0)
        )
        assertEquals(1, r2.passagensDescontadas)
        assertTrue(r2.novoEstadoDia.voltaUsada)
    }

    @Test
    fun `perna dispensada pelo usuario nao e re-descontada`() {
        // Usuário desmarcou a ida ("não usei") — processada mas não usada
        val dispensada = EstadoDiaOnibus(
            data = terca, idaUsada = false, idaProcessada = true
        )
        val r = calcularDescontosOnibus(
            config, dispensada, terca.minusDays(1), terca.atTime(12, 0)
        )
        assertEquals(0, r.passagensDescontadas)
        assertFalse(r.novoEstadoDia.idaUsada)
    }

    @Test
    fun `catch-up de dias fechados desconta os dias de rotina inteiros`() {
        // Cursor na segunda (06/07); agora é quinta de manhã cedo (antes da ida):
        // terça e quarta inteiras = 4 passagens; quinta ainda nada
        val r = calcularDescontosOnibus(
            config, null, terca.minusDays(1), quinta.atTime(5, 0)
        )
        assertEquals(4, r.passagensDescontadas)
        assertEquals(quinta.minusDays(1), r.novoUltimoDiaProcessado)
    }

    @Test
    fun `catch-up respeita check-ins feitos no dia anterior`() {
        // Ontem (terça) o usuário marcou a ida à mão; a volta ninguém processou
        val ontem = EstadoDiaOnibus(data = terca, idaUsada = true, idaProcessada = true)
        val r = calcularDescontosOnibus(
            config, ontem, terca.minusDays(1), quarta.atTime(5, 0)
        )
        // Só a volta de terça (quarta ainda antes da ida)
        assertEquals(1, r.passagensDescontadas)
    }

    @Test
    fun `dia fora da rotina nao desconta`() {
        val r = calcularDescontosOnibus(
            ConfigOnibus(valorPassagem = 500L, saldoAtual = 5_000L),
            null,
            sabado.minusDays(1),
            sabado.atTime(19, 0)
        )
        assertEquals(0, r.passagensDescontadas)
    }

    @Test
    fun `sem saldo para no zero e liga a flag sem ficar devendo`() {
        val pobre = config.copy(saldoAtual = 500L) // 1 passagem
        val r = calcularDescontosOnibus(
            pobre, null, terca.minusDays(1), terca.atTime(19, 0)
        )
        assertEquals(1, r.passagensDescontadas)
        assertTrue(r.saldoInsuficiente)
        // A volta foi processada sem uso: não é dívida nem re-tentada
        assertTrue(r.novoEstadoDia.voltaProcessada)
        assertFalse(r.novoEstadoDia.voltaUsada)
        val r2 = calcularDescontosOnibus(
            pobre.copy(saldoAtual = 0L), r.novoEstadoDia,
            r.novoUltimoDiaProcessado, terca.atTime(20, 0)
        )
        assertEquals(0, r2.passagensDescontadas)
    }

    @Test
    fun `so ida desconta uma por dia`() {
        val soIda = config.copy(idaEVolta = false)
        val r = calcularDescontosOnibus(
            soIda, null, terca.minusDays(1), terca.atTime(19, 0)
        )
        assertEquals(1, r.passagensDescontadas)
        assertFalse(r.novoEstadoDia.voltaProcessada)
    }

    @Test
    fun `nao configurado nao desconta mas avanca o cursor`() {
        val r = calcularDescontosOnibus(
            ConfigOnibus(), null, terca.minusDays(3), terca.atTime(19, 0)
        )
        assertEquals(0, r.passagensDescontadas)
        assertEquals(terca.minusDays(1), r.novoUltimoDiaProcessado)
    }

    // ---------- projetarOnibus com usadasHoje ----------

    @Test
    fun `projecao desconta as passagens ja usadas hoje`() {
        // 10 passagens; hoje (terça) já usou 2 -> hoje não consome nada,
        // cobre qua/qui/sex/seg/ter (5 dias x 2)
        val cheia = projetarOnibus(config, terca, usadasHoje = 0)
        val usadas = projetarOnibus(config, terca, usadasHoje = 2)
        assertEquals(cheia.passagensRestantes, usadas.passagensRestantes)
        // Sem desconto de hoje o saldo acaba um dia útil antes
        assertTrue(usadas.ultimoDia!! > cheia.ultimoDia!!)
    }

    @Test
    fun `projecao com uma usada hoje cobre so a volta de hoje`() {
        val r = projetarOnibus(config.copy(saldoAtual = 500L), terca, usadasHoje = 1)
        assertEquals(terca, r.ultimoDia)
        assertTrue(r.cobreVoltaNoUltimoDia)
    }
}
