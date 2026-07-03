package com.finapp.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ConfiguracaoPerfil(
    @PrimaryKey
    val perfil: Perfil,
    /** Salário em CENTAVOS. */
    val salarioFixo: Long = 0,
    /** Dia do mês em que o salário é recebido (1 a 28). */
    val diaRecebimento: Int = 1
)
