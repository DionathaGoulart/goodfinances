package com.finapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.finapp.data.db.entities.Meta
import com.finapp.data.db.entities.Perfil
import kotlinx.coroutines.flow.Flow

@Dao
interface MetaDao {

    @Insert
    suspend fun inserir(meta: Meta): Long

    @Update
    suspend fun atualizar(meta: Meta)

    @Query("SELECT * FROM Meta WHERE perfil = :perfil AND deletado = 0 ORDER BY id DESC")
    fun observarTodas(perfil: Perfil): Flow<List<Meta>>

    @Query("SELECT * FROM Meta WHERE perfil = :perfil AND deletado = 0 ORDER BY id DESC")
    suspend fun listarTodas(perfil: Perfil): List<Meta>

    @Query("SELECT * FROM Meta WHERE id = :id")
    suspend fun obterPorId(id: Long): Meta?

    // ---------- Sincronização (Casa / pessoal) ----------

    @Query("SELECT * FROM Meta WHERE uuid = :uuid")
    suspend fun obterPorUuid(uuid: String): Meta?

    /** Sinal de mudança para o push (inclui tombstones). */
    @Query("SELECT COALESCE(MAX(atualizadoEm), 0) FROM Meta WHERE perfil = :perfil")
    fun observarUltimaModificacao(perfil: Perfil): Flow<Long>

    /** Linhas modificadas desde a última marca de push (inclui tombstones). */
    @Query("SELECT * FROM Meta WHERE perfil = :perfil AND atualizadoEm > :desde")
    suspend fun listarModificadas(perfil: Perfil, desde: Long): List<Meta>

    /** Limpeza que propaga no sync: marca tudo como tombstone. */
    @Query("UPDATE Meta SET deletado = 1, atualizadoEm = :agora WHERE perfil = :perfil AND deletado = 0")
    suspend fun marcarTodasDeletadas(perfil: Perfil, agora: Long)

    /** Limpeza local (sair da casa) — delete físico. */
    @Query("DELETE FROM Meta WHERE perfil = :perfil")
    suspend fun deletarTodas(perfil: Perfil)
}
