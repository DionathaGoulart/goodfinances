package com.finapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.finapp.data.db.entities.ConfiguracaoPerfil
import com.finapp.data.db.entities.Perfil
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfiguracaoPerfilDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(configuracao: ConfiguracaoPerfil)

    @Query("SELECT * FROM ConfiguracaoPerfil WHERE perfil = :perfil")
    fun observar(perfil: Perfil): Flow<ConfiguracaoPerfil?>

    @Query("SELECT * FROM ConfiguracaoPerfil WHERE perfil = :perfil")
    suspend fun obter(perfil: Perfil): ConfiguracaoPerfil?
}
