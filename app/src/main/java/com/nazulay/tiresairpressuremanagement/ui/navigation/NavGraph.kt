package com.nazulay.tiresairpressuremanagement.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.nazulay.tiresairpressuremanagement.ui.screens.MainScreen
import com.nazulay.tiresairpressuremanagement.ui.screens.settings.SettingsScreen
import com.nazulay.tiresairpressuremanagement.ui.screens.settings.SettingsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import android.app.Application
import com.nazulay.tiresairpressuremanagement.data.ble.BleManager
import com.nazulay.tiresairpressuremanagement.data.repository.BleRepository

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
            val context = LocalContext.current
            val application = context.applicationContext as Application
            val bleRepository = BleRepository.getInstance(application)
            val bleManager = BleManager.getInstance(application, bleRepository)
            
            MainScreen(
                bleManager = bleManager,
                onDisconnect = {
                    // Handle disconnect - could navigate to a connection screen
                },
                onSettingsClick = {
                    navController.navigate(NavDestinations.SETTINGS_SCREEN)
                },
                onAboutClick = {
                    // Handle about navigation if needed
                },
                onExitClick = {
                    // Handle exit action if needed
                }
            )
        }
        
        composable(NavDestinations.SETTINGS_SCREEN) {
            val context = LocalContext.current
            val application = context.applicationContext as Application
            val bleRepository = BleRepository.getInstance(application)
            val bleManager = BleManager.getInstance(application, bleRepository)
            
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.provideFactory(
                    application = application,
                    bleManager = bleManager
                )
            )
            
            SettingsScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
} 
