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

    @Query(
        """
        SELECT * FROM TransacaoRecorrente
        WHERE perfil = :perfil AND deletado = 0 AND ativa = 1
        ORDER BY proximoLancamento
        """
    )
    fun observarAtivas(perfil: Perfil): Flow<List<TransacaoRecorrente>>

    @Query("SELECT * FROM TransacaoRecorrente WHERE perfil = :perfil AND deletado = 0 AND ativa = 1")
    suspend fun listarAtivas(perfil: Perfil): List<TransacaoRecorrente>

    @Query(
        """
        UPDATE TransacaoRecorrente SET categoria = :novoNome, atualizadoEm = :agora
        WHERE categoria = :nomeAntigo AND perfil = :perfil
        """
    )
    suspend fun renomearCategoria(
        perfil: Perfil,
        nomeAntigo: String,
        novoNome: String,
        agora: Long
    )

    /** Limpeza local (sair da casa) — NÃO usar em balde sincronizado ativo. */
    @Query("DELETE FROM TransacaoRecorrente WHERE perfil = :perfil")
    suspend fun deletarTodas(perfil: Perfil)

    /**
     * Recorrências que precisam MATERIALIZAR ocorrências (cursor dentro do
     * [horizonte]) OU CONFIRMAR um recebimento vencido (o cursor de
     * materialização fica meses à frente; sem a segunda condição o salário
     * nunca seria auto-confirmado).
     */
    @Query(
        """
        SELECT * FROM TransacaoRecorrente
        WHERE deletado = 0 AND ativa = 1
            AND (proximoLancamento <= :horizonte
                OR (proximaConfirmacao IS NOT NULL AND proximaConfirmacao <= :hoje))
        """
    )
    suspend fun obterVencidas(horizonte: LocalDate, hoje: LocalDate): List<TransacaoRecorrente>
}
