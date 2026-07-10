package com.finapp.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configuração do vale-transporte (ônibus), guardada em SharedPreferences e
 * exposta como StateFlow. Valores monetários em CENTAVOS, como no resto do app.
 *
 * [saldoAtual] é o saldo atual do cartão de ônibus; a recarga soma nele e
 * gera um gasto de verdade (categoria Transporte). [idaEVolta] = 2 passagens
 * por dia de uso (ida e volta); caso contrário, 1 (só ida).
 */
data class ConfigOnibus(
    val valorPassagem: Long = 0L,
    val diasSemana: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    ),
    val idaEVolta: Boolean = true,
    val saldoAtual: Long = 0L
) {
    /** Passagens por dia de uso: 2 (ida e volta) ou 1 (só ida). */
    val passagensPorDia: Int get() = if (idaEVolta) 2 else 1

    /** Está configurado o suficiente para projetar? */
    val configurado: Boolean get() = valorPassagem > 0L && diasSemana.isNotEmpty()
}

/**
 * Projeção de até quando o saldo do cartão de ônibus dura, a partir de hoje.
 * [ultimoDia] é o último dia de uso coberto; [cobreVoltaNoUltimoDia] indica se
 * nesse dia dá para ida e volta (false = só a ida).
 */
data class ProjecaoOnibus(
    val passagensRestantes: Int = 0,
    val diasDeUso: Int = 0,
    val ultimoDia: LocalDate? = null,
    val cobreVoltaNoUltimoDia: Boolean = true
)

/** Projeta o consumo do saldo, dia a dia, respeitando os dias da semana. */
fun projetarOnibus(config: ConfigOnibus, hoje: LocalDate): ProjecaoOnibus {
    if (!config.configurado) return ProjecaoOnibus()
    val totalPassagens = (config.saldoAtual / config.valorPassagem).toInt()
    if (totalPassagens <= 0) return ProjecaoOnibus(passagensRestantes = 0)

    var restantes = totalPassagens
    val porDia = config.passagensPorDia
    var dia = hoje
    var ultimo: LocalDate? = null
    var cobreVolta = true
    var diasDeUso = 0
    // Teto de segurança (~2 anos) para nunca travar mesmo com saldo enorme
    var guarda = 0
    while (restantes > 0 && guarda < 800) {
        if (dia.dayOfWeek in config.diasSemana) {
            val usadas = minOf(porDia, restantes)
            restantes -= usadas
            ultimo = dia
            cobreVolta = usadas >= porDia
            diasDeUso++
        }
        dia = dia.plusDays(1)
        guarda++
    }
    return ProjecaoOnibus(
        passagensRestantes = totalPassagens,
        diasDeUso = diasDeUso,
        ultimoDia = ultimo,
        cobreVoltaNoUltimoDia = cobreVolta
    )
}

/** Guarda e expõe a configuração do ônibus (um único setup, compartilhado entre perfis). */
@Singleton
class OnibusManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(ler())
    val config: StateFlow<ConfigOnibus> = _config.asStateFlow()

    fun definirValorPassagem(centavos: Long) = salvar(_config.value.copy(valorPassagem = centavos))

    fun definirDias(dias: Set<DayOfWeek>) = salvar(_config.value.copy(diasSemana = dias))

    fun definirIdaEVolta(idaEVolta: Boolean) = salvar(_config.value.copy(idaEVolta = idaEVolta))

    fun definirSaldo(centavos: Long) = salvar(_config.value.copy(saldoAtual = centavos.coerceAtLeast(0L)))

    /** Recarga: soma no saldo do cartão de ônibus (o gasto é lançado à parte). */
    fun adicionarSaldo(centavos: Long) =
        salvar(_config.value.copy(saldoAtual = _config.value.saldoAtual + centavos))

    private fun salvar(config: ConfigOnibus) {
        prefs.edit {
            putLong(CHAVE_PASSAGEM, config.valorPassagem)
            putString(CHAVE_DIAS, config.diasSemana.map { it.value }.sorted().joinToString(","))
            putBoolean(CHAVE_IDA_VOLTA, config.idaEVolta)
            putLong(CHAVE_SALDO, config.saldoAtual)
        }
        _config.value = config
    }

    private fun ler(): ConfigOnibus {
        val padrao = ConfigOnibus()
        val diasSalvos = prefs.getString(CHAVE_DIAS, null)
        val dias = if (diasSalvos == null) {
            padrao.diasSemana
        } else {
            diasSalvos.split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .mapNotNull { n -> runCatching { DayOfWeek.of(n) }.getOrNull() }
                .toSet()
        }
        return ConfigOnibus(
            valorPassagem = prefs.getLong(CHAVE_PASSAGEM, padrao.valorPassagem),
            diasSemana = dias,
            idaEVolta = prefs.getBoolean(CHAVE_IDA_VOLTA, padrao.idaEVolta),
            saldoAtual = prefs.getLong(CHAVE_SALDO, padrao.saldoAtual)
        )
    }

    private companion object {
        const val PREFS = "finapp_prefs"
        const val CHAVE_PASSAGEM = "onibus_valor_passagem"
        const val CHAVE_DIAS = "onibus_dias_semana"
        const val CHAVE_IDA_VOLTA = "onibus_ida_volta"
        const val CHAVE_SALDO = "onibus_saldo"
    }
}
