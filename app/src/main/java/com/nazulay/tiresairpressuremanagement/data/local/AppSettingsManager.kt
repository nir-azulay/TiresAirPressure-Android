package com.nazulay.tiresairpressuremanagement.data.local

import android.content.Context
import android.util.Log
import com.nazulay.tiresairpressuremanagement.domain.models.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

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
        private const val PREDEFINED_PRESSURES_KEY_PREFIX = "predefined_pressure_"
    }

    private val prefs = context.getSharedPreferences(APP_SETTINGS_PREF, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        return AppSettings(
            themeMode = prefs.getInt(THEME_MODE_KEY, 0),
            simulationMode = prefs.getBoolean(SIMULATION_MODE_KEY, false),
            predefinedPressures = loadPredefinedPressures()
        )
    }

    private fun loadPredefinedPressures(): List<Float> {
        val pressuresString = prefs.getString(PREDEFINED_PRESSURES_KEY_PREFIX, null)
        return pressuresString?.split(",")?.mapNotNull { it.toFloatOrNull() }
            ?: listOf(8f, 10f, 12f, 16f, 20f, 30f, 38f, 40f)
    }

    fun updateSettings(settings: AppSettings) {
        prefs.edit().apply {
            putInt(THEME_MODE_KEY, settings.themeMode)
            putBoolean(SIMULATION_MODE_KEY, settings.simulationMode)
            putString(PREDEFINED_PRESSURES_KEY_PREFIX, settings.predefinedPressures.joinToString(","))
            apply()
        }
        _settings.value = settings
        Log.d(TAG, "Settings updated: $settings")
    }

    fun updateThemeMode(themeMode: Int) {
        val currentSettings = _settings.value
        updateSettings(currentSettings.copy(themeMode = themeMode))
    }

    fun updateSimulationMode(enabled: Boolean) {
        val currentSettings = _settings.value
        updateSettings(currentSettings.copy(simulationMode = enabled))
    }

    fun updatePredefinedPressures(pressures: List<Float>) {
        val currentSettings = _settings.value
        updateSettings(currentSettings.copy(predefinedPressures = pressures))
    }
} 