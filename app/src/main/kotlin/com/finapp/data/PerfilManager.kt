package com.finapp.data

import android.content.Context
import androidx.core.content.edit
import com.finapp.data.db.entities.ModoUso
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoEmpresa
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guarda o modo de uso e o contexto ativo em SharedPreferences e os expõe
 * como StateFlow, para que todas as telas reajam à troca em tempo real.
 *
 * [perfilAtivo] é o modo de uso escolhido (Pessoal / Pessoal+Empresa / Empresa).
 * [contextosDisponiveis] são as abas da Home: os baldes do modo + CASA
 * quando o usuário está numa casa (o CasaManager informa via [definirTemCasa]).
 * [perfilDados] é o contexto/balde ativo — todas as queries derivam dele.
 */
@Singleton
class PerfilManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _perfilAtivo = MutableStateFlow(lerPerfil())
    val perfilAtivo: StateFlow<Perfil> = _perfilAtivo.asStateFlow()

    // "casa_id" é gravada pelo CasaManager — presença = usuário está numa casa
    private var temCasa = prefs.contains(CHAVE_CASA_ID)

    private val _contextosDisponiveis = MutableStateFlow(calcularContextos())
    /** Abas da Home (ex: Pessoal | Empresa | Casa). */
    val contextosDisponiveis: StateFlow<List<Perfil>> = _contextosDisponiveis.asStateFlow()

    private val _perfilDados = MutableStateFlow(lerContexto())
    /** Contexto/balde ativo — as queries de todas as telas seguem este flow. */
    val perfilDados: StateFlow<Perfil> = _perfilDados.asStateFlow()

    private val _perfilFoiEscolhido = MutableStateFlow(prefs.contains(CHAVE_PERFIL))
    /** False apenas na primeira abertura — dispara a tela de seleção de perfil. */
    val perfilFoiEscolhido: StateFlow<Boolean> = _perfilFoiEscolhido.asStateFlow()

    private val _tipoEmpresa = MutableStateFlow(lerTipoEmpresa())
    /** Tipo da empresa (MEI/CNPJ) — informativo, só existe nos modos com empresa. */
    val tipoEmpresa: StateFlow<TipoEmpresa?> = _tipoEmpresa.asStateFlow()

    /** Troca o modo de uso (onboarding e Configurações). */
    fun mudarModo(modo: ModoUso) = mudarPerfil(modo.perfil)

    fun definirTipoEmpresa(tipo: TipoEmpresa) {
        prefs.edit { putString(CHAVE_TIPO_EMPRESA, tipo.name) }
        _tipoEmpresa.value = tipo
    }

    /** Troca o modo de uso. O contexto volta para a primeira aba do modo. */
    fun mudarPerfil(perfil: Perfil) {
        require(perfil in Perfil.PRINCIPAIS) { "Perfil ${perfil.name} não é um modo de uso" }
        prefs.edit { putString(CHAVE_PERFIL, perfil.name) }
        _perfilAtivo.value = perfil
        _perfilFoiEscolhido.value = true
        _contextosDisponiveis.value = calcularContextos()
        mudarContexto(_contextosDisponiveis.value.first())
    }

    /** Troca a aba ativa (Pessoal / Empresa / Casa). */
    fun mudarContexto(contexto: Perfil) {
        if (contexto !in _contextosDisponiveis.value) return
        prefs.edit { putString(CHAVE_CONTEXTO, contexto.name) }
        _perfilDados.value = contexto
    }

    /** Chamado pelo CasaManager quando o usuário entra/sai de uma casa. */
    fun definirTemCasa(tem: Boolean) {
        temCasa = tem
        _contextosDisponiveis.value = calcularContextos()
        if (!tem && _perfilDados.value == Perfil.CASA) {
            mudarContexto(_contextosDisponiveis.value.first())
        }
    }

    private fun calcularContextos(): List<Perfil> {
        val doModo = when (_perfilAtivo.value) {
            Perfil.MEI -> listOf(Perfil.MEI_PESSOAL, Perfil.MEI_NEGOCIO)
            Perfil.CNPJ -> listOf(Perfil.CNPJ)
            else -> listOf(Perfil.PESSOA_FISICA)
        }
        return if (temCasa) doModo + Perfil.CASA else doModo
    }

    private fun lerPerfil(): Perfil {
        val salvo = prefs.getString(CHAVE_PERFIL, null) ?: return Perfil.PESSOA_FISICA
        val perfil = runCatching { Perfil.valueOf(salvo) }.getOrDefault(Perfil.PESSOA_FISICA)
        // Versões antigas gravavam CASA como perfil; hoje a Casa é um contexto
        return if (perfil in Perfil.PRINCIPAIS) perfil else Perfil.PESSOA_FISICA
    }

    private fun lerContexto(): Perfil {
        val salvo = prefs.getString(CHAVE_CONTEXTO, null)
        if (salvo != null) {
            val contexto = runCatching { Perfil.valueOf(salvo) }.getOrNull()
            if (contexto != null && contexto in _contextosDisponiveis.value) return contexto
        }
        // Migração dos prefs antigos (perfil CASA / aba do MEI)
        return when (prefs.getString(CHAVE_PERFIL, null)) {
            Perfil.CASA.name -> if (temCasa) Perfil.CASA else Perfil.PESSOA_FISICA
            Perfil.MEI.name ->
                if (prefs.getString(CHAVE_CONTEXTO_MEI_LEGADO, null) == "NEGOCIO") {
                    Perfil.MEI_NEGOCIO
                } else {
                    Perfil.MEI_PESSOAL
                }
            Perfil.CNPJ.name -> Perfil.CNPJ
            else -> Perfil.PESSOA_FISICA
        }
    }

    private fun lerTipoEmpresa(): TipoEmpresa? {
        val salvo = prefs.getString(CHAVE_TIPO_EMPRESA, null) ?: return null
        return runCatching { TipoEmpresa.valueOf(salvo) }.getOrNull()
    }

    private companion object {
        const val PREFS = "finapp_prefs"
        const val CHAVE_PERFIL = "perfil_ativo"
        const val CHAVE_CONTEXTO = "contexto_ativo"
        const val CHAVE_CONTEXTO_MEI_LEGADO = "contexto_mei"
        const val CHAVE_TIPO_EMPRESA = "tipo_empresa"
        const val CHAVE_CASA_ID = "casa_id"
    }
}
