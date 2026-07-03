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
        Index(value = ["uuid"], unique = true)
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
    val criadoPor: String = ""
)
