package com.finapp.utils

import com.finapp.data.db.entities.Perfil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * Junta o mesmo flow de lista de vários baldes num só. Base da visão
 * unificada Pessoal+Casa: as telas pedem uma lista, o merge faz o resto.
 *
 * `combine` de uma lista VAZIA nunca emite (a UI ficaria travada no valor
 * inicial para sempre) — por isso o curto-circuito.
 */
fun <T> mesclarListas(
    baldes: Collection<Perfil>,
    porBalde: (Perfil) -> Flow<List<T>>
): Flow<List<T>> = when (baldes.size) {
    0 -> flowOf(emptyList())
    1 -> porBalde(baldes.first())
    else -> combine(baldes.map(porBalde)) { listas -> listas.toList().flatten() }
}

/** Soma o mesmo agregado (centavos) de vários baldes. Mesmo cuidado com o vazio. */
fun somarBaldes(
    baldes: Collection<Perfil>,
    porBalde: (Perfil) -> Flow<Long>
): Flow<Long> = when (baldes.size) {
    0 -> flowOf(0L)
    1 -> porBalde(baldes.first())
    else -> combine(baldes.map(porBalde)) { valores -> valores.sum() }
}
