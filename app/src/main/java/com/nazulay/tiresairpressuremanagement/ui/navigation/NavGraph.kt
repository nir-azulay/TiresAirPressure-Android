package com.nazulay.tiresairpressuremanagement.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nazulay.tiresairpressuremanagement.ui.screens.main.MainScreen
import com.nazulay.tiresairpressuremanagement.ui.screens.settings.SettingsScreen
import com.nazulay.tiresairpressuremanagement.ui.screens.settings.SettingsViewModel

object NavDestinations {
    const val MAIN_SCREEN = "main"
    const val SETTINGS_SCREEN = "settings"
}

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = NavDestinations.MAIN_SCREEN
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(NavDestinations.MAIN_SCREEN) {
            MainScreen(
                onNavigateToSettings = {
                    navController.navigate(NavDestinations.SETTINGS_SCREEN)
                }
            )
        }
        
        composable(NavDestinations.SETTINGS_SCREEN) {
            val viewModel: SettingsViewModel = viewModel()
            SettingsScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
} 
