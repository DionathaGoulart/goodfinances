package com.finapp.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.Transacao
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Soma de gastos agrupada por categoria (para o gráfico de pizza). Total em centavos. */
data class SomaPorCategoria(
    val categoria: String,
    val total: Long
)

@Dao
interface TransacaoDao {

    // ---------- CRUD ----------

    @Insert
    suspend fun inserir(transacao: Transacao): Long

    @Insert
    suspend fun inserirTodas(transacoes: List<Transacao>)

    @Query("SELECT * FROM Transacao WHERE perfil = :perfil AND deletado = 0")
    suspend fun listarTodas(perfil: Perfil): List<Transacao>

    /** Todas as linhas do perfil, INCLUSIVE tombstones — restauração de backup. */
    @Query("SELECT * FROM Transacao WHERE perfil = :perfil")
    suspend fun listarComTombstones(perfil: Perfil): List<Transacao>

    /** Todos os uuids do perfil, INCLUSIVE tombstones — dedup de importação. */
    @Query("SELECT uuid FROM Transacao WHERE perfil = :perfil")
    suspend fun listarUuids(perfil: Perfil): List<String>

    // ---------- Sincronização (Casa) ----------

    @Query("SELECT * FROM Transacao WHERE uuid = :uuid")
    suspend fun obterPorUuid(uuid: String): Transacao?

    /** Sinal de mudança para o push (inclui tombstones). */
    @Query("SELECT COALESCE(MAX(atualizadoEm), 0) FROM Transacao WHERE perfil = :perfil")
    fun observarUltimaModificacao(perfil: Perfil): Flow<Long>

    /** Linhas modificadas desde a última marca de push (inclui tombstones). */
    @Query("SELECT * FROM Transacao WHERE perfil = :perfil AND atualizadoEm > :desde")
    suspend fun listarModificadas(perfil: Perfil, desde: Long): List<Transacao>

    @Update
    suspend fun atualizar(transacao: Transacao)

    @Delete
    suspend fun deletar(transacao: Transacao)

    @Query("DELETE FROM Transacao WHERE perfil = :perfil")
    suspend fun deletarTodas(perfil: Perfil)

    /** Limpeza que propaga no sync: marca tudo como tombstone (todos os baldes do usuário). */
    @Query(
        """
        UPDATE Transacao SET deletado = 1, atualizadoEm = :agora
        WHERE perfil = :perfil AND deletado = 0
        """
    )
    suspend fun marcarTodasDeletadas(perfil: Perfil, agora: Long)

    /** Nomes de arquivo de nota fiscal referenciados (todos os perfis). */
    @Query("SELECT notaFiscal FROM Transacao WHERE notaFiscal != ''")
    suspend fun listarNotasFiscais(): List<String>

    /** Remove um espelho da visão Membros (o dono parou de compartilhar). */
    @Query("DELETE FROM Transacao WHERE uuid = :uuid AND perfil = :perfil")
    suspend fun deletarPorUuidEPerfil(uuid: String, perfil: Perfil)

    @Query("SELECT * FROM Transacao WHERE id = :id")
    suspend fun obterPorId(id: Long): Transacao?

    /** As duas pernas de uma transferência entre contextos. */
    @Query("SELECT * FROM Transacao WHERE transferenciaId = :transferenciaId")
    suspend fun listarPorTransferencia(transferenciaId: String): List<Transacao>

    // ---------- Listagens (Flow = atualiza a UI em tempo real) ----------

    @Query(
        """
        SELECT * FROM Transacao WHERE perfil = :perfil AND deletado = 0
        ORDER BY data DESC, id DESC
        """
    )
    fun observarTodas(perfil: Perfil): Flow<List<Transacao>>

    @Query(
        """
        SELECT * FROM Transacao
        WHERE perfil = :perfil AND deletado = 0 AND data BETWEEN :inicio AND :fim
        ORDER BY data DESC, id DESC
        """
    )
    fun observarPeriodo(perfil: Perfil, inicio: LocalDate, fim: LocalDate): Flow<List<Transacao>>

    @Query(
        """
        SELECT * FROM Transacao WHERE perfil = :perfil AND deletado = 0
        ORDER BY data DESC, id DESC LIMIT :limite
        """
    )
    fun observarUltimas(perfil: Perfil, limite: Int = 10): Flow<List<Transacao>>

    @Query(
        """
        SELECT * FROM Transacao
        WHERE perfil = :perfil AND deletado = 0 AND descricao LIKE '%' || :termo || '%'
        ORDER BY data DESC, id DESC
        """
    )
    fun buscar(perfil: Perfil, termo: String): Flow<List<Transacao>>

    // ---------- Agregações ----------

    /** Saldo total em centavos: soma de ganhos menos soma de gastos. */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN tipo = 'GANHO' THEN valor ELSE -valor END), 0)
        FROM Transacao WHERE perfil = :perfil AND deletado = 0
        """
    )
    fun observarSaldoTotal(perfil: Perfil): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(valor), 0) FROM Transacao
        WHERE perfil = :perfil AND deletado = 0
            AND tipo = :tipo AND data BETWEEN :inicio AND :fim
        """
    )
    fun observarSomaPorTipo(
        perfil: Perfil,
        tipo: TipoTransacao,
        inicio: LocalDate,
        fim: LocalDate
    ): Flow<Long>

    @Query(
        """
        SELECT categoria, COALESCE(SUM(valor), 0) AS total FROM Transacao
        WHERE perfil = :perfil AND deletado = 0
            AND tipo = :tipo AND data BETWEEN :inicio AND :fim
        GROUP BY categoria ORDER BY total DESC
        """
    )
    fun observarSomaPorCategoria(
        perfil: Perfil,
        tipo: TipoTransacao,
        inicio: LocalDate,
        fim: LocalDate
    ): Flow<List<SomaPorCategoria>>

    @Query(
        """
        SELECT COALESCE(SUM(valor), 0) FROM Transacao
        WHERE perfil = :perfil AND deletado = 0
            AND tipo = :tipo AND data BETWEEN :inicio AND :fim
        """
    )
    suspend fun somarPorTipo(
        perfil: Perfil,
        tipo: TipoTransacao,
        inicio: LocalDate,
        fim: LocalDate
    ): Long

    /** Mantém o histórico coerente quando uma categoria é renomeada. */
    @Query(
        """
        UPDATE Transacao SET categoria = :novoNome, atualizadoEm = :agora
        WHERE categoria = :nomeAntigo AND perfil = :perfil
        """
    )
    suspend fun renomearCategoria(
        perfil: Perfil,
        nomeAntigo: String,
        novoNome: String,
        agora: Long
    )
}
