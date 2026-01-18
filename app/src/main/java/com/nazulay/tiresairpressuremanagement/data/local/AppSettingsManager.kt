package com.nazulay.tiresairpressuremanagement.data.local

import android.content.Context
import android.util.Log
import com.nazulay.tiresairpressuremanagement.domain.models.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import com.nazulay.tiresairpressuremanagement.ui.screens.settings.SettingsViewModel

/**
 * Manager class for application settings that are stored locally
 * This includes theme, language, simulation mode, and predefined pressures
 * Implemented as a singleton to ensure consistency across the app.
 */
class AppSettingsManager private constructor(private val context: Context) {
    companion object {
        @Volatile private var INSTANCE: AppSettingsManager? = null

        fun getInstance(context: Context): AppSettingsManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        
        private const val TAG = "AppSettingsManager"
        private const val APP_SETTINGS_PREF = "app_settings_preferences"
        private const val THEME_MODE_KEY = "theme_mode"
        private const val SIMULATION_MODE_KEY = "simulation_mode"
        private const val DEBUG_MODE_KEY = "debug_mode"
        private const val PREDEFINED_PRESSURES_KEY_PREFIX = "predefined_pressure_"
        private const val MIN_PRESSURE_COUNT_KEY = "min_pressure_count"
        private const val MAX_PRESSURE_COUNT_KEY = "max_pressure_count"
    }

    private val prefs = context.getSharedPreferences(APP_SETTINGS_PREF, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val debugMode = prefs.getBoolean(DEBUG_MODE_KEY, false)
        Log.d(TAG, "Loading settings - debugMode: $debugMode")
        return AppSettings(
            themeMode = SettingsViewModel.THEME_MODE_DARK,
            simulationMode = prefs.getBoolean(SIMULATION_MODE_KEY, false),
            predefinedPressures = loadPredefinedPressures(),
            minPressureCount = prefs.getInt(MIN_PRESSURE_COUNT_KEY, 1),
            maxPressureCount = prefs.getInt(MAX_PRESSURE_COUNT_KEY, 8),
            debugMode = debugMode
        )
    }

    private fun loadPredefinedPressures(): List<Float> {
        val pressuresString = prefs.getString(PREDEFINED_PRESSURES_KEY_PREFIX, null)
        return pressuresString?.split(",")?.mapNotNull { it.toFloatOrNull() }
            ?: listOf(8f, 10f, 12f, 16f, 20f, 30f, 38f, 40f)
    }

    fun updateSettings(settings: AppSettings) {
        prefs.edit().apply {
            putInt(THEME_MODE_KEY, SettingsViewModel.THEME_MODE_DARK)
            putBoolean(SIMULATION_MODE_KEY, settings.simulationMode)
            putString(PREDEFINED_PRESSURES_KEY_PREFIX, settings.predefinedPressures.joinToString(","))
            putInt(MIN_PRESSURE_COUNT_KEY, settings.minPressureCount)
            putInt(MAX_PRESSURE_COUNT_KEY, settings.maxPressureCount)
            putBoolean(DEBUG_MODE_KEY, settings.debugMode)
            apply()
        }
        _settings.value = settings.copy(themeMode = SettingsViewModel.THEME_MODE_DARK)
        Log.d(TAG, "Settings updated: $settings")
    }

    fun updateThemeMode(themeMode: Int) {
        Log.d(TAG, "Theme mode change requested but ignored - using dark mode only")
    }

    fun updateSimulationMode(enabled: Boolean) {
        val currentSettings = _settings.value
        updateSettings(currentSettings.copy(simulationMode = enabled))
    }

    fun updatePredefinedPressures(pressures: List<Float>) {
        val currentSettings = _settings.value
        updateSettings(currentSettings.copy(predefinedPressures = pressures))
    }
    
    fun updatePressureCount(minCount: Int, maxCount: Int) {
        val currentSettings = _settings.value
        val maxAllowed = 16 // Maximum allowed count
        val fixedMinCount = 4 // Fixed minimum count
        
        // Apply constraints to ensure valid values and make sure max is even
        val validMax = if (maxCount % 2 == 0) {
            maxCount.coerceIn(fixedMinCount, maxAllowed)
        } else {
            // If odd, round down to nearest even number
            (maxCount - 1).coerceIn(fixedMinCount, maxAllowed)
        }
        
        // Create updated settings with fixed min and valid max
        val updatedSettings = currentSettings.copy(
            minPressureCount = fixedMinCount,
            maxPressureCount = validMax
        )
        
        // Ensure we have correct number of pressures
        val currentPressures = currentSettings.predefinedPressures
        // Extend the default pressures list to support more presets
        val defaultPressures = listOf(8f, 10f, 12f, 16f, 20f, 30f, 38f, 40f, 45f, 50f, 55f, 60f, 65f, 70f, 75f, 80f)
        
        // If we need to add more, use defaults or zeros
        if (currentPressures.size < validMax) {
            val additionalPressures = (currentPressures.size until validMax).map { i ->
                defaultPressures.getOrElse(i) { 0f }
            }
            val newPressures = currentPressures + additionalPressures
            updateSettings(updatedSettings.copy(predefinedPressures = newPressures))
        } else if (currentPressures.size > validMax) {
            // If we need to remove some, truncate the list
            updateSettings(updatedSettings.copy(predefinedPressures = currentPressures.take(validMax)))
        } else {
            // Just update the min/max
            updateSettings(updatedSettings)
        }
        
        Log.d(TAG, "Pressure count updated: min=$fixedMinCount, max=$validMax")
    }

    fun updateDebugMode(enabled: Boolean) {
        Log.d(TAG, "Updating debug mode to: $enabled")
        val currentSettings = _settings.value
        updateSettings(currentSettings.copy(debugMode = enabled))
        Log.d(TAG, "Debug mode updated and saved to SharedPreferences")
    }
} 