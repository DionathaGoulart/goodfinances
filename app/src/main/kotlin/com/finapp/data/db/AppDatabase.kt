package com.finapp.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.finapp.data.db.entities.Categoria
import com.finapp.data.db.entities.ConfiguracaoPerfil
import com.finapp.data.db.entities.Transacao
import com.finapp.data.db.entities.TransacaoRecorrente

@Database(
    entities = [
        Transacao::class,
        Categoria::class,
        ConfiguracaoPerfil::class,
        TransacaoRecorrente::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transacaoDao(): TransacaoDao
    abstract fun categoriaDao(): CategoriaDao
    abstract fun configuracaoPerfilDao(): ConfiguracaoPerfilDao
    abstract fun transacaoRecorrenteDao(): TransacaoRecorrenteDao

    companion object {
        const val NOME = "finapp.db"

        /** v1 -> v2: valores monetários passam de REAL (reais) para INTEGER (centavos). */
        val MIGRACAO_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Transacao
                db.execSQL(
                    "CREATE TABLE Transacao_v2 (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "valor INTEGER NOT NULL, tipo TEXT NOT NULL, " +
                        "categoria TEXT NOT NULL, descricao TEXT NOT NULL, " +
                        "data INTEGER NOT NULL, perfil TEXT NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO Transacao_v2 (id, valor, tipo, categoria, descricao, data, perfil) " +
                        "SELECT id, CAST(ROUND(valor * 100) AS INTEGER), tipo, categoria, " +
                        "descricao, data, perfil FROM Transacao"
                )
                db.execSQL("DROP TABLE Transacao")
                db.execSQL("ALTER TABLE Transacao_v2 RENAME TO Transacao")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_Transacao_perfil_data " +
                        "ON Transacao (perfil, data)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_Transacao_perfil_tipo " +
                        "ON Transacao (perfil, tipo)"
                )

                // ConfiguracaoPerfil
                db.execSQL(
                    "CREATE TABLE ConfiguracaoPerfil_v2 (" +
                        "perfil TEXT NOT NULL, salarioFixo INTEGER NOT NULL, " +
                        "diaRecebimento INTEGER NOT NULL, PRIMARY KEY(perfil))"
                )
                db.execSQL(
                    "INSERT INTO ConfiguracaoPerfil_v2 (perfil, salarioFixo, diaRecebimento) " +
                        "SELECT perfil, CAST(ROUND(salarioFixo * 100) AS INTEGER), " +
                        "diaRecebimento FROM ConfiguracaoPerfil"
                )
                db.execSQL("DROP TABLE ConfiguracaoPerfil")
                db.execSQL("ALTER TABLE ConfiguracaoPerfil_v2 RENAME TO ConfiguracaoPerfil")

                // TransacaoRecorrente
                db.execSQL(
                    "CREATE TABLE TransacaoRecorrente_v2 (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "valor INTEGER NOT NULL, tipo TEXT NOT NULL, " +
                        "categoria TEXT NOT NULL, descricao TEXT NOT NULL, " +
                        "frequencia TEXT NOT NULL, proximoLancamento INTEGER NOT NULL, " +
                        "perfil TEXT NOT NULL, ativa INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO TransacaoRecorrente_v2 (id, valor, tipo, categoria, descricao, " +
                        "frequencia, proximoLancamento, perfil, ativa) " +
                        "SELECT id, CAST(ROUND(valor * 100) AS INTEGER), tipo, categoria, " +
                        "descricao, frequencia, proximoLancamento, perfil, ativa " +
                        "FROM TransacaoRecorrente"
                )
                db.execSQL("DROP TABLE TransacaoRecorrente")
                db.execSQL("ALTER TABLE TransacaoRecorrente_v2 RENAME TO TransacaoRecorrente")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "index_TransacaoRecorrente_perfil_ativa_proximoLancamento " +
                        "ON TransacaoRecorrente (perfil, ativa, proximoLancamento)"
                )
            }
        }
    }
}
