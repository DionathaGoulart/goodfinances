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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.finapp.MainActivity
import com.finapp.data.PerfilManager
import com.finapp.data.repository.FinanceRepository
import com.finapp.utils.Formatadores
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.YearMonth
import kotlinx.coroutines.flow.first

/**
 * Widget "Saldo": mostra o saldo total do perfil ativo e os ganhos/gastos
 * do mês corrente. Um toque abre o app. Os dados vêm do repository via
 * Hilt EntryPoint (widgets Glance não suportam injeção direta).
 */
class SaldoWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SaldoWidgetEntryPoint {
        fun financeRepository(): FinanceRepository
        fun perfilManager(): PerfilManager
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SaldoWidgetEntryPoint::class.java
        )
        val repository = entryPoint.financeRepository()
        val perfil = entryPoint.perfilManager().perfilDados.value

        val mes = YearMonth.now()
        val saldo = repository.observarSaldoTotal(perfil).first()
        val ganhos = repository.observarGanhos(perfil, mes.atDay(1), mes.atEndOfMonth()).first()
        val gastos = repository.observarGastos(perfil, mes.atDay(1), mes.atEndOfMonth()).first()

        provideContent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            var moldura = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(FUNDO, FUNDO))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                moldura = moldura.cornerRadius(16.dp)
            }

            Column(
                modifier = moldura
                    .clickable(actionStartActivity(intent))
                    .padding(16.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = "GoodFinances",
                    style = TextStyle(
                        color = ColorProvider(TEXTO_SUAVE, TEXTO_SUAVE),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = Formatadores.moeda(saldo),
                    style = TextStyle(
                        color = ColorProvider(TEXTO, TEXTO),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Text(
                        text = "+" + Formatadores.moeda(ganhos),
                        style = TextStyle(
                            color = ColorProvider(VERDE, VERDE),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(12.dp))
                    Text(
                        text = "−" + Formatadores.moeda(gastos),
                        style = TextStyle(
                            color = ColorProvider(VERMELHO, VERMELHO),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }

    private companion object {
        // Paleta dark do app (o widget não recompõe com o tema dinâmico)
        val FUNDO = Color(0xFF161B22)
        val VERDE = Color(0xFF10B981)
        val VERMELHO = Color(0xFFEF4444)
        val TEXTO = Color(0xFFF0F6FC)
        val TEXTO_SUAVE = Color(0xFF8B949E)
    }
}

class SaldoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SaldoWidget()
}
