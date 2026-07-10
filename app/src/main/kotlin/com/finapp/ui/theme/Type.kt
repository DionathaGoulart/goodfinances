package com.finapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

// Dígitos tabulares: todos com a mesma largura, para valores alinharem
// em listas e não "dançarem" quando mudam (recurso OpenType "tnum").
private const val NUMEROS_TABULARES = "tnum"

val Typography = Typography(
    // Saldo total (grande, em destaque)
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        fontFeatureSettings = NUMEROS_TABULARES
    ),
    // Saldo do card do dashboard
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        fontFeatureSettings = NUMEROS_TABULARES
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        fontFeatureSettings = NUMEROS_TABULARES
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    )
)

/** Escala um estilo preservando o resto (peso, fontFeatureSettings etc.). */
private fun TextStyle.escalar(fator: Float): TextStyle = copy(
    fontSize = fontSize * fator,
    lineHeight = if (lineHeight.isSpecified) lineHeight * fator else lineHeight
)

/**
 * Multiplica os tamanhos de fonte pelo fator escolhido em Aparência.
 * Escala TODOS os estilos da Typography — inclusive os padrão do M3
 * (labelLarge de botões, headlineSmall de títulos de diálogo, bodySmall…),
 * não só os customizados acima.
 */
fun escalarTipografia(base: Typography, fator: Float): Typography =
    if (fator == 1f) base else Typography(
        displayLarge = base.displayLarge.escalar(fator),
        displayMedium = base.displayMedium.escalar(fator),
        displaySmall = base.displaySmall.escalar(fator),
        headlineLarge = base.headlineLarge.escalar(fator),
        headlineMedium = base.headlineMedium.escalar(fator),
        headlineSmall = base.headlineSmall.escalar(fator),
        titleLarge = base.titleLarge.escalar(fator),
        titleMedium = base.titleMedium.escalar(fator),
        titleSmall = base.titleSmall.escalar(fator),
        bodyLarge = base.bodyLarge.escalar(fator),
        bodyMedium = base.bodyMedium.escalar(fator),
        bodySmall = base.bodySmall.escalar(fator),
        labelLarge = base.labelLarge.escalar(fator),
        labelMedium = base.labelMedium.escalar(fator),
        labelSmall = base.labelSmall.escalar(fator)
    )
