package com.finapp

import android.app.Application
import com.finapp.data.notif.NotificacaoManager
import com.finapp.data.notif.NotificacaoWorker
import com.finapp.data.sync.SyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FinanApplication : Application() {

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var notificacaoManager: NotificacaoManager

    override fun onCreate() {
        super.onCreate()
        // Sincronização da Casa: liga sozinho quando o usuário está numa casa
        syncManager.iniciar()
        // Notificações locais: cria os canais e agenda a avaliação diária
        notificacaoManager.garantirCanais()
        NotificacaoWorker.agendar(this)
    }
}
