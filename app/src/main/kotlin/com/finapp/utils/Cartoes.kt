package com.finapp.utils

import com.finapp.data.db.entities.Cartao

/**
 * Um cartão pode existir em mais de uma linha: o cartão pessoal e o espelho
 * dele na Casa (`origemUuid`), que é como ele chega aos outros membros. Para
 * quem usa o app é UM cartão só — estas funções colapsam as linhas na
 * identidade canônica (a do original) para que a mesma fatura não vire duas.
 */

/** Uuid canônico de um cartão: o do original quando a linha é um espelho. */
fun canonicoCartao(cartoes: List<Cartao>, uuid: String): String =
    cartoes.firstOrNull { it.uuid == uuid }?.origemUuid?.takeIf { it.isNotBlank() }
        ?: uuid

/** Acha o cartão por uuid canônico (aceita cair no espelho quando o original sumiu). */
fun cartaoPorCanonico(cartoes: List<Cartao>, uuid: String): Cartao? =
    cartoes.firstOrNull { it.uuid == uuid }
        ?: cartoes.firstOrNull { it.origemUuid == uuid }

/**
 * Lista de cartões para escolher/exibir: um por identidade canônica.
 * Descarta o espelho quando o original está presente (senão o seletor do
 * modal mostraria "Nubank" duas vezes); um espelho órfão — cartão de OUTRO
 * membro, que chegou pela Casa — continua na lista, é um cartão de verdade.
 */
fun cartoesUnicos(cartoes: List<Cartao>): List<Cartao> {
    val uuids = cartoes.mapTo(mutableSetOf()) { it.uuid }
    return cartoes
        .filterNot { it.origemUuid.isNotBlank() && it.origemUuid in uuids }
        .distinctBy { it.uuid }
        .sortedBy { it.nome }
}
