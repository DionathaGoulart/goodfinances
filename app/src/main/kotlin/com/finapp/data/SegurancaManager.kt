package com.finapp.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferência de segurança: exigir biometria (ou PIN do aparelho) ao abrir.
 * A trava em si fica na MainActivity, via BiometricPrompt.
 */
@Singleton
class SegurancaManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("finapp_prefs", Context.MODE_PRIVATE)

    private val _bloqueioAtivado = MutableStateFlow(prefs.getBoolean(CHAVE_BLOQUEIO, false))
    val bloqueioAtivado: StateFlow<Boolean> = _bloqueioAtivado.asStateFlow()

    fun alternarBloqueio(ativo: Boolean) {
        prefs.edit { putBoolean(CHAVE_BLOQUEIO, ativo) }
        _bloqueioAtivado.value = ativo
    }

    private companion object {
        const val CHAVE_BLOQUEIO = "bloqueio_biometrico"
    }
}
