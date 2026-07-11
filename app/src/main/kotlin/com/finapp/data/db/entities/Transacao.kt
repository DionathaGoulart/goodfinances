package com.finapp.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

@Entity(
    indices = [
        Index(value = ["perfil", "data"]),
        Index(value = ["perfil", "tipo"]),
        Index(value = ["uuid"], unique = true),
        Index(value = ["recorrenciaUuid"])
    ]
)
data class Transacao(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Identidade global — ids autoincrement colidem entre aparelhos no sync. */
    val uuid: String = UUID.randomUUID().toString(),
    /** Valor em CENTAVOS (ex: R$ 12,34 = 1234) — evita erro de ponto flutuante. */
    val valor: Long,
    val tipo: TipoTransacao,
    val categoria: String,
    val descricao: String = "",
    val data: LocalDate,
    val perfil: Perfil,
    /** Última modificação (epoch millis) — "última edição vence" no sync. */
    val atualizadoEm: Long = System.currentTimeMillis(),
    /** Tombstone: deleção é lógica para se propagar entre aparelhos. */
    val deletado: Boolean = false,
    /** Nome de quem lançou (só preenchido no perfil Casa). */
    val criadoPor: String = "",
    /** Uid Firebase de quem lançou (perfil Casa) — autoria à prova de nome repetido. */
    val criadoPorUid: String = "",
    /**
     * Vincula as DUAS pernas de uma transferência entre contextos (mesmo
     * valor nas duas). Deletar/restaurar uma perna afeta a outra.
     * Vazio = transação comum.
     */
    val transferenciaId: String = "",
    /**
     * Nome do arquivo da nota fiscal em `filesDir/notas/` (vazio = sem nota).
     * Disponível em todos os contextos; o arquivo é local e fica fora do sync.
     */
    val notaFiscal: String = "",
    /**
     * Oculto da visão Membros da casa: mesmo com "compartilhar lançamentos
     * pessoais" ligado, um lançamento marcado como oculto NÃO é espelhado
     * para os outros membros. Continua contando no seu próprio saldo/análise.
     * Só faz sentido nos baldes pessoais.
     */
    val oculto: Boolean = false,
    /**
     * Uuid do cartão de crédito (vazio = compra em dinheiro/débito). Numa
     * compra no crédito, [data] é o VENCIMENTO da fatura (quando o gasto
     * conta no mês) e [dataCompra] é o dia em que a compra foi feita.
     */
    val cartaoUuid: String = "",
    /** Dia em que a compra no crédito foi feita (null = não é compra de cartão). */
    val dataCompra: LocalDate? = null,
    /**
     * False = pendente: tem data para pagar ([data]) mas ainda não saiu do
     * bolso — NÃO conta no saldo até ser marcada como paga. Nascem pendentes
     * as compras no crédito (pagas ao pagar a fatura), as ocorrências
     * MENSAIS de recorrência (a pagar / a receber) e os ganhos esperados.
     */
    val pago: Boolean = true,
    /**
     * Uuid da [TransacaoRecorrente] que gerou esta ocorrência ("" = manual).
     * Editar/encerrar a recorrência propaga só para as ocorrências futuras
     * NÃO PAGAS vinculadas por aqui.
     */
    val recorrenciaUuid: String = ""
)

/**
 * Na Casa, só quem lançou pode editar/apagar. Compara pelo uid quando
 * disponível (à prova de nomes repetidos); lançamentos antigos só com nome
 * comparam pelo nome; sem autor nenhum, continuam editáveis por todos.
 */
fun Transacao.podeSerEditadaPor(uid: String?, nomeUsuario: String?): Boolean = when {
    perfil != Perfil.CASA -> true
    criadoPorUid.isNotBlank() -> criadoPorUid == uid
    criadoPor.isNotBlank() -> criadoPor == nomeUsuario
    else -> true
}
