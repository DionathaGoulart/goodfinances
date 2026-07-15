package com.finapp.data.notif

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.finapp.data.OnibusManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Job que avalia os gatilhos financeiros e dispara as notificações — roda em
 * três rodadas diárias (manhã, tarde e noite).
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

        fun onibusManager(): OnibusManager
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            NotificacaoEntryPoint::class.java
        )
        // Passagens de ônibus dos dias de rotina: garante o desconto mesmo
        // sem abrir o app (idempotente — cursor + flags do dia)
        runCatching { entryPoint.onibusManager().processarDescontosAutomaticos() }
        return runCatching { entryPoint.notificacaoManager().avaliar() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val TRABALHO_DIARIO_LEGADO = "notificacoes_diarias"

        /** Rodadas do dia: os lembretes de vencimento saem em cada uma. */
        private val RODADAS = listOf("manha" to 8, "tarde" to 13, "noite" to 19)

        /**
         * Agenda as três rodadas diárias (idempotente — mantém as existentes).
         * Cada rodada é um trabalho periódico de 24h ancorado na primeira
         * ocorrência do horário; a dedup por faixa do dia nas prefs absorve
         * o deslize de horário que o WorkManager possa introduzir.
         */
        fun agendar(context: Context) {
            val workManager = WorkManager.getInstance(context)
            // Agendamento antigo de rodada única — substituído pelas rodadas
            workManager.cancelUniqueWork(TRABALHO_DIARIO_LEGADO)
            RODADAS.forEach { (nome, hora) ->
                val requisicao = PeriodicWorkRequestBuilder<NotificacaoWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(minutosAte(hora), TimeUnit.MINUTES)
                    .build()
                workManager.enqueueUniquePeriodicWork(
                    "notificacoes_$nome",
                    ExistingPeriodicWorkPolicy.KEEP,
                    requisicao
                )
            }
        }

        /** Minutos até a próxima ocorrência de [hora] em ponto (hoje ou amanhã). */
        private fun minutosAte(hora: Int): Long {
            val agora = LocalDateTime.now()
            var alvo = agora.toLocalDate().atTime(hora, 0)
            if (!alvo.isAfter(agora)) alvo = alvo.plusDays(1)
            return ChronoUnit.MINUTES.between(agora, alvo)
        }
    }
}
