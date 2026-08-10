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
     * Dia em que a pendência foi paga/recebida de fato (null = pagou sem
     * registrar o dia, ou nasceu paga — nesses casos [data] é o dia efetivo).
     * O histórico agrupa pelo dia pago; [data] (vencimento) só aparece
     * enquanto a pendência está aberta.
     */
    val dataPagamento: LocalDate? = null,
    /**
     * Uuid da [TransacaoRecorrente] que gerou esta ocorrência ("" = manual).
     * Editar/encerrar a recorrência propaga só para as ocorrências futuras
     * NÃO PAGAS vinculadas por aqui.
     */
    val recorrenciaUuid: String = "",
    /**
     * Uid de QUEM é o gasto/ganho dentro da casa ("" = é da casa, o padrão).
     * Diferente de [criadoPorUid], que é quem DIGITOU: eu posso lançar um
     * gasto que é da minha namorada, e ela pode lançar um que é meu.
     * Só faz sentido no balde [Perfil.CASA]; fora dele fica vazio.
     */
    val pessoaUid: String = "",
    /** Nome de exibição da pessoa em [pessoaUid] (evita depender do sync). */
    val pessoaNome: String = ""
)

/**
 * Dia efetivo no histórico: o dia pago quando a transação já foi paga
 * (linhas antigas sem registro caem na própria [Transacao.data]); o
 * vencimento ([Transacao.data]) enquanto a pendência está aberta.
 */
val Transacao.dataEfetiva: LocalDate
    get() = if (pago) dataPagamento ?: data else data

/**
 * True quando a transação passa pelo filtro "de quem". [meuUid] é o usuário
 * logado — precisa entrar aqui porque "meus gastos" vêm de dois lugares: os
 * atribuídos a mim na casa e os do meu balde privado (inclusive os de antes
 * da atribuição existir).
 */
fun Transacao.atendeFiltro(filtro: FiltroDono, meuUid: String): Boolean = when (filtro) {
    FiltroDono.Tudo -> true
    // Da casa = no balde compartilhado e sem dono específico
    FiltroDono.Casa -> perfil == Perfil.CASA && pessoaUid.isBlank()
    is FiltroDono.Pessoa -> when {
        perfil == Perfil.CASA -> pessoaUid == filtro.uid
        // Espelho: o privado dela, que chega se ela compartilha
        perfil == Perfil.CASA_MEMBROS -> criadoPorUid == filtro.uid
        // Meu balde privado só é "meu" se o filtro for por mim
        else -> filtro.uid == meuUid && meuUid.isNotBlank()
    }
}

/**
 * Rótulo curto de "de quem é", para o selo da linha do histórico. Vazio
 * quando não há o que distinguir (sem casa, ou dentro da empresa) — selo em
 * toda linha só faria ruído.
 */
fun Transacao.rotuloDono(temCasa: Boolean): String = when {
    perfil == Perfil.CASA_MEMBROS -> criadoPor.substringBefore(' ').ifBlank { "Membro" }
    !temCasa || perfil.ehEmpresa -> ""
    perfil == Perfil.CASA && pessoaUid.isBlank() -> "Casa"
    perfil == Perfil.CASA -> pessoaNome.substringBefore(' ').ifBlank { "Pessoa" }
    else -> "Meu"
}

/**
 * Rótulo do contexto para a aba Cartões, que mistura TUDO de propósito
 * (pessoal, casa e empresa no mesmo cartão). Diferente de [rotuloDono], aqui
 * nunca é vazio: é justamente o rótulo que responde "esse gasto foi de onde?".
 */
fun Transacao.rotuloContexto(): String = when {
    perfil == Perfil.CASA -> "Casa"
    perfil == Perfil.CASA_MEMBROS -> criadoPor.ifBlank { "Membro" }
    perfil.ehEmpresa -> "Empresa"
    else -> "Meu"
}

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
