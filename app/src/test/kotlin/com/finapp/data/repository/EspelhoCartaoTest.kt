package com.finapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EspelhoCartaoTest {

    @Test
    fun `uuid do espelho e deterministico e estavel`() {
        val original = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"
        val a = FinanceRepository.uuidEspelhoCartao(original)
        val b = FinanceRepository.uuidEspelhoCartao(original)
        // Determinístico: dois aparelhos derivam o mesmo espelho (LWW converge)
        assertEquals(a, b)
        // E nunca colide com o uuid do original
        assertNotEquals(original, a)
    }

    @Test
    fun `originais diferentes geram espelhos diferentes`() {
        assertNotEquals(
            FinanceRepository.uuidEspelhoCartao("uuid-a"),
            FinanceRepository.uuidEspelhoCartao("uuid-b")
        )
    }
}
