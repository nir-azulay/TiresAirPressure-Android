package com.nazulay.tiresairpressuremanagement.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.preference.PreferenceManager
import java.util.*

/**
 * Helper class for language/locale related operations
 */
object LocaleHelper {
    private const val SELECTED_LANGUAGE = "user_selected_language"
    const val LANGUAGE_SYSTEM = "system"

    /**
     * Update the app's locale to the one provided
     * @param context The context used to get resources
     * @param languageCode The language code to set (e.g., "en", "iw" for Hebrew)
     * @return Context Updated context with new locale applied
     */
    fun setLocale(context: Context, languageCode: String): Context {
        persist(context, languageCode)
        return updateResources(context, languageCode)
    }

    /**
     * Gets the currently selected language from preferences
     * @param context The context to access shared preferences
     * @return The language code (defaults to "en" if not set)
     */
    fun getSelectedLanguage(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString(SELECTED_LANGUAGE, getSystemLanguage()) ?: getSystemLanguage()
    }

    /**
     * Get the system's default language
     * @return The system language code
     */
    fun getSystemLanguage(): String {
        return Locale.getDefault().language
    }

    /**
     * Get a list of supported languages
     * @return A map of language codes to their display names
     */
    fun getSupportedLanguages(): Map<String, String> {
        return linkedMapOf(
            LANGUAGE_SYSTEM to "System Default",
            "en" to "English",
            "iw" to "עברית" // Hebrew
        )
    }

    /**
     * Save selected language to preferences
     */
    private fun persist(context: Context, languageCode: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putString(SELECTED_LANGUAGE, languageCode).apply()
    }

    /**
     * Update resources with new locale
     */
    private fun updateResources(context: Context, languageCode: String): Context {
        val locale = if (languageCode.isEmpty() || languageCode == LANGUAGE_SYSTEM) {
            Resources.getSystem().configuration.locales[0]
        } else {
            Locale(languageCode)
        }
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        
        return context.createConfigurationContext(configuration)
    }
} 