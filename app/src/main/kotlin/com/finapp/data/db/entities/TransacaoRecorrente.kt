package com.finapp.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

@Entity(
    indices = [
        Index(value = ["perfil", "ativa", "proximoLancamento"]),
        Index(value = ["uuid"], unique = true)
    ]
)
data class TransacaoRecorrente(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Identidade global para sincronização. */
    val uuid: String = UUID.randomUUID().toString(),
    /** Valor em CENTAVOS. */
    val valor: Long,
    val tipo: TipoTransacao,
    val categoria: String,
    val descricao: String = "",
    val frequencia: Frequencia,
    /** Data em que a próxima transação deve ser lançada automaticamente. */
    val proximoLancamento: LocalDate,
    /**
     * Dia do mês desejado (recorrência MENSAL): preserva a intenção quando o
     * mês é curto — dia 31 lança em 28/02 e VOLTA para 31/03 (sem esse campo,
     * o plusMonths encadeado ficaria preso no dia 28 para sempre).
     * 0 = derivar do próprio [proximoLancamento].
     */
    val diaMensal: Int = 0,
    val perfil: Perfil,
    val ativa: Boolean = true,
    /** Última modificação (epoch millis) — resolução de conflitos no sync. */
    val atualizadoEm: Long = System.currentTimeMillis(),
    /** Tombstone para o sync. */
    val deletado: Boolean = false
)
