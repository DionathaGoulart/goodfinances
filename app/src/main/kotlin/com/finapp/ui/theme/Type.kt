package com.finapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    // Saldo total (grande, em destaque)
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
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

/** Multiplica os tamanhos de fonte pelo fator escolhido em Aparência. */
fun escalarTipografia(base: Typography, fator: Float): Typography =
    if (fator == 1f) base else base.copy(
        displayLarge = base.displayLarge.copy(fontSize = base.displayLarge.fontSize * fator),
        titleLarge = base.titleLarge.copy(fontSize = base.titleLarge.fontSize * fator),
        titleMedium = base.titleMedium.copy(fontSize = base.titleMedium.fontSize * fator),
        bodyLarge = base.bodyLarge.copy(fontSize = base.bodyLarge.fontSize * fator),
        bodyMedium = base.bodyMedium.copy(fontSize = base.bodyMedium.fontSize * fator),
        labelSmall = base.labelSmall.copy(fontSize = base.labelSmall.fontSize * fator)
    )
