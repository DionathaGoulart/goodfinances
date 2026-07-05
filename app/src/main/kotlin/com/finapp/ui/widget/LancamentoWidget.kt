package com.finapp.ui.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.finapp.MainActivity

/**
 * Widget "Novo lançamento": um toque abre o app direto no modal de
 * nova transação do modo ativo.
 */
class LancamentoWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_ABRIR_LANCAMENTO, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            var moldura = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(FUNDO, FUNDO))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                moldura = moldura.cornerRadius(16.dp)
            }

            Row(
                modifier = moldura
                    .clickable(actionStartActivity(intent))
                    .padding(16.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = "＋",
                    style = TextStyle(
                        color = ColorProvider(VERDE, VERDE),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.width(10.dp))
                Text(
                    text = "Novo lançamento",
                    style = TextStyle(
                        color = ColorProvider(TEXTO, TEXTO),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }

    private companion object {
        // Paleta dark do app (o widget não recompõe com o tema dinâmico)
        val FUNDO = Color(0xFF161B22)
        val VERDE = Color(0xFF10B981)
        val TEXTO = Color(0xFFF0F6FC)
    }
}

class LancamentoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LancamentoWidget()
}
