package com.finapp.data

import android.content.Context
import androidx.core.content.edit
import com.finapp.data.db.entities.ContextoMei
import com.finapp.data.db.entities.Perfil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guarda o perfil ativo (e a aba do MEI) em SharedPreferences e os expõe
 * como StateFlow, para que todas as telas reajam à troca em tempo real.
 *
 * [perfilAtivo] é o que o usuário escolheu (Pessoa Física / MEI / CNPJ).
 * [perfilDados] é o "balde" de dados efetivo das queries: para o MEI, vira
 * MEI_PESSOAL ou MEI_NEGOCIO conforme a aba ativa; para os demais, é o próprio.
 */
@Singleton
class PerfilManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _perfilAtivo = MutableStateFlow(lerPerfil())
    val perfilAtivo: StateFlow<Perfil> = _perfilAtivo.asStateFlow()

    private val _contextoMei = MutableStateFlow(lerContextoMei())
    val contextoMei: StateFlow<ContextoMei> = _contextoMei.asStateFlow()

    private val _perfilDados = MutableStateFlow(calcularPerfilDados())
    val perfilDados: StateFlow<Perfil> = _perfilDados.asStateFlow()

    private val _perfilFoiEscolhido = MutableStateFlow(prefs.contains(CHAVE_PERFIL))
    /** False apenas na primeira abertura — dispara a tela de seleção de perfil. */
    val perfilFoiEscolhido: StateFlow<Boolean> = _perfilFoiEscolhido.asStateFlow()

    fun mudarPerfil(perfil: Perfil) {
        require(perfil in Perfil.PRINCIPAIS) { "Perfil ${perfil.name} não é selecionável" }
        prefs.edit { putString(CHAVE_PERFIL, perfil.name) }
        _perfilAtivo.value = perfil
        _perfilFoiEscolhido.value = true
        _perfilDados.value = calcularPerfilDados()
    }

    fun mudarContextoMei(contexto: ContextoMei) {
        prefs.edit { putString(CHAVE_CONTEXTO_MEI, contexto.name) }
        _contextoMei.value = contexto
        _perfilDados.value = calcularPerfilDados()
    }

    private fun calcularPerfilDados(): Perfil =
        if (_perfilAtivo.value == Perfil.MEI) {
            when (_contextoMei.value) {
                ContextoMei.PESSOAL -> Perfil.MEI_PESSOAL
                ContextoMei.NEGOCIO -> Perfil.MEI_NEGOCIO
            }
        } else {
            _perfilAtivo.value
        }

    private fun lerPerfil(): Perfil {
        val salvo = prefs.getString(CHAVE_PERFIL, null) ?: return Perfil.PESSOA_FISICA
        return runCatching { Perfil.valueOf(salvo) }.getOrDefault(Perfil.PESSOA_FISICA)
    }

    private fun lerContextoMei(): ContextoMei {
        val salvo = prefs.getString(CHAVE_CONTEXTO_MEI, null) ?: return ContextoMei.PESSOAL
        return runCatching { ContextoMei.valueOf(salvo) }.getOrDefault(ContextoMei.PESSOAL)
    }

    private companion object {
        const val PREFS = "finapp_prefs"
        const val CHAVE_PERFIL = "perfil_ativo"
        const val CHAVE_CONTEXTO_MEI = "contexto_mei"
    }
}
