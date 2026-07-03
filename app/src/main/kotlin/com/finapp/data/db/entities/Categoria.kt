package com.finapp.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    indices = [
        Index(value = ["perfil", "tipo", "arquivada"]),
        Index(value = ["uuid"], unique = true)
    ]
)
data class Categoria(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Identidade global para sincronização. */
    val uuid: String = UUID.randomUUID().toString(),
    val nome: String,
    val tipo: TipoTransacao,
    /** Cor em hex, ex: "#10B981" */
    val cor: String,
    val perfil: Perfil,
    /** Categorias arquivadas não aparecem em novos lançamentos, mas preservam o histórico. */
    val arquivada: Boolean = false,
    /** Última modificação (epoch millis) — resolução de conflitos no sync. */
    val atualizadoEm: Long = System.currentTimeMillis(),
    /** Tombstone para o sync. */
    val deletado: Boolean = false
)
