package com.finapp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

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
 * Deriva a paleta inteira a partir da cor-semente: containers e secundária
 * saem da própria cor, e os fundos ganham uma tintura leve (4–8%) para o
 * app todo mudar de clima ao alternar pessoal/empresa — sempre dark e
 * legível. Verde/vermelho de ganho/gasto ficam fora (são semânticos).
 */
private fun esquemaParaCor(semente: Color): ColorScheme = DarkColorScheme.copy(
    primary = semente,
    primaryContainer = lerp(semente, Color.Black, 0.55f),
    secondary = lerp(semente, TextSecondary, 0.40f),
    background = lerp(BackgroundDark, semente, 0.04f),
    surface = lerp(SurfaceDark, semente, 0.05f),
    surfaceVariant = lerp(SurfaceVariantDark, semente, 0.08f),
    outline = lerp(OutlineDark, semente, 0.15f)
)

/**
 * Tema do FinanApp: dark mode sempre ativo (padrão do app),
 * independente da configuração do sistema.
 * [escalaFonte] e [corPrimaria] vêm das Configurações de Aparência;
 * a cor muda conforme o contexto ativo (pessoal ou empresa).
 */
@Composable
fun FinanAppTheme(
    escalaFonte: Float = 1f,
    corPrimaria: Color = GreenPrimary,
    content: @Composable () -> Unit
) {
    val esquema = remember(corPrimaria) { esquemaParaCor(corPrimaria) }
    val tipografia = remember(escalaFonte) { escalarTipografia(Typography, escalaFonte) }
    MaterialTheme(
        colorScheme = esquema,
        typography = tipografia,
        content = content
    )
}
