package com.finapp.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configuração do vale-transporte (ônibus), guardada em SharedPreferences e
 * exposta como StateFlow. Valores monetários em CENTAVOS, como no resto do app.
 *
 * [saldoAtual] é o saldo atual do cartão de ônibus; a recarga soma nele e
 * gera um gasto de verdade (categoria Transporte). [idaEVolta] = 2 passagens
 * por dia de uso (ida e volta); caso contrário, 1 (só ida).
 * [horaIda]/[horaVolta]: horários em que o desconto automático acontece nos
 * dias de rotina (a ida de manhã, a volta à noite).
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
    val saldoAtual: Long = 0L,
    val horaIda: LocalTime = LocalTime.of(6, 0),
    val horaVolta: LocalTime = LocalTime.of(18, 0)
) {
    /** Passagens por dia de uso: 2 (ida e volta) ou 1 (só ida). */
    val passagensPorDia: Int get() = if (idaEVolta) 2 else 1

    /** Está configurado o suficiente para projetar? */
    val configurado: Boolean get() = valorPassagem > 0L && diasSemana.isNotEmpty()
}

/**
 * Check-in de passagens do dia. `usada` = a passagem saiu do saldo;
 * `processada` = o desconto automático (ou o usuário) já decidiu sobre essa
 * perna — desmarcar uma perna processada não é "re-descontada" pelo automático
 * (é assim que "não usei hoje" sobrevive à próxima passada do processamento).
 */
data class EstadoDiaOnibus(
    val data: LocalDate,
    val idaUsada: Boolean = false,
    val voltaUsada: Boolean = false,
    val idaProcessada: Boolean = false,
    val voltaProcessada: Boolean = false
) {
    /** Passagens já descontadas hoje (alimenta a projeção). */
    val usadasHoje: Int get() = (if (idaUsada) 1 else 0) + (if (voltaUsada) 1 else 0)
}

/** Resultado de uma passada do desconto automático (função pura). */
data class ResultadoOnibus(
    val passagensDescontadas: Int,
    val novoEstadoDia: EstadoDiaOnibus,
    val novoUltimoDiaProcessado: LocalDate,
    val saldoInsuficiente: Boolean
)

/**
 * Desconto automático dos dias de rotina, do dia seguinte a
 * [ultimoDiaProcessado] até [agora]:
 *  - dias completos no intervalo descontam as pernas que ninguém processou
 *    (check-in manual do dia conta como processado);
 *  - hoje desconta a ida a partir de [ConfigOnibus.horaIda] e a volta a partir
 *    de [ConfigOnibus.horaVolta] (se [ConfigOnibus.idaEVolta]);
 *  - nunca desconta abaixo de zero: perna sem saldo é marcada processada sem
 *    uso (dia não fica "devendo"; dá para marcar à mão depois da recarga).
 */
