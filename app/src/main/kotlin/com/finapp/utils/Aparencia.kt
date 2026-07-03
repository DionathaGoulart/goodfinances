package com.finapp.utils

/** Escala de fonte aplicada em toda a tipografia do app. */
enum class EscalaFonte(val rotulo: String, val fator: Float) {
    NORMAL("Normal", 1f),
    GRANDE("Grande", 1.15f),
    MUITO_GRANDE("Muito Grande", 1.3f)
}

/** Opções de cor primária do tema. */
enum class CorApp(val rotulo: String, val hex: String) {
    VERDE("Verde", "#10B981"),
    AZUL("Azul", "#3B82F6"),
    ROXO("Roxo", "#8B5CF6"),
    LARANJA("Laranja", "#F59E0B")
}

/** Paleta para categorias (criação e edição). */
object CoresCategorias {
    val TODAS = listOf(
        "#10B981", "#3B82F6", "#8B5CF6", "#EC4899", "#EF4444",
        "#F59E0B", "#84CC16", "#14B8A6", "#06B6D4", "#6B7280"
    )
}
