package com.finapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GreenPrimary,
    onPrimary = BackgroundDark,
    primaryContainer = GreenDark,
    onPrimaryContainer = TextPrimary,
    secondary = BluAccent,
    onSecondary = TextPrimary,
    error = RedExpense,
    onError = TextPrimary,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    outline = OutlineDark
)

/**
 * Tema do FinanApp: dark mode sempre ativo (padrão do app),
 * independente da configuração do sistema.
 * [escalaFonte] e [corPrimaria] vêm das Configurações de Aparência.
 */
@Composable
fun FinanAppTheme(
    escalaFonte: Float = 1f,
    corPrimaria: Color = GreenPrimary,
    content: @Composable () -> Unit
) {
    val esquema = remember(corPrimaria) { DarkColorScheme.copy(primary = corPrimaria) }
    val tipografia = remember(escalaFonte) { escalarTipografia(Typography, escalaFonte) }
    MaterialTheme(
        colorScheme = esquema,
        typography = tipografia,
        content = content
    )
}
