package com.finapp.data.io

import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ParserImportacaoTest {

    private val parser = ParserImportacao()
    private val perfil = Perfil.PESSOA_FISICA

    // ---------- CSV ----------

    @Test
    fun `csv simples com header e valores em centavos`() {
        val csv = """
            data,descricao,valor,tipo,categoria
            15/06/2024,Salário,3000.00,ganho,Trabalho
            16/06/2024,Almoço,25.50,gasto,Alimentação
        """.trimIndent()

        val dados = parser.lerTexto(csv, perfil)

        assertEquals(2, dados.transacoes.size)
        val salario = dados.transacoes[0]
        assertEquals(300_000L, salario.valor)
        assertEquals(TipoTransacao.GANHO, salario.tipo)
        assertEquals(LocalDate.of(2024, 6, 15), salario.data)
        assertEquals(2_550L, dados.transacoes[1].valor)
        assertEquals(TipoTransacao.GASTO, dados.transacoes[1].tipo)
    }

    @Test
    fun `csv sem header tambem funciona`() {
        val dados = parser.lerTexto("15/06/2024,Teste,10.00,gasto,Outros", perfil)
        assertEquals(1, dados.transacoes.size)
        assertEquals(1_000L, dados.transacoes[0].valor)
    }

    @Test
    fun `csv com campo entre aspas contendo virgula`() {
        val csv = "data,descricao,valor,tipo,categoria\n" +
            "01/01/2024,\"Almoço, jantar e \"\"extras\"\"\",99.99,gasto,Alimentação"

        val transacao = parser.lerTexto(csv, perfil).transacoes.single()
        assertEquals("Almoço, jantar e \"extras\"", transacao.descricao)
        assertEquals(9_999L, transacao.valor)
    }

    @Test
    fun `csv aceita data iso e valores em formato brasileiro`() {
        val csv = """
            2024-06-15,A,1234.56,ganho,X
            15/06/2024,B,"1.234,56",ganho,X
            15/06/2024,C,R$ 55,gasto,X
        """.trimIndent()

        val dados = parser.lerTexto(csv, perfil)
        assertEquals(123_456L, dados.transacoes[0].valor)
        assertEquals(123_456L, dados.transacoes[1].valor)
        assertEquals(5_500L, dados.transacoes[2].valor)
    }

    @Test
    fun `csv com data invalida lanca erro com numero da linha`() {
        val erro = assertThrows(IllegalArgumentException::class.java) {
            parser.lerTexto("99/99/2024,X,10.00,gasto,Y", perfil)
        }
        assertTrue(erro.message!!.contains("data inválida"))
    }

    @Test
    fun `csv com valor zero e rejeitado`() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.lerTexto("01/01/2024,X,0.00,gasto,Y", perfil)
        }
    }

    @Test
    fun `csv com menos de 5 colunas e rejeitado`() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.lerTexto("01/01/2024,X,10.00", perfil)
        }
    }

    @Test
    fun `arquivo vazio e rejeitado`() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.lerTexto("   \n  ", perfil)
        }
    }

    // ---------- JSON ----------

    @Test
    fun `json completo com transacoes e categorias`() {
        val json = """
            {
              "versao": "1.0",
              "perfil": "pessoa_fisica",
              "transacoes": [
                {"data": "2024-06-15", "descricao": "Salário", "valor": 3000.0,
                 "tipo": "ganho", "categoria": "Trabalho"}
              ],
              "categorias": [
                {"nome": "Trabalho", "tipo": "ganho", "cor": "#10B981"}
              ]
            }
        """.trimIndent()

        val dados = parser.lerTexto(json, perfil)

        assertEquals(1, dados.transacoes.size)
        assertEquals(300_000L, dados.transacoes[0].valor)
        assertEquals(perfil, dados.transacoes[0].perfil)
        assertEquals(1, dados.categorias.size)
        assertEquals("#10B981", dados.categorias[0].cor)
    }

    @Test
    fun `json le pago e assume pago quando ausente`() {
        val json = """
            {"transacoes":[
              {"data":"2024-01-01","valor":10.0,"tipo":"gasto","pago":false},
              {"data":"2024-01-02","valor":20.0,"tipo":"gasto"}
            ]}
        """.trimIndent()

        val transacoes = parser.lerTexto(json, perfil).transacoes

        assertEquals(false, transacoes[0].pago)
        assertEquals(true, transacoes[1].pago)
    }

    @Test
    fun `json preserva centavos sem erro de arredondamento`() {
        val json = """{"transacoes":[{"data":"2024-01-01","valor":0.29,"tipo":"gasto"}]}"""
        assertEquals(29L, parser.lerTexto(json, perfil).transacoes[0].valor)
    }

    @Test
    fun `json malformado lanca erro claro`() {
        val erro = assertThrows(IllegalArgumentException::class.java) {
            parser.lerTexto("{ isso nao é json", perfil)
        }
        assertTrue(erro.message!!.contains("malformado"))
    }

    @Test
    fun `json sem lista de transacoes e rejeitado`() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.lerTexto("""{"versao":"1.0"}""", perfil)
        }
    }
}
