package com.nazulay.tiresairpressuremanagement.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.nazulay.tiresairpressuremanagement.domain.models.AppSettings
import com.nazulay.tiresairpressuremanagement.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manager class for application settings that are stored locally
 * This includes theme, simulation mode, and predefined pressures
 */
class AppSettingsManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AppSettingsManager"
        private const val APP_SETTINGS_PREF = "app_settings_preferences"
        
        // Settings keys
        private const val THEME_MODE_KEY = "theme_mode"
        private const val SIMULATION_MODE_KEY = "simulation_mode"
        private const val PREDEFINED_PRESSURES_KEY_PREFIX = "predefined_pressure_" 
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(APP_SETTINGS_PREF, Context.MODE_PRIVATE)
    
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    
    private fun loadSettings(): AppSettings {
        Log.d(TAG, "Loading app settings from SharedPreferences")
        
        // Always use dark theme
        val themeMode = SettingsViewModel.THEME_MODE_DARK
        
        val simulationMode = sharedPreferences.getBoolean(
            SIMULATION_MODE_KEY, 
            false
        )
        
        val predefinedPressures = loadPredefinedPressures()
        
        return AppSettings(
            themeMode = themeMode,
            simulationMode = simulationMode,
            predefinedPressures = predefinedPressures
        )
    }
    
    private fun loadPredefinedPressures(): List<Float> {
        val defaultPressures = listOf(8f, 10f, 12f, 16f, 20f, 30f, 38f, 40f)
        
        val pressures = mutableListOf<Float>()
        for (i in defaultPressures.indices) {
            val pressure = sharedPreferences.getFloat(
                PREDEFINED_PRESSURES_KEY_PREFIX + i,
                defaultPressures.getOrElse(i) { 0f }
            )
            pressures.add(pressure)
        }
        
        return pressures
    }
    
    private fun savePredefinedPressures(pressures: List<Float>) {
        val editor = sharedPreferences.edit()
        
        pressures.forEachIndexed { index, pressure ->
            editor.putFloat(PREDEFINED_PRESSURES_KEY_PREFIX + index, pressure)
        }
        
        editor.apply()
    }
    
    fun updateThemeMode(mode: Int) {
        // No-op - always use dark mode
        Log.d(TAG, "Theme mode update ignored - using dark mode only")
    }
    
    fun updateSimulationMode(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(SIMULATION_MODE_KEY, enabled).apply()
        
        _settings.value = _settings.value.copy(simulationMode = enabled)
        Log.d(TAG, "Simulation mode updated to: $enabled")
    }
    
    fun updatePredefinedPressures(pressures: List<Float>) {
        savePredefinedPressures(pressures)
        
        _settings.value = _settings.value.copy(predefinedPressures = pressures)
        Log.d(TAG, "Predefined pressures updated to: $pressures")
    }
} 