package com.finapp.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.finapp.data.db.entities.Cartao
import com.finapp.data.db.entities.Categoria
import com.finapp.data.db.entities.ConfiguracaoPerfil
import com.finapp.data.db.entities.ContaAgendada
import com.finapp.data.db.entities.Meta
import com.finapp.data.db.entities.Transacao
import com.finapp.data.db.entities.TransacaoRecorrente

@Database(
    entities = [
        Transacao::class,
        Categoria::class,
        ConfiguracaoPerfil::class,
        TransacaoRecorrente::class,
        Cartao::class,
        Meta::class,
        ContaAgendada::class
    ],
    version = 16,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transacaoDao(): TransacaoDao
    abstract fun categoriaDao(): CategoriaDao
    abstract fun configuracaoPerfilDao(): ConfiguracaoPerfilDao
    abstract fun transacaoRecorrenteDao(): TransacaoRecorrenteDao
    abstract fun cartaoDao(): CartaoDao
    abstract fun metaDao(): MetaDao
    abstract fun contaAgendadaDao(): ContaAgendadaDao

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

        /**
         * v2 -> v3: prepara o modelo para sincronização entre aparelhos:
         * uuid (identidade global), atualizadoEm (última edição vence) e
         * deletado (tombstone). Uuids das linhas existentes são gerados
         * com randomblob — únicos o suficiente para identidade global.
         */
        val MIGRACAO_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val agora = System.currentTimeMillis()

                // Transacao
                db.execSQL(
                    "CREATE TABLE Transacao_v3 (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "uuid TEXT NOT NULL, valor INTEGER NOT NULL, tipo TEXT NOT NULL, " +
                        "categoria TEXT NOT NULL, descricao TEXT NOT NULL, " +
                        "data INTEGER NOT NULL, perfil TEXT NOT NULL, " +
                        "atualizadoEm INTEGER NOT NULL, deletado INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO Transacao_v3 (id, uuid, valor, tipo, categoria, descricao, " +
                        "data, perfil, atualizadoEm, deletado) " +
                        "SELECT id, lower(hex(randomblob(16))), valor, tipo, categoria, " +
                        "descricao, data, perfil, $agora, 0 FROM Transacao"
                )
                db.execSQL("DROP TABLE Transacao")
                db.execSQL("ALTER TABLE Transacao_v3 RENAME TO Transacao")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_Transacao_perfil_data " +
                        "ON Transacao (perfil, data)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_Transacao_perfil_tipo " +
                        "ON Transacao (perfil, tipo)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_Transacao_uuid " +
                        "ON Transacao (uuid)"
                )

                // Categoria
                db.execSQL(
                    "CREATE TABLE Categoria_v3 (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "uuid TEXT NOT NULL, nome TEXT NOT NULL, tipo TEXT NOT NULL, " +
                        "cor TEXT NOT NULL, perfil TEXT NOT NULL, arquivada INTEGER NOT NULL, " +
                        "atualizadoEm INTEGER NOT NULL, deletado INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO Categoria_v3 (id, uuid, nome, tipo, cor, perfil, arquivada, " +
                        "atualizadoEm, deletado) " +
                        "SELECT id, lower(hex(randomblob(16))), nome, tipo, cor, perfil, " +
                        "arquivada, $agora, 0 FROM Categoria"
                )
                db.execSQL("DROP TABLE Categoria")
                db.execSQL("ALTER TABLE Categoria_v3 RENAME TO Categoria")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_Categoria_perfil_tipo_arquivada " +
                        "ON Categoria (perfil, tipo, arquivada)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_Categoria_uuid " +
                        "ON Categoria (uuid)"
                )

                // TransacaoRecorrente
                db.execSQL(
                    "CREATE TABLE TransacaoRecorrente_v3 (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "uuid TEXT NOT NULL, valor INTEGER NOT NULL, tipo TEXT NOT NULL, " +
                        "categoria TEXT NOT NULL, descricao TEXT NOT NULL, " +
                        "frequencia TEXT NOT NULL, proximoLancamento INTEGER NOT NULL, " +
                        "perfil TEXT NOT NULL, ativa INTEGER NOT NULL, " +
                        "atualizadoEm INTEGER NOT NULL, deletado INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO TransacaoRecorrente_v3 (id, uuid, valor, tipo, categoria, " +
                        "descricao, frequencia, proximoLancamento, perfil, ativa, " +
                        "atualizadoEm, deletado) " +
                        "SELECT id, lower(hex(randomblob(16))), valor, tipo, categoria, " +
                        "descricao, frequencia, proximoLancamento, perfil, ativa, $agora, 0 " +
                        "FROM TransacaoRecorrente"
                )
                db.execSQL("DROP TABLE TransacaoRecorrente")
                db.execSQL("ALTER TABLE TransacaoRecorrente_v3 RENAME TO TransacaoRecorrente")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "index_TransacaoRecorrente_perfil_ativa_proximoLancamento " +
                        "ON TransacaoRecorrente (perfil, ativa, proximoLancamento)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_TransacaoRecorrente_uuid " +
                        "ON TransacaoRecorrente (uuid)"
                )
            }
        }

        /** v3 -> v4: Transacao ganha criadoPor (quem lançou, no perfil Casa). */
        val MIGRACAO_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE Transacao_v4 (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "uuid TEXT NOT NULL, valor INTEGER NOT NULL, tipo TEXT NOT NULL, " +
                        "categoria TEXT NOT NULL, descricao TEXT NOT NULL, " +
                        "data INTEGER NOT NULL, perfil TEXT NOT NULL, " +
                        "atualizadoEm INTEGER NOT NULL, deletado INTEGER NOT NULL, " +
                        "criadoPor TEXT NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO Transacao_v4 (id, uuid, valor, tipo, categoria, descricao, " +
                        "data, perfil, atualizadoEm, deletado, criadoPor) " +
                        "SELECT id, uuid, valor, tipo, categoria, descricao, data, perfil, " +
                        "atualizadoEm, deletado, '' FROM Transacao"
                )
                db.execSQL("DROP TABLE Transacao")
                db.execSQL("ALTER TABLE Transacao_v4 RENAME TO Transacao")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_Transacao_perfil_data " +
                        "ON Transacao (perfil, data)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_Transacao_perfil_tipo " +
                        "ON Transacao (perfil, tipo)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_Transacao_uuid " +
                        "ON Transacao (uuid)"
                )
            }
        }

        /** v4 -> v5: Transacao ganha notaFiscal (arquivo anexado, contextos de empresa). */
        val MIGRACAO_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE Transacao ADD COLUMN notaFiscal TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /** v5 -> v6: Categoria ganha orcamentoMensal (teto de gasto, em centavos). */
        val MIGRACAO_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE Categoria ADD COLUMN orcamentoMensal INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** v6 -> v7: Transacao ganha criadoPorUid (autoria da Casa à prova de nome). */
        val MIGRACAO_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE Transacao ADD COLUMN criadoPorUid TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /** v7 -> v8: Transacao ganha transferenciaId (vincula as pernas da transferência). */
        val MIGRACAO_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE Transacao ADD COLUMN transferenciaId TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /** v8 -> v9: Transacao ganha oculto (esconder da visão Membros da casa). */
        val MIGRACAO_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE Transacao ADD COLUMN oculto INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * v9 -> v10: cartões de crédito. Nova tabela Cartao e, na Transacao,
         * cartaoUuid (vínculo com o cartão) + dataCompra (dia da compra;
         * a `data` passa a ser o vencimento da fatura no crédito).
         */
        val MIGRACAO_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS Cartao (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "uuid TEXT NOT NULL, nome TEXT NOT NULL, " +
                        "diaFechamento INTEGER NOT NULL, diaVencimento INTEGER NOT NULL, " +
                        "cor TEXT NOT NULL, perfil TEXT NOT NULL, " +
                        "atualizadoEm INTEGER NOT NULL, deletado INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_Cartao_perfil ON Cartao (perfil)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_Cartao_uuid ON Cartao (uuid)"
                )
                db.execSQL(
                    "ALTER TABLE Transacao ADD COLUMN cartaoUuid TEXT NOT NULL DEFAULT ''"
                )
                // dataCompra é nullable (epoch day) — sem DEFAULT: fica NULL nas antigas
                db.execSQL("ALTER TABLE Transacao ADD COLUMN dataCompra INTEGER")
            }
        }

        /**
         * v10 -> v11: metas de economia (tabela Meta) e contas a pagar/receber
         * agendadas (tabela ContaAgendada). Ambas isoladas por perfil, com
         * uuid/atualizadoEm/deletado no mesmo padrão das demais entidades.
         */
        val MIGRACAO_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS Meta (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "uuid TEXT NOT NULL, nome TEXT NOT NULL, " +
                        "valorAlvo INTEGER NOT NULL, valorGuardado INTEGER NOT NULL, " +
                        "prazo INTEGER, cor TEXT NOT NULL, perfil TEXT NOT NULL, " +
                        "atualizadoEm INTEGER NOT NULL, deletado INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_Meta_perfil ON Meta (perfil)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_Meta_uuid ON Meta (uuid)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS ContaAgendada (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "uuid TEXT NOT NULL, descricao TEXT NOT NULL, " +
                        "valor INTEGER NOT NULL, tipo TEXT NOT NULL, " +
                        "categoria TEXT NOT NULL, vencimento INTEGER NOT NULL, " +
                        "pago INTEGER NOT NULL, perfil TEXT NOT NULL, " +
                        "atualizadoEm INTEGER NOT NULL, deletado INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_ContaAgendada_perfil_pago " +
                        "ON ContaAgendada (perfil, pago)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_ContaAgendada_uuid " +
                        "ON ContaAgendada (uuid)"
                )
            }
        }

        /**
         * v11 -> v12: Transacao ganha pago (false = pendente, não conta no
         * saldo até pagar). Linhas existentes ficam pagas, EXCETO compras no
         * crédito de fatura ainda não vencida — essas viram pendentes, que é
         * o estado que passam a ter no novo modelo.
         */
        val MIGRACAO_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE Transacao ADD COLUMN pago INTEGER NOT NULL DEFAULT 1"
                )
                val hoje = java.time.LocalDate.now().toEpochDay()
                db.execSQL(
                    "UPDATE Transacao SET pago = 0 " +
                        "WHERE cartaoUuid != '' AND deletado = 0 AND data >= $hoje"
                )
            }
        }

        /**
         * v12 -> v13: TransacaoRecorrente ganha diaMensal (dia do mês desejado
         * da recorrência MENSAL). Sem ele, o reagendamento encadeado com
         * plusMonths trunca em fevereiro (31 -> 28) e a recorrência deriva
         * para o dia 28 para sempre. Backfill: dia do lançamento agendado
         * (proximoLancamento é epoch day; *86400 vira unix epoch em segundos).
         */
        val MIGRACAO_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE TransacaoRecorrente " +
                        "ADD COLUMN diaMensal INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE TransacaoRecorrente SET diaMensal = " +
                        "CAST(strftime('%d', proximoLancamento * 86400, 'unixepoch') AS INTEGER) " +
                        "WHERE frequencia = 'MENSAL'"
                )
            }
        }

        /**
         * v13 -> v14: Cartao ganha origemUuid (uuid do cartão pessoal de
         * origem quando a linha é o espelho dele na Casa; "" = nativo).
         */
        val MIGRACAO_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE Cartao ADD COLUMN origemUuid TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * v14 -> v15: recorrências materializadas meses à frente.
         * Transacao.recorrenciaUuid vincula ocorrência -> recorrência (edição/
         * encerramento propagam só nas não pagas); TransacaoRecorrente ganha
         * terminaEm ("dura até", epoch day, nullable) e proximaConfirmacao
         * (auto-recebimento do GANHO mensal, nullable — backfill com o cursor
         * atual para o salário existente não pular os meses materializados).
         */
        val MIGRACAO_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE Transacao ADD COLUMN recorrenciaUuid TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_Transacao_recorrenciaUuid " +
                        "ON Transacao (recorrenciaUuid)"
                )
                db.execSQL("ALTER TABLE TransacaoRecorrente ADD COLUMN terminaEm INTEGER")
                db.execSQL(
                    "ALTER TABLE TransacaoRecorrente ADD COLUMN proximaConfirmacao INTEGER"
                )
                db.execSQL(
                    "UPDATE TransacaoRecorrente SET proximaConfirmacao = proximoLancamento " +
                        "WHERE frequencia = 'MENSAL' AND tipo = 'GANHO' " +
                        "AND ativa = 1 AND deletado = 0"
                )
            }
        }

        /**
         * v15 -> v16: Transacao.dataPagamento (epoch day, nullable) — o dia
         * em que a pendência foi paga de fato; o histórico agrupa por ele.
         * Sem backfill: nas linhas antigas fica NULL e a exibição cai na
         * própria `data`.
         */
        val MIGRACAO_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Transacao ADD COLUMN dataPagamento INTEGER")
            }
        }
    }
}
