package com.finapp.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TransacaoRecorrente
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface TransacaoRecorrenteDao {

    @Insert
    suspend fun inserir(recorrente: TransacaoRecorrente): Long

    @Update
    suspend fun atualizar(recorrente: TransacaoRecorrente)

    @Delete
    suspend fun deletar(recorrente: TransacaoRecorrente)

    @Query("SELECT * FROM TransacaoRecorrente WHERE perfil = :perfil AND ativa = 1 ORDER BY proximoLancamento")
    fun observarAtivas(perfil: Perfil): Flow<List<TransacaoRecorrente>>

    @Query("SELECT * FROM TransacaoRecorrente WHERE perfil = :perfil AND ativa = 1")
    suspend fun listarAtivas(perfil: Perfil): List<TransacaoRecorrente>

    @Query(
        """
        UPDATE TransacaoRecorrente SET categoria = :novoNome
        WHERE categoria = :nomeAntigo AND perfil = :perfil
        """
    )
    suspend fun renomearCategoria(perfil: Perfil, nomeAntigo: String, novoNome: String)

    /** Recorrências vencidas — devem gerar transações ao abrir o app. */
    @Query("SELECT * FROM TransacaoRecorrente WHERE ativa = 1 AND proximoLancamento <= :hoje")
    suspend fun obterVencidas(hoje: LocalDate): List<TransacaoRecorrente>
}
