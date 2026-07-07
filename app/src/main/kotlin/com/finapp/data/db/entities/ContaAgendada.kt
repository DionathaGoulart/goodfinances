package com.finapp.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

/**
 * Conta a pagar/receber agendada (boleto, mensalidade avulsa...). Fica
 * PENDENTE até o usuário marcar como paga — aí vira uma [Transacao] real na
 * data de pagamento. Diferente da recorrência (que repete sozinha) e da
 * parcela (valor fixo já lançado): aqui existe um estado pendente e um
 * lembrete de vencimento. Dados isolados por [perfil].
 */
@Entity(
    indices = [
        Index(value = ["perfil", "pago"]),
        Index(value = ["uuid"], unique = true)
    ]
)
data class ContaAgendada(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Identidade global para sincronização. */
    val uuid: String = UUID.randomUUID().toString(),
    val descricao: String,
    /** Valor em CENTAVOS. */
    val valor: Long,
    /** GASTO = conta a pagar; GANHO = valor a receber. */
    val tipo: TipoTransacao,
    val categoria: String,
    /** Data de vencimento (epoch day via Converters). */
    val vencimento: LocalDate,
    /** True depois de marcada como paga (já virou transação). */
    val pago: Boolean = false,
    val perfil: Perfil,
    /** Última modificação (epoch millis) — resolução de conflitos no sync. */
    val atualizadoEm: Long = System.currentTimeMillis(),
    /** Tombstone para o sync. */
    val deletado: Boolean = false
)
