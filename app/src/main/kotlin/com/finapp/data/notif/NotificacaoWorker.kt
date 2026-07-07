package com.finapp.data.notif

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Job diário que avalia os gatilhos financeiros e dispara as notificações.
 * Usa um Hilt EntryPoint para obter o [NotificacaoManager] (evita depender do
 * HiltWorkerFactory e de mudar a inicialização do WorkManager).
 */
class NotificacaoWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface NotificacaoEntryPoint {
        fun notificacaoManager(): NotificacaoManager
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            NotificacaoEntryPoint::class.java
        )
        return runCatching { entryPoint.notificacaoManager().avaliar() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val TRABALHO_DIARIO = "notificacoes_diarias"

        /** Agenda a avaliação diária (idempotente — mantém a existente). */
        fun agendar(context: Context) {
            val requisicao = PeriodicWorkRequestBuilder<NotificacaoWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TRABALHO_DIARIO,
                ExistingPeriodicWorkPolicy.KEEP,
                requisicao
            )
        }
    }
}
