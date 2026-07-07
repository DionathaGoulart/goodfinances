package com.finapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.finapp.data.db.entities.Cartao
import com.finapp.data.db.entities.Perfil
import kotlinx.coroutines.flow.Flow

@Dao
interface CartaoDao {

    @Insert
    suspend fun inserir(cartao: Cartao): Long

    @Update
    suspend fun atualizar(cartao: Cartao)

    @Query("SELECT * FROM Cartao WHERE perfil = :perfil AND deletado = 0 ORDER BY nome")
    fun observarTodos(perfil: Perfil): Flow<List<Cartao>>

    @Query("SELECT * FROM Cartao WHERE perfil = :perfil AND deletado = 0 ORDER BY nome")
    suspend fun listarTodos(perfil: Perfil): List<Cartao>

    /** Limpeza local (sair da casa) — NÃO usar em balde sincronizado ativo. */
    @Query("DELETE FROM Cartao WHERE perfil = :perfil")
    suspend fun deletarTodos(perfil: Perfil)

    // ---------- Sincronização ----------

    @Query("SELECT * FROM Cartao WHERE uuid = :uuid")
    suspend fun obterPorUuid(uuid: String): Cartao?

    /** Sinal de mudança para o push (inclui tombstones). */
    @Query("SELECT COALESCE(MAX(atualizadoEm), 0) FROM Cartao WHERE perfil = :perfil")
    fun observarUltimaModificacao(perfil: Perfil): Flow<Long>

    /** Linhas modificadas desde a última marca de push (inclui tombstones). */
    @Query("SELECT * FROM Cartao WHERE perfil = :perfil AND atualizadoEm > :desde")
    suspend fun listarModificadas(perfil: Perfil, desde: Long): List<Cartao>
}
