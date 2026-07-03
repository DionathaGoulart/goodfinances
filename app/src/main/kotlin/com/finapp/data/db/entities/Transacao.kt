package com.finapp.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    indices = [
        Index(value = ["perfil", "data"]),
        Index(value = ["perfil", "tipo"])
    ]
)
data class Transacao(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Valor em CENTAVOS (ex: R$ 12,34 = 1234) — evita erro de ponto flutuante. */
    val valor: Long,
    val tipo: TipoTransacao,
    val categoria: String,
    val descricao: String = "",
    val data: LocalDate,
    val perfil: Perfil
)
