package com.finapp.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.finapp.ui.screen.AnaliseScreen
import com.finapp.ui.screen.ConfigScreen
import com.finapp.ui.screen.HomeScreen
import com.finapp.ui.screen.PerfilSelecaoScreen
import com.finapp.ui.screen.TransacoesScreen
import com.finapp.viewmodel.PerfilViewModel

/** Destinos da Bottom Navigation. */
enum class FinanDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME("home", "Home", Icons.Filled.Home),
    ANALISE("analise", "Análise", Icons.Filled.PieChart),
    TRANSACOES("transacoes", "Transações", Icons.AutoMirrored.Filled.ReceiptLong),
    CONFIG("config", "Configurações", Icons.Filled.Settings)
}

@Composable
fun FinanApp(perfilViewModel: PerfilViewModel = hiltViewModel()) {
    val perfilFoiEscolhido by perfilViewModel.perfilFoiEscolhido.collectAsStateWithLifecycle()

    // Primeira abertura: escolher perfil antes de entrar no app
    if (!perfilFoiEscolhido) {
        PerfilSelecaoScreen(onSelecionar = perfilViewModel::escolherPerfil)
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                FinanDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.route == destination.route } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                // Evita empilhar destinos e preserva estado de cada aba
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = FinanDestination.HOME.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(FinanDestination.HOME.route) { HomeScreen() }
            composable(FinanDestination.ANALISE.route) { AnaliseScreen() }
            composable(FinanDestination.TRANSACOES.route) { TransacoesScreen() }
            composable(FinanDestination.CONFIG.route) { ConfigScreen() }
        }
    }
}
