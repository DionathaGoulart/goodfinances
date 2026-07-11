package com.finapp.data.repository

import com.finapp.data.db.entities.Frequencia
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.TransacaoRecorrente
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RecorrenciasTest {

    private fun recorrente(
        tipo: TipoTransacao = TipoTransacao.GASTO,
        frequencia: Frequencia = Frequencia.MENSAL,
        diaMensal: Int = 0,
        terminaEm: LocalDate? = null
    ) = TransacaoRecorrente(
        valor = 10_000L,
        tipo = tipo,
        categoria = "Contas",
        descricao = "Internet",
        frequencia = frequencia,
        proximoLancamento = LocalDate.of(2026, 7, 18),
        diaMensal = diaMensal,
        perfil = Perfil.PESSOA_FISICA,
        terminaEm = terminaEm
    )

    // ---------- deveLancarRecorrente: horizonte de 12 meses ----------

    @Test
    fun `mensal materializa ate 12 meses a frente (gasto e ganho)`() {
        val hoje = LocalDate.of(2026, 7, 11)
        listOf(TipoTransacao.GASTO, TipoTransacao.GANHO).forEach { tipo ->
            val rec = recorrente(tipo = tipo, frequencia = Frequencia.MENSAL)
            // Mês corrente e o 12º mês à frente entram
            assertTrue(deveLancarRecorrente(rec, LocalDate.of(2026, 7, 18), hoje))
            assertTrue(
                "$tipo deveria materializar em YM+12",
                deveLancarRecorrente(rec, LocalDate.of(2027, 7, 18), hoje)
            )
            // O 13º fica para depois
            assertFalse(
                "$tipo não deveria materializar em YM+13",
                deveLancarRecorrente(rec, LocalDate.of(2027, 8, 18), hoje)
            )
        }
    }

    @Test
    fun `diaria semanal e anual so lancam no dia`() {
        listOf(Frequencia.DIARIA, Frequencia.SEMANAL, Frequencia.ANUAL).forEach { freq ->
            val rec = recorrente(frequencia = freq)
            val dia = LocalDate.of(2026, 7, 18)
            assertFalse(
                "$freq não deveria antecipar",
                deveLancarRecorrente(rec, dia, LocalDate.of(2026, 7, 17))
            )
            assertTrue(deveLancarRecorrente(rec, dia, LocalDate.of(2026, 7, 18)))
        }
    }

    // ---------- terminaEm ("dura até") ----------

    @Test
    fun `terminaEm bloqueia ocorrencia apos o limite`() {
        val hoje = LocalDate.of(2026, 7, 11)
        val limite = LocalDate.of(2026, 9, 30)
        val rec = recorrente(terminaEm = limite)
        // No limite ainda lança; um dia depois, não (mesmo dentro do horizonte)
        assertTrue(deveLancarRecorrente(rec, LocalDate.of(2026, 9, 30), hoje))
        assertFalse(deveLancarRecorrente(rec, LocalDate.of(2026, 10, 1), hoje))
    }

    @Test
    fun `terminaEm nao afeta ocorrencias dentro do limite`() {
        val hoje = LocalDate.of(2026, 7, 11)
        val rec = recorrente(terminaEm = LocalDate.of(2026, 12, 31))
        assertTrue(deveLancarRecorrente(rec, LocalDate.of(2026, 8, 18), hoje))
        assertTrue(deveLancarRecorrente(rec, LocalDate.of(2026, 12, 18), hoje))
    }

    @Test
    fun `terminaEm vale tambem para diaria semanal e anual`() {
        val hoje = LocalDate.of(2026, 7, 18)
        val rec = recorrente(
            frequencia = Frequencia.DIARIA,
            terminaEm = LocalDate.of(2026, 7, 17)
        )
        // O dia chegou, mas o limite já passou
        assertFalse(deveLancarRecorrente(rec, LocalDate.of(2026, 7, 18), hoje))
    }

    // ---------- proximaOcorrencia ----------

    @Test
    fun `mensal reancora no diaMensal sem travar em fevereiro`() {
        // 31/jan -> 28/fev -> 31/mar (diaMensal preserva a intenção)
        val fev = proximaOcorrencia(Frequencia.MENSAL, 31, LocalDate.of(2026, 1, 31))
        assertEquals(LocalDate.of(2026, 2, 28), fev)
        val mar = proximaOcorrencia(Frequencia.MENSAL, 31, fev)
        assertEquals(LocalDate.of(2026, 3, 31), mar)
    }

    @Test
    fun `mensal sem diaMensal usa o dia da proxima`() {
        val prox = proximaOcorrencia(Frequencia.MENSAL, 0, LocalDate.of(2026, 7, 15))
        assertEquals(LocalDate.of(2026, 8, 15), prox)
    }

    @Test
    fun `diaria semanal e anual avancam o periodo`() {
        val base = LocalDate.of(2026, 7, 18)
        assertEquals(base.plusDays(1), proximaOcorrencia(Frequencia.DIARIA, 0, base))
        assertEquals(base.plusWeeks(1), proximaOcorrencia(Frequencia.SEMANAL, 0, base))
        assertEquals(base.plusYears(1), proximaOcorrencia(Frequencia.ANUAL, 0, base))
    }

    // ---------- ajustarDiaNoMes ----------

    @Test
    fun `ajustarDiaNoMes reancora no dia coagido ao tamanho do mes`() {
        assertEquals(
            LocalDate.of(2026, 7, 31),
            ajustarDiaNoMes(LocalDate.of(2026, 7, 15), 31)
        )
        // Fevereiro coage 31 -> 28
        assertEquals(
            LocalDate.of(2026, 2, 28),
            ajustarDiaNoMes(LocalDate.of(2026, 2, 10), 31)
        )
        // Dia inválido (0) coage para 1
        assertEquals(
            LocalDate.of(2026, 7, 1),
            ajustarDiaNoMes(LocalDate.of(2026, 7, 15), 0)
        )
    }

    // ---------- uuid determinístico da ocorrência ----------

    @Test
    fun `uuidOcorrenciaRecorrente e estavel por recorrencia e data`() {
        val data = LocalDate.of(2026, 8, 18)
        val a = FinanceRepository.uuidOcorrenciaRecorrente("rec-1", data)
        val b = FinanceRepository.uuidOcorrenciaRecorrente("rec-1", data)
        assertEquals(a, b)
        // Muda a data ou a recorrência, muda o uuid
        assertNotEquals(a, FinanceRepository.uuidOcorrenciaRecorrente("rec-1", data.plusMonths(1)))
        assertNotEquals(a, FinanceRepository.uuidOcorrenciaRecorrente("rec-2", data))
    }

    // ---------- sanidade do horizonte ----------

    @Test
    fun `horizonte constante cobre um ano`() {
        assertEquals(12L, MESES_HORIZONTE_MENSAL)
        // A materialização a partir de julho/2026 alcança julho/2027
        val alvo = YearMonth.of(2026, 7).plusMonths(MESES_HORIZONTE_MENSAL)
        assertEquals(YearMonth.of(2027, 7), alvo)
    }
}
