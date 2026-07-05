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

/**
 * Preferências de aparência (tamanho de fonte e cores do tema), persistidas.
 * O tema tem duas cores: uma para o contexto pessoal e outra para o de
 * empresa — a MainActivity escolhe pela `PerfilManager.perfilDados`.
 */
@Singleton
class AparenciaManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("finapp_prefs", Context.MODE_PRIVATE)

    private val _escalaFonte = MutableStateFlow(
        lerEnum(CHAVE_FONTE, EscalaFonte.NORMAL, EscalaFonte::valueOf)
    )
    val escalaFonte: StateFlow<EscalaFonte> = _escalaFonte.asStateFlow()

    // CHAVE_COR_PESSOAL lê a chave antiga "cor_primaria" — preferência
    // de quem já usava o app migra para o tema pessoal sem perder nada.
    private val _corPessoal = MutableStateFlow(
        lerEnum(CHAVE_COR_PESSOAL, CorApp.VERDE, CorApp::valueOf)
    )
    val corPessoal: StateFlow<CorApp> = _corPessoal.asStateFlow()

    private val _corEmpresa = MutableStateFlow(
        lerEnum(CHAVE_COR_EMPRESA, CorApp.AZUL, CorApp::valueOf)
    )
    val corEmpresa: StateFlow<CorApp> = _corEmpresa.asStateFlow()

    fun definirEscalaFonte(escala: EscalaFonte) {
        prefs.edit { putString(CHAVE_FONTE, escala.name) }
        _escalaFonte.value = escala
    }

    fun definirCorPessoal(cor: CorApp) {
        prefs.edit { putString(CHAVE_COR_PESSOAL, cor.name) }
        _corPessoal.value = cor
    }

    fun definirCorEmpresa(cor: CorApp) {
        prefs.edit { putString(CHAVE_COR_EMPRESA, cor.name) }
        _corEmpresa.value = cor
    }

    private fun <T> lerEnum(chave: String, padrao: T, converter: (String) -> T): T {
        val salvo = prefs.getString(chave, null) ?: return padrao
        return runCatching { converter(salvo) }.getOrDefault(padrao)
    }

    private companion object {
        const val CHAVE_FONTE = "escala_fonte"
        const val CHAVE_COR_PESSOAL = "cor_primaria"
        const val CHAVE_COR_EMPRESA = "cor_empresa"
    }
}
