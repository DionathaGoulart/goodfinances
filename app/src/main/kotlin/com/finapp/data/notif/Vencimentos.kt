package com.finapp.data.notif

/**
 * Regras dos lembretes de vencimento, em funções puras (sem Android) para os
 * testes unitários cobrirem — padrão Recorrencias/ParserImportacao.
 */

/** A partir de quantos dias antes do vencimento os lembretes diários começam. */
const val DIAS_AVISO_VENCIMENTO = 5L

/**
 * Faixas do dia em que o [NotificacaoWorker] roda — os lembretes de
 * vencimento repetem uma vez por faixa (manhã, tarde e noite).
 */
enum class PeriodoDia(val rotulo: String) {
    MANHA("manha"),
    TARDE("tarde"),
    NOITE("noite");

    companion object {
        /** Faixa correspondente à [hora] do dia (0-23). */
        fun deHora(hora: Int): PeriodoDia = when {
            hora < 12 -> MANHA
            hora < 18 -> TARDE
            else -> NOITE
        }
    }
}

/**
 * Texto do prazo de um vencimento: [dias] é a distância até a data
 * (negativo = já venceu e a conta/fatura está atrasada).
 */
fun mensagemPrazo(dias: Long): String = when {
    dias < 0 -> {
        val atras = -dias
        "atrasou há $atras ${if (atras == 1L) "dia" else "dias"}"
    }
    dias == 0L -> "vence hoje"
    dias == 1L -> "vence amanhã"
    else -> "vence em $dias dias"
}
