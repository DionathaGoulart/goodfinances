package com.finapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.finapp.data.db.entities.ContaAgendada
import com.finapp.data.db.entities.Perfil
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ContaAgendadaDao {

    @Insert
    suspend fun inserir(conta: ContaAgendada): Long

    @Update
    suspend fun atualizar(conta: ContaAgendada)

    /** Contas ainda pendentes do perfil, do vencimento mais próximo ao mais distante. */
    @Query(
        """
        SELECT * FROM ContaAgendada
        WHERE perfil = :perfil AND deletado = 0 AND pago = 0
        ORDER BY vencimento
        """
    )
    fun observarPendentes(perfil: Perfil): Flow<List<ContaAgendada>>

    /** Todas as contas pendentes (todos os perfis) que vencem até [ate] — notificações. */
    @Query(
        """
        SELECT * FROM ContaAgendada
        WHERE deletado = 0 AND pago = 0 AND perfil = :perfil AND vencimento <= :ate
        ORDER BY vencimento
        """
    )
    suspend fun listarPendentesAte(perfil: Perfil, ate: LocalDate): List<ContaAgendada>

    @Query("SELECT * FROM ContaAgendada WHERE id = :id")
    suspend fun obterPorId(id: Long): ContaAgendada?

    /** Todas as contas vivas do perfil (pendentes e pagas) — backup. */
    @Query("SELECT * FROM ContaAgendada WHERE perfil = :perfil AND deletado = 0")
    suspend fun listarTodas(perfil: Perfil): List<ContaAgendada>

    // ---------- Sincronização (Casa / pessoal) ----------

    @Query("SELECT * FROM ContaAgendada WHERE uuid = :uuid")
    suspend fun obterPorUuid(uuid: String): ContaAgendada?

    /** Sinal de mudança para o push (inclui tombstones). */
    @Query("SELECT COALESCE(MAX(atualizadoEm), 0) FROM ContaAgendada WHERE perfil = :perfil")
    fun observarUltimaModificacao(perfil: Perfil): Flow<Long>

    /** Linhas modificadas desde a última marca de push (inclui tombstones). */
    @Query("SELECT * FROM ContaAgendada WHERE perfil = :perfil AND atualizadoEm > :desde")
    suspend fun listarModificadas(perfil: Perfil, desde: Long): List<ContaAgendada>

    /** Limpeza que propaga no sync: marca tudo como tombstone. */
    @Query(
        "UPDATE ContaAgendada SET deletado = 1, atualizadoEm = :agora " +
            "WHERE perfil = :perfil AND deletado = 0"
    )
    suspend fun marcarTodasDeletadas(perfil: Perfil, agora: Long)

    /** Limpeza local (sair da casa) — delete físico. */
    @Query("DELETE FROM ContaAgendada WHERE perfil = :perfil")
    suspend fun deletarTodas(perfil: Perfil)
}
