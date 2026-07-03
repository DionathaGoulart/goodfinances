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

    @Query("SELECT * FROM Categoria WHERE perfil = :perfil ORDER BY nome")
    fun observarTodas(perfil: Perfil): Flow<List<Categoria>>

    /** Categorias ativas de um tipo — usadas no dropdown de nova transação. */
    @Query(
        """
        SELECT * FROM Categoria
        WHERE perfil = :perfil AND tipo = :tipo AND arquivada = 0
        ORDER BY nome
        """
    )
    fun observarAtivasPorTipo(perfil: Perfil, tipo: TipoTransacao): Flow<List<Categoria>>

    @Query("SELECT COUNT(*) FROM Categoria WHERE perfil = :perfil")
    suspend fun contar(perfil: Perfil): Int

    @Query("SELECT * FROM Categoria WHERE perfil = :perfil")
    suspend fun listarTodas(perfil: Perfil): List<Categoria>
}
