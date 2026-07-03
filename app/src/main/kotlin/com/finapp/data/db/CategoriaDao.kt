package com.finapp.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.finapp.data.db.entities.Categoria
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoriaDao {

    @Insert
    suspend fun inserir(categoria: Categoria): Long

    @Insert
    suspend fun inserirTodas(categorias: List<Categoria>)

    @Update
    suspend fun atualizar(categoria: Categoria)

    @Delete
    suspend fun deletar(categoria: Categoria)

    @Query("SELECT * FROM Categoria WHERE perfil = :perfil AND deletado = 0 ORDER BY nome")
    fun observarTodas(perfil: Perfil): Flow<List<Categoria>>

    /** Categorias ativas de um tipo — usadas no dropdown de nova transação. */
    @Query(
        """
        SELECT * FROM Categoria
        WHERE perfil = :perfil AND deletado = 0 AND tipo = :tipo AND arquivada = 0
        ORDER BY nome
        """
    )
    fun observarAtivasPorTipo(perfil: Perfil, tipo: TipoTransacao): Flow<List<Categoria>>

    /** Conta TODAS (inclusive tombstones) — controla o seed único das padrão. */
    @Query("SELECT COUNT(*) FROM Categoria WHERE perfil = :perfil")
    suspend fun contar(perfil: Perfil): Int

    @Query("SELECT * FROM Categoria WHERE perfil = :perfil AND deletado = 0")
    suspend fun listarTodas(perfil: Perfil): List<Categoria>

    /** Todos os uuids do perfil, INCLUSIVE tombstones — dedup de importação. */
    @Query("SELECT uuid FROM Categoria WHERE perfil = :perfil")
    suspend fun listarUuids(perfil: Perfil): List<String>

    // ---------- Sincronização (Casa) ----------

    @Query("SELECT * FROM Categoria WHERE uuid = :uuid")
    suspend fun obterPorUuid(uuid: String): Categoria?

    /** Sinal de mudança para o push (inclui tombstones). */
    @Query("SELECT COALESCE(MAX(atualizadoEm), 0) FROM Categoria WHERE perfil = :perfil")
    fun observarUltimaModificacao(perfil: Perfil): Flow<Long>

    /** Linhas modificadas desde a última marca de push (inclui tombstones). */
    @Query("SELECT * FROM Categoria WHERE perfil = :perfil AND atualizadoEm > :desde")
    suspend fun listarModificadas(perfil: Perfil, desde: Long): List<Categoria>
}