fun calcularDescontosOnibus(
    config: ConfigOnibus,
    estadoDia: EstadoDiaOnibus?,
    ultimoDiaProcessado: LocalDate,
    agora: LocalDateTime
): ResultadoOnibus {
    val hoje = agora.toLocalDate()
    // Estado sempre rotaciona para hoje (mantém as flags se já é de hoje)
    var estadoHoje = if (estadoDia?.data == hoje) estadoDia else EstadoDiaOnibus(hoje)
    val semDesconto = ResultadoOnibus(
        passagensDescontadas = 0,
        novoEstadoDia = estadoHoje,
        novoUltimoDiaProcessado = maxOf(ultimoDiaProcessado, hoje.minusDays(1)),
        saldoInsuficiente = false
    )
    if (!config.configurado) return semDesconto

    var disponiveis = (config.saldoAtual / config.valorPassagem).toInt()
    var descontadas = 0
    var insuficiente = false

    // Tenta descontar 1 passagem; sem saldo, liga a flag e devolve false
    fun descontar(): Boolean = if (disponiveis > 0) {
        disponiveis--
        descontadas++
        true
    } else {
        insuficiente = true
        false
    }

    // Dias completos entre o cursor e hoje (exclusivo): desconta as pernas
    // que ninguém processou naquele dia
    var dia = ultimoDiaProcessado.plusDays(1)
    while (dia.isBefore(hoje)) {
        if (dia.dayOfWeek in config.diasSemana) {
            val doDia = estadoDia?.takeIf { it.data == dia }
            if (doDia?.idaProcessada != true) descontar()
            if (config.idaEVolta && doDia?.voltaProcessada != true) descontar()
        }
        dia = dia.plusDays(1)
    }

    // Hoje: perna a perna, conforme o horário passa
    if (hoje.dayOfWeek in config.diasSemana) {
        val hora = agora.toLocalTime()
        if (!estadoHoje.idaProcessada && hora >= config.horaIda) {
            estadoHoje = estadoHoje.copy(idaProcessada = true, idaUsada = descontar())
        }
        if (config.idaEVolta && !estadoHoje.voltaProcessada && hora >= config.horaVolta) {
            estadoHoje = estadoHoje.copy(voltaProcessada = true, voltaUsada = descontar())
        }
    }

    return ResultadoOnibus(
        passagensDescontadas = descontadas,
        novoEstadoDia = estadoHoje,
        novoUltimoDiaProcessado = maxOf(ultimoDiaProcessado, hoje.minusDays(1)),
        saldoInsuficiente = insuficiente
    )
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

/**
 * Projeta o consumo do saldo, dia a dia, respeitando os dias da semana.
 * [usadasHoje]: passagens já descontadas hoje (o dia de hoje só consome o
 * que falta — sem isso a projeção contaria o dia de hoje em dobro).
 */
fun projetarOnibus(config: ConfigOnibus, hoje: LocalDate, usadasHoje: Int = 0): ProjecaoOnibus {
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
            val necessarias = if (dia == hoje) {
                (porDia - usadasHoje).coerceAtLeast(0)
            } else {
                porDia
            }
            if (necessarias > 0) {
                val usadas = minOf(necessarias, restantes)
                restantes -= usadas
                ultimo = dia
                cobreVolta = usadas >= necessarias
                diasDeUso++
            }
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
    private val trava = Any()

    private val _config = MutableStateFlow(ler())
    val config: StateFlow<ConfigOnibus> = _config.asStateFlow()

    private val _estadoDia = MutableStateFlow(lerEstadoDia())
    /** Check-in de hoje (rotaciona no processamento/check-in). */
    val estadoDia: StateFlow<EstadoDiaOnibus> = _estadoDia.asStateFlow()

    fun definirValorPassagem(centavos: Long) = salvar(_config.value.copy(valorPassagem = centavos))

    fun definirDias(dias: Set<DayOfWeek>) = salvar(_config.value.copy(diasSemana = dias))

    fun definirIdaEVolta(idaEVolta: Boolean) = salvar(_config.value.copy(idaEVolta = idaEVolta))

    fun definirHoraIda(hora: LocalTime) = salvar(_config.value.copy(horaIda = hora))

    fun definirHoraVolta(hora: LocalTime) = salvar(_config.value.copy(horaVolta = hora))

    fun definirSaldo(centavos: Long) = salvar(_config.value.copy(saldoAtual = centavos.coerceAtLeast(0L)))

    /** Recarga: soma no saldo do cartão de ônibus (o gasto é lançado à parte). */
    fun adicionarSaldo(centavos: Long) =
        salvar(_config.value.copy(saldoAtual = _config.value.saldoAtual + centavos))

    /**
     * Marca/desmarca a perna ([ida] ou volta) de hoje. Marcar desconta uma
     * passagem na hora (false = saldo insuficiente); desmarcar devolve ao
     * saldo. Uma perna desmarcada não é re-descontada pelo automático.
     */
    fun alternarCheckin(ida: Boolean): Boolean = synchronized(trava) {
        val hoje = LocalDate.now()
        val atual = _estadoDia.value.takeIf { it.data == hoje } ?: EstadoDiaOnibus(hoje)
        val config = _config.value
        val marcada = if (ida) atual.idaUsada else atual.voltaUsada
        if (!marcada && config.saldoAtual < config.valorPassagem) return false

        val novo = if (ida) {
            atual.copy(idaUsada = !marcada, idaProcessada = true)
        } else {
            atual.copy(voltaUsada = !marcada, voltaProcessada = true)
        }
        val delta = if (marcada) config.valorPassagem else -config.valorPassagem
        persistir(
            config.copy(saldoAtual = (config.saldoAtual + delta).coerceAtLeast(0L)),
            novo,
            ultimoDiaProcessado()
        )
        true
    }

    /**
     * Aplica o desconto automático pendente (dias de rotina desde a última
     * passada + as pernas de hoje cujo horário já passou). Idempotente:
     * chamado ao abrir o app, na virada do dia e pelo worker diário.
     */
    fun processarDescontosAutomaticos(
        agora: LocalDateTime = LocalDateTime.now()
    ): ResultadoOnibus = synchronized(trava) {
        val config = _config.value
        val resultado = calcularDescontosOnibus(
            config = config,
            estadoDia = _estadoDia.value,
            ultimoDiaProcessado = ultimoDiaProcessado(),
            agora = agora
        )
        persistir(
            config.copy(
                saldoAtual = (config.saldoAtual - resultado.passagensDescontadas * config.valorPassagem)
                    .coerceAtLeast(0L)
            ),
            resultado.novoEstadoDia,
            resultado.novoUltimoDiaProcessado
        )
        resultado
    }

    /**
     * Cursor do desconto automático. Primeira execução = ontem: nada de
     * cobrar retroativo de antes da feature existir.
     */
    private fun ultimoDiaProcessado(): LocalDate = LocalDate.ofEpochDay(
        prefs.getLong(CHAVE_ULTIMO_PROC, LocalDate.now().minusDays(1).toEpochDay())
    )

    /** Grava config + estado do dia + cursor num único edit (atômico). */
    private fun persistir(config: ConfigOnibus, estado: EstadoDiaOnibus, cursor: LocalDate) {
        prefs.edit {
            putLong(CHAVE_PASSAGEM, config.valorPassagem)
            putString(CHAVE_DIAS, config.diasSemana.map { it.value }.sorted().joinToString(","))
            putBoolean(CHAVE_IDA_VOLTA, config.idaEVolta)
            putLong(CHAVE_SALDO, config.saldoAtual)
            putInt(CHAVE_HORA_IDA, config.horaIda.toSecondOfDay() / 60)
            putInt(CHAVE_HORA_VOLTA, config.horaVolta.toSecondOfDay() / 60)
            putLong(CHAVE_CHECKIN_DATA, estado.data.toEpochDay())
            putBoolean(CHAVE_CHECKIN_IDA, estado.idaUsada)
            putBoolean(CHAVE_CHECKIN_VOLTA, estado.voltaUsada)
            putBoolean(CHAVE_CHECKIN_IDA_PROC, estado.idaProcessada)
            putBoolean(CHAVE_CHECKIN_VOLTA_PROC, estado.voltaProcessada)
            putLong(CHAVE_ULTIMO_PROC, cursor.toEpochDay())
        }
        _config.value = config
        _estadoDia.value = estado
    }

    private fun salvar(config: ConfigOnibus) = synchronized(trava) {
        persistir(config, _estadoDia.value, ultimoDiaProcessado())
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
            saldoAtual = prefs.getLong(CHAVE_SALDO, padrao.saldoAtual),
            horaIda = lerHora(CHAVE_HORA_IDA, padrao.horaIda),
            horaVolta = lerHora(CHAVE_HORA_VOLTA, padrao.horaVolta)
        )
    }

    private fun lerHora(chave: String, padrao: LocalTime): LocalTime {
        val minutos = prefs.getInt(chave, -1)
        return if (minutos in 0 until 24 * 60) LocalTime.ofSecondOfDay(minutos * 60L) else padrao
    }

    private fun lerEstadoDia(): EstadoDiaOnibus {
        val epoch = prefs.getLong(CHAVE_CHECKIN_DATA, -1L)
        if (epoch < 0L) return EstadoDiaOnibus(LocalDate.now())
        return EstadoDiaOnibus(
            data = LocalDate.ofEpochDay(epoch),
            idaUsada = prefs.getBoolean(CHAVE_CHECKIN_IDA, false),
            voltaUsada = prefs.getBoolean(CHAVE_CHECKIN_VOLTA, false),
            idaProcessada = prefs.getBoolean(CHAVE_CHECKIN_IDA_PROC, false),
            voltaProcessada = prefs.getBoolean(CHAVE_CHECKIN_VOLTA_PROC, false)
        )
    }

    private companion object {
        const val PREFS = "finapp_prefs"
        const val CHAVE_PASSAGEM = "onibus_valor_passagem"
        const val CHAVE_DIAS = "onibus_dias_semana"
        const val CHAVE_IDA_VOLTA = "onibus_ida_volta"
        const val CHAVE_SALDO = "onibus_saldo"
        const val CHAVE_HORA_IDA = "onibus_hora_ida"
        const val CHAVE_HORA_VOLTA = "onibus_hora_volta"
        const val CHAVE_CHECKIN_DATA = "onibus_checkin_data"
        const val CHAVE_CHECKIN_IDA = "onibus_checkin_ida"
        const val CHAVE_CHECKIN_VOLTA = "onibus_checkin_volta"
        const val CHAVE_CHECKIN_IDA_PROC = "onibus_checkin_ida_proc"
        const val CHAVE_CHECKIN_VOLTA_PROC = "onibus_checkin_volta_proc"
        const val CHAVE_ULTIMO_PROC = "onibus_ultimo_dia_processado"
    }
}
