package com.finapp.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Cartão de crédito de um perfil. A compra no crédito é registrada com a
 * data da compra ([Transacao.dataCompra]), mas o gasto cai no mês da fatura:
 * a `data` da transação vira o vencimento da fatura correspondente.
 */
@Entity(
    indices = [
        Index(value = ["perfil"]),
        Index(value = ["uuid"], unique = true)
    ]
)
data class Cartao(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Identidade global para sincronização. */
    val uuid: String = UUID.randomUUID().toString(),
    val nome: String,
    /** Dia do mês em que a fatura fecha (1 a 28). */
    val diaFechamento: Int,
    /** Dia do mês em que a fatura vence (1 a 28). */
    val diaVencimento: Int,
    /** Cor em hex, ex: "#8B5CF6". */
    val cor: String = "#8B5CF6",
    val perfil: Perfil,
    /**
     * Uuid do cartão pessoal de origem quando esta linha é o espelho dele na
     * Casa ("" = cartão nativo do balde). Cartões pessoais aparecem também na
     * Casa via espelho one-way (original → espelho); o espelho é read-only.
     */
    val origemUuid: String = "",
    /** Última modificação (epoch millis) — resolução de conflitos no sync. */
    val atualizadoEm: Long = System.currentTimeMillis(),
    /** Tombstone para o sync. */
    val deletado: Boolean = false
)
