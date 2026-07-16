package com.finapp.di

import android.content.Context
import androidx.room.Room
import com.finapp.data.db.AppDatabase
import com.finapp.data.db.CartaoDao
import com.finapp.data.db.CategoriaDao
import com.finapp.data.db.ConfiguracaoPerfilDao
import com.finapp.data.db.ContaAgendadaDao
import com.finapp.data.db.MetaDao
import com.finapp.data.db.TransacaoDao
import com.finapp.data.db.TransacaoRecorrenteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NOME)
            .addMigrations(
                AppDatabase.MIGRACAO_1_2,
                AppDatabase.MIGRACAO_2_3,
                AppDatabase.MIGRACAO_3_4,
                AppDatabase.MIGRACAO_4_5,
                AppDatabase.MIGRACAO_5_6,
                AppDatabase.MIGRACAO_6_7,
                AppDatabase.MIGRACAO_7_8,
                AppDatabase.MIGRACAO_8_9,
                AppDatabase.MIGRACAO_9_10,
                AppDatabase.MIGRACAO_10_11,
                AppDatabase.MIGRACAO_11_12,
                AppDatabase.MIGRACAO_12_13,
                AppDatabase.MIGRACAO_13_14,
                AppDatabase.MIGRACAO_14_15,
                AppDatabase.MIGRACAO_15_16
            )
            .build()

    @Provides
    fun provideTransacaoDao(db: AppDatabase): TransacaoDao = db.transacaoDao()

    @Provides
    fun provideCategoriaDao(db: AppDatabase): CategoriaDao = db.categoriaDao()

    @Provides
    fun provideConfiguracaoPerfilDao(db: AppDatabase): ConfiguracaoPerfilDao =
        db.configuracaoPerfilDao()

    @Provides
    fun provideTransacaoRecorrenteDao(db: AppDatabase): TransacaoRecorrenteDao =
        db.transacaoRecorrenteDao()

    @Provides
    fun provideCartaoDao(db: AppDatabase): CartaoDao = db.cartaoDao()

    @Provides
    fun provideMetaDao(db: AppDatabase): MetaDao = db.metaDao()

    @Provides
    fun provideContaAgendadaDao(db: AppDatabase): ContaAgendadaDao = db.contaAgendadaDao()
}
