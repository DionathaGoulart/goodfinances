package com.finapp.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    indices = [Index(value = ["perfil", "ativa", "proximoLancamento"])]
)
data class TransacaoRecorrente(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Valor em CENTAVOS. */
    val valor: Long,
    val tipo: TipoTransacao,
    val categoria: String,
    val descricao: String = "",
    val frequencia: Frequencia,
    /** Data em que a próxima transação deve ser lançada automaticamente. */
    val proximoLancamento: LocalDate,
    val perfil: Perfil,
    val ativa: Boolean = true
)
