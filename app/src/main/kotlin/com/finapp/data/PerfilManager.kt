package com.finapp.data

import android.content.Context
import androidx.core.content.edit
import com.finapp.data.db.entities.Dono
import com.finapp.data.db.entities.ModoUso
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoEmpresa
import com.finapp.data.db.entities.ehEmpresa
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
 * [contextosDisponiveis] são as abas da Home. **A Casa NÃO é mais uma aba**:
 * o lado pessoal e o da casa são uma visão só (o "de quem" é escolhido em cada
 * lançamento, via [Dono]) — só a Empresa fica separada.
 * [perfilDados] é o balde PRIVADO do contexto ativo (destino de escrita
 * padrão e âncora das telas que ainda operam num balde só);
 * [baldesVisiveis] é o conjunto que as LISTAS leem e [baldesFinanceiros] o
 * que entra em saldo/agregado — a diferença é o espelho dos membros, que
 * aparece na lista mas nunca no seu saldo.
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
    /** Abas da Home: Pessoal (inclui a Casa) e, nos modos com empresa, Empresa. */
    val contextosDisponiveis: StateFlow<List<Perfil>> = _contextosDisponiveis.asStateFlow()

    private val _perfilDados = MutableStateFlow(lerContexto())
    /** Contexto/balde PRIVADO ativo — destino de escrita padrão e âncora das telas. */
    val perfilDados: StateFlow<Perfil> = _perfilDados.asStateFlow()

    private val _baldesVisiveis = MutableStateFlow(calcularBaldesVisiveis())
    /**
     * Baldes que as LISTAS do contexto ativo leem. No pessoal com casa:
     * privado + Casa + espelho dos membros. Na empresa: só ela.
     */
    val baldesVisiveis: StateFlow<List<Perfil>> = _baldesVisiveis.asStateFlow()

    private val _baldesFinanceiros = MutableStateFlow(calcularBaldesFinanceiros())
    /**
     * Baldes que entram em saldo/somas/pendências. É [baldesVisiveis] SEM o
     * espelho dos membros: o gasto pessoal da sua esposa aparece na lista
     * (para você saber o que rolou), mas não pode descontar do SEU saldo.
     */
    val baldesFinanceiros: StateFlow<List<Perfil>> = _baldesFinanceiros.asStateFlow()

    private val _perfilFoiEscolhido = MutableStateFlow(prefs.contains(CHAVE_PERFIL))
    /** False apenas na primeira abertura — dispara a tela de seleção de perfil. */
    val perfilFoiEscolhido: StateFlow<Boolean> = _perfilFoiEscolhido.asStateFlow()

    private val _dicasVistas = MutableStateFlow(prefs.getBoolean(CHAVE_DICAS, false))
    /** False até o usuário fechar as dicas iniciais (mostradas uma única vez). */
    val dicasVistas: StateFlow<Boolean> = _dicasVistas.asStateFlow()

    fun marcarDicasVistas() {
        prefs.edit { putBoolean(CHAVE_DICAS, true) }
        _dicasVistas.value = true
    }

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

    /** Troca a aba ativa (Pessoal / Empresa). */
    fun mudarContexto(contexto: Perfil) {
        if (contexto !in _contextosDisponiveis.value) return
        prefs.edit { putString(CHAVE_CONTEXTO, contexto.name) }
        _perfilDados.value = contexto
        atualizarBaldes()
    }

    /** True quando o usuário está numa casa (o repository usa para espelhar cartões). */
    fun temCasa(): Boolean = temCasa

    /** Chamado pelo CasaManager quando o usuário entra/sai de uma casa. */
    fun definirTemCasa(tem: Boolean) {
        temCasa = tem
        _contextosDisponiveis.value = calcularContextos()
        if (_perfilDados.value !in _contextosDisponiveis.value) {
            mudarContexto(_contextosDisponiveis.value.first())
        } else {
            // Entrar/sair da casa muda o que a aba Pessoal enxerga
            atualizarBaldes()
        }
    }

    /**
     * Balde de escrita conforme o dono escolhido. Numa casa TUDO vai para o
     * balde compartilhado — inclusive o atribuído a uma pessoa: é o que
     * permite atribuir gasto um ao outro (no balde privado de quem digitou,
     * a outra pessoa nunca veria). Fora de uma casa, tudo é privado.
     */
    @Suppress("UNUSED_PARAMETER")
    fun baldeDe(dono: Dono): Perfil {
        val contexto = _perfilDados.value
        // Na empresa o "de quem" não se aplica: é sempre da empresa
        if (contexto.ehEmpresa) return contexto
        return if (temCasa) Perfil.CASA else contexto
    }

    /**
     * Balde onde nascem as entidades GLOBAIS (hoje: cartões). Sempre o lado
     * pessoal do modo, mesmo se o usuário estiver na aba Empresa: cartão não
     * pertence mais a um contexto, e nascer no balde pessoal é o que o faz
     * espelhar para a Casa e chegar aos outros membros. No modo só-empresa
     * não existe lado pessoal, então fica na própria empresa.
     */
    fun baldeGlobal(): Perfil = when (_perfilAtivo.value) {
        Perfil.MEI -> Perfil.MEI_PESSOAL
        Perfil.CNPJ -> Perfil.CNPJ
        else -> Perfil.PESSOA_FISICA
    }

    private fun atualizarBaldes() {
        _baldesVisiveis.value = calcularBaldesVisiveis()
        _baldesFinanceiros.value = calcularBaldesFinanceiros()
    }

    private fun calcularContextos(): List<Perfil> = when (_perfilAtivo.value) {
        Perfil.MEI -> listOf(Perfil.MEI_PESSOAL, Perfil.MEI_NEGOCIO)
        Perfil.CNPJ -> listOf(Perfil.CNPJ)
        else -> listOf(Perfil.PESSOA_FISICA)
    }

    /**
     * Baldes financeiros de um contexto QUALQUER (não só o ativo) — a Análise
     * combina contextos e precisa expandir cada um. A empresa nunca mistura
     * com a casa; o pessoal só a inclui quando o usuário está numa.
     */
    fun baldesDoContexto(contexto: Perfil): List<Perfil> =
        if (contexto.ehEmpresa || !temCasa) listOf(contexto) else listOf(contexto, Perfil.CASA)

    private fun calcularBaldesFinanceiros(): List<Perfil> = baldesDoContexto(_perfilDados.value)

    private fun calcularBaldesVisiveis(): List<Perfil> {
        val financeiros = calcularBaldesFinanceiros()
        return if (temCasa && !_perfilDados.value.ehEmpresa) {
            financeiros + Perfil.CASA_MEMBROS
        } else {
            financeiros
        }
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
        // Migração dos prefs antigos (perfil/aba CASA — hoje a Casa não é mais
        // uma aba, virou o dono padrão dos lançamentos do contexto pessoal —
        // e a aba do MEI)
        if (salvo == Perfil.CASA.name) {
            return if (_perfilAtivo.value == Perfil.MEI) Perfil.MEI_PESSOAL else Perfil.PESSOA_FISICA
        }
        return when (prefs.getString(CHAVE_PERFIL, null)) {
            Perfil.CASA.name ->
                if (_perfilAtivo.value == Perfil.MEI) Perfil.MEI_PESSOAL else Perfil.PESSOA_FISICA
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
        const val CHAVE_DICAS = "dicas_iniciais_vistas"
    }
}
