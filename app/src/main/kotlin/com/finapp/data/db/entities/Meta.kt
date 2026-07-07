package com.finapp.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

/**
 * Meta de economia (guardar dinheiro para um objetivo) — diferente do
 * orçamento, que é teto de gasto. O usuário aporta manualmente e acompanha
 * o progresso até [valorAlvo]. Dados isolados por [perfil], como o resto.
 */
@Entity(
    indices = [
        Index(value = ["perfil"]),
        Index(value = ["uuid"], unique = true)
    ]
)
data class Meta(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Identidade global para sincronização. */
    val uuid: String = UUID.randomUUID().toString(),
    val nome: String,
    /** Valor da meta em CENTAVOS. */
    val valorAlvo: Long,
    /** Quanto já foi guardado, em CENTAVOS. */
    val valorGuardado: Long = 0,
    /** Prazo opcional (epoch day via Converters). */
    val prazo: LocalDate? = null,
    /** Cor em hex, ex: "#10B981". */
    val cor: String = "#10B981",
    val perfil: Perfil,
    /** Última modificação (epoch millis) — resolução de conflitos no sync. */
    val atualizadoEm: Long = System.currentTimeMillis(),
    /** Tombstone para o sync. */
    val deletado: Boolean = false
)
