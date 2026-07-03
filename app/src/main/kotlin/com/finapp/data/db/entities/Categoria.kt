package com.finapp.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [Index(value = ["perfil", "tipo", "arquivada"])]
)
data class Categoria(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nome: String,
    val tipo: TipoTransacao,
    /** Cor em hex, ex: "#10B981" */
    val cor: String,
    val perfil: Perfil,
    /** Categorias arquivadas não aparecem em novos lançamentos, mas preservam o histórico. */
    val arquivada: Boolean = false
)
