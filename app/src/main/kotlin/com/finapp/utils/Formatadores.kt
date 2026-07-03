package com.finapp.utils

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formatação de valores e datas no padrão brasileiro. */
object Formatadores {

    val LOCALE_BR: Locale = Locale.forLanguageTag("pt-BR")

    private val moeda: NumberFormat = NumberFormat.getCurrencyInstance(LOCALE_BR)

    private val dataCurta: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE_BR)

    private val dataAgrupamento: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d 'DE' MMM, EEEE", LOCALE_BR)

    /** Centavos -> moeda. Ex: 123456 -> "R$ 1.234,56" */
    fun moeda(centavos: Long): String = moeda.format(centavos / 100.0)

    /** Ex: 2024-06-15 -> "15/06/2024" */
    fun dataCurta(data: LocalDate): String = data.format(dataCurta)

    /** Ex: 2024-06-15 -> "15 DE JUN, DOMINGO" (header de grupo na lista) */
    fun dataAgrupamento(data: LocalDate): String =
        data.format(dataAgrupamento).uppercase(LOCALE_BR)

    /** Centavos -> valor compacto para rótulos de gráfico. Ex: 125000 (R$ 1.250) -> "1,3k" */
    fun moedaCompacta(centavos: Long): String {
        val reais = centavos / 100.0
        return when {
            reais >= 1_000_000 -> String.format(LOCALE_BR, "%.1fM", reais / 1_000_000)
            reais >= 1_000 -> String.format(LOCALE_BR, "%.1fk", reais / 1_000)
            else -> String.format(LOCALE_BR, "%.0f", reais)
        }
    }
}
