package com.finapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import com.finapp.data.AparenciaManager
import com.finapp.ui.FinanApp
import com.finapp.ui.theme.FinanAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var aparenciaManager: AparenciaManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val escala by aparenciaManager.escalaFonte.collectAsState()
            val cor by aparenciaManager.corPrimaria.collectAsState()

            FinanAppTheme(
                escalaFonte = escala.fator,
                corPrimaria = Color(cor.hex.toColorInt())
            ) {
                FinanApp()
            }
        }
    }
}
