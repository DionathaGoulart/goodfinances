package com.finapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finapp.data.ConfigOnibus
import com.finapp.data.OnibusManager
import com.finapp.data.PerfilManager
import com.finapp.data.ProjecaoOnibus
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.Transacao
import com.finapp.data.repository.FinanceRepository
import com.finapp.data.sync.CasaManager
import com.finapp.utils.fluxoDataAtual
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

/**
 * Aba Ônibus: valor da passagem, dias de uso e projeção de até quando o saldo
 * do cartão dura. A recarga soma no saldo e lança um gasto real (Transporte).
 */
@HiltViewModel
class OnibusViewModel @Inject constructor(
    private val onibusManager: OnibusManager,
    private val repository: FinanceRepository,
    private val perfilManager: PerfilManager,
    private val casaManager: CasaManager
) : ViewModel() {

    val config: StateFlow<ConfigOnibus> = onibusManager.config

    /** Contextos disponíveis (Pessoal/Empresa/Casa) — destino do gasto da recarga. */
    val contextos: StateFlow<List<Perfil>> = perfilManager.contextosDisponiveis

    val perfilDados: StateFlow<Perfil> = perfilManager.perfilDados

    /** Projeção reativa: recalcula quando a config muda ou vira o dia. */
    val projecao: StateFlow<ProjecaoOnibus> =
        combine(onibusManager.config, fluxoDataAtual()) { cfg, hoje ->
            com.finapp.data.projetarOnibus(cfg, hoje)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjecaoOnibus())

    private val _mensagens = MutableSharedFlow<String>()
    val mensagens: SharedFlow<String> = _mensagens

    fun definirValorPassagem(centavos: Long) = onibusManager.definirValorPassagem(centavos)

    fun definirDias(dias: Set<DayOfWeek>) = onibusManager.definirDias(dias)

    fun definirIdaEVolta(idaEVolta: Boolean) = onibusManager.definirIdaEVolta(idaEVolta)

    fun definirSaldo(centavos: Long) = onibusManager.definirSaldo(centavos)

    fun alternarDia(dia: DayOfWeek) {
        val atuais = config.value.diasSemana
        onibusManager.definirDias(if (dia in atuais) atuais - dia else atuais + dia)
    }

    /**
     * Recarga: soma [centavos] no saldo do cartão. Com [registrarGasto], lança
     * também um GASTO de verdade (categoria Transporte, "Ônibus") no [perfil]
     * escolhido; sem ele, só ajusta o saldo (carga feita fora do app / já tinha).
     */
    fun recarregar(centavos: Long, perfil: Perfil, registrarGasto: Boolean) {
        if (centavos <= 0L) {
            emitir("Informe um valor de recarga maior que zero")
            return
        }
        viewModelScope.launch {
            runCatching {
                if (registrarGasto) {
                    // Na Casa, carimba a autoria (mesma regra do TransacaoViewModel):
                    // sem ela a linha fica sem "por Fulano" e editável por todos
                    val usuario = casaManager.usuario.value.takeIf { perfil == Perfil.CASA }
                    repository.inserirTransacao(
                        Transacao(
                            valor = centavos,
                            tipo = TipoTransacao.GASTO,
                            categoria = CATEGORIA_TRANSPORTE,
                            descricao = "Ônibus",
                            data = LocalDate.now(),
                            perfil = perfil,
                            criadoPor = usuario?.nome.orEmpty(),
                            criadoPorUid = usuario?.uid.orEmpty(),
                            pago = true
                        )
                    )
                }
                onibusManager.adicionarSaldo(centavos)
            }
                .onSuccess {
                    emitir(
                        if (registrarGasto) "Recarga lançada em ${perfil.rotulo}"
                        else "Saldo adicionado (sem gasto)"
                    )
                }
                .onFailure { emitir("Erro ao lançar a recarga") }
        }
    }

    /**
     * Uso avulso: desconta [passagens] do saldo sem gerar gasto (o dinheiro já
     * saiu na recarga). Cobre dias fora da rotina — trabalhar num dia extra ou
     * gastar só uma passagem.
     */
    fun registrarUso(passagens: Int) {
        val atual = config.value
        if (atual.valorPassagem <= 0L) {
            emitir("Defina o valor da passagem primeiro")
            return
        }
        onibusManager.definirSaldo(atual.saldoAtual - passagens.toLong() * atual.valorPassagem)
        emitir(
            if (passagens == 1) "Registrado: 1 passagem usada"
            else "Registrado: $passagens passagens usadas"
        )
    }

    private fun emitir(mensagem: String) {
        viewModelScope.launch { _mensagens.emit(mensagem) }
    }

    private companion object {
        const val CATEGORIA_TRANSPORTE = "Transporte"
    }
}
