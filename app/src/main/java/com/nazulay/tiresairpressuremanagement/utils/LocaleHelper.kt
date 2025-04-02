package com.nazulay.tiresairpressuremanagement.utils

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.app.Activity
import com.nazulay.tiresairpressuremanagement.MainActivity
import java.util.Locale

/**
 * Helper class for handling locale changes across the app
 */
object LocaleHelper {
    private const val TAG = "LocaleHelper"
    const val LANGUAGE_PREF = "language_preferences"
    const val LANGUAGE_KEY = "selected_language"
    
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_HEBREW = "he"
    
    /**
     * Updates the locale configuration for the given context and restarts the app
     */
    fun setLocale(context: Context, languageCode: String) {
        Log.d(TAG, "Setting locale to: $languageCode")
        
        // Save the selected language preference
        saveLanguagePreference(context, languageCode)
        
        // Apply the locale
        applyLocale(context, languageCode)
        
        // Restart the app to apply changes
        restartApp(context)
    }
    
    /**
     * Applies the locale configuration based on saved preferences without restarting
     */
    fun applyLocale(context: Context, languageCode: String? = null): Context {
        // Use provided language code or load from preferences
        val language = languageCode ?: getSavedLanguage(context)
        
        Log.d(TAG, "Applying locale: $language")
        
        // Create locale instance
        val locale = Locale(language)
        Locale.setDefault(locale)
        
        // Update configuration
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        
        // Apply configuration to resources
        val resources = context.resources
        resources.updateConfiguration(config, resources.displayMetrics)
        
        // Return the context with updated configuration
        return context.createConfigurationContext(config)
    }
    
    /**
     * Gets the saved language preference or default system language
     */
    fun getSavedLanguage(context: Context): String {
        val sharedPreferences = context.getSharedPreferences(LANGUAGE_PREF, Context.MODE_PRIVATE)
        val savedLanguage = sharedPreferences.getString(LANGUAGE_KEY, null)
        
        val systemLanguage = Locale.getDefault().language
        Log.d(TAG, "System default language: $systemLanguage")
        
        // If no saved preference, use system language or default to English
        if (savedLanguage == null) {
            Log.d(TAG, "No saved language preference found")
            val selectedLanguage = when (systemLanguage) {
                LANGUAGE_ENGLISH, LANGUAGE_HEBREW -> systemLanguage
                else -> LANGUAGE_ENGLISH // Default to English for unsupported languages
            }
            Log.d(TAG, "Using language: $selectedLanguage (based on system language)")
            return selectedLanguage
        }
        
        Log.d(TAG, "Using saved language preference: $savedLanguage")
        return savedLanguage
    }
    
    /**
     * Save language preference in shared preferences
     */
    private fun saveLanguagePreference(context: Context, languageCode: String) {
        val sharedPreferences = context.getSharedPreferences(LANGUAGE_PREF, Context.MODE_PRIVATE)
        val previousLanguage = sharedPreferences.getString(LANGUAGE_KEY, null)
        sharedPreferences.edit().putString(LANGUAGE_KEY, languageCode).apply()
        Log.d(TAG, "Language preference changed: $previousLanguage -> $languageCode")
        
        // Verify the change was saved
        val verifyLanguage = sharedPreferences.getString(LANGUAGE_KEY, null)
        Log.d(TAG, "Verified saved language preference: $verifyLanguage")
    }
    
    /**
     * Restart the app to apply language changes
     */
    private fun restartApp(context: Context) {
        try {
            Log.d(TAG, "Restarting app to apply language changes")
            val intent = Intent(context, MainActivity::class.java)
            
            // Clear the existing task and start a new one
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or 
                          Intent.FLAG_ACTIVITY_NEW_TASK or
                          Intent.FLAG_ACTIVITY_CLEAR_TOP)
            
            // This flag helps ensure configuration changes are applied
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            
            // If context is an activity, finish it
            if (context is Activity) {
                context.finish()
            }
            
            // Start the new activity
            context.startActivity(intent)
            
            // Force runtime to use the new configuration
            if (context is Activity) {
                context.recreate()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting app: ${e.message}", e)
        }
    }
} 