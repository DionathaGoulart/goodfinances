package com.finapp.data

import android.content.Context
import androidx.core.content.edit
import com.finapp.utils.CorApp
import com.finapp.utils.EscalaFonte
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Preferências de aparência (tamanho de fonte e cor primária), persistidas. */
@Singleton
class AparenciaManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("finapp_prefs", Context.MODE_PRIVATE)

    private val _escalaFonte = MutableStateFlow(
        lerEnum(CHAVE_FONTE, EscalaFonte.NORMAL, EscalaFonte::valueOf)
    )
    val escalaFonte: StateFlow<EscalaFonte> = _escalaFonte.asStateFlow()

    private val _corPrimaria = MutableStateFlow(
        lerEnum(CHAVE_COR, CorApp.VERDE, CorApp::valueOf)
    )
    val corPrimaria: StateFlow<CorApp> = _corPrimaria.asStateFlow()

    fun definirEscalaFonte(escala: EscalaFonte) {
        prefs.edit { putString(CHAVE_FONTE, escala.name) }
        _escalaFonte.value = escala
    }

    fun definirCorPrimaria(cor: CorApp) {
        prefs.edit { putString(CHAVE_COR, cor.name) }
        _corPrimaria.value = cor
    }

    private fun <T> lerEnum(chave: String, padrao: T, converter: (String) -> T): T {
        val salvo = prefs.getString(chave, null) ?: return padrao
        return runCatching { converter(salvo) }.getOrDefault(padrao)
    }

    private companion object {
        const val CHAVE_FONTE = "escala_fonte"
        const val CHAVE_COR = "cor_primaria"
    }
}
