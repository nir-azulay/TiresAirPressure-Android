package com.nazulay.tiresairpressuremanagement

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log
import com.nazulay.tiresairpressuremanagement.utils.LocaleHelper
import java.util.Locale

class TiresAirPressureApp : Application() {
    companion object {
        private const val TAG = "TiresAirPressureApp"
    }

    override fun attachBaseContext(base: Context) {
        // Log system default language
        val systemLocale = Locale.getDefault()
        Log.d(TAG, "System default locale: ${systemLocale.language} (${systemLocale.displayLanguage})")
        
        // Get language preference
        val savedLanguage = LocaleHelper.getSavedLanguage(base)
        Log.d(TAG, "Saved language preference: $savedLanguage")
        
        // Use LocaleHelper to apply the saved locale
        val context = LocaleHelper.applyLocale(base)
        super.attachBaseContext(context)
        
        // Log the actually applied locale
        Log.d(TAG, "Base context attached with locale: ${context.resources.configuration.locales[0].language} (${context.resources.configuration.locales[0].displayLanguage})")
    }

    override fun onCreate() {
        super.onCreate()
        
        // Log current locale from resources
        val currentLocale = resources.configuration.locales[0]
        Log.d(TAG, "Application created with locale: ${currentLocale.language} (${currentLocale.displayLanguage})")
        Log.d(TAG, "Current system locale: ${Locale.getDefault().language} (${Locale.getDefault().displayLanguage})")
        
        // Apply locale to app resources
        updateResourcesLocale()
    }
    
    // Make sure resources have the correct locale
    private fun updateResourcesLocale() {
        val languageCode = LocaleHelper.getSavedLanguage(this)
        Log.d(TAG, "Updating resources locale to: $languageCode")
        
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        
        // Log the default locale after setting
        Log.d(TAG, "Default locale after setting: ${Locale.getDefault().language} (${Locale.getDefault().displayLanguage})")
        
        val resources = Resources.getSystem()
        val config = resources.configuration
        config.setLocale(locale)
        
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
        
        // Log the configuration locale after update
        Log.d(TAG, "System resources configuration locale after update: ${resources.configuration.locales[0].language} (${resources.configuration.locales[0].displayLanguage})")
        Log.d(TAG, "App resources configuration locale: ${this.resources.configuration.locales[0].language} (${this.resources.configuration.locales[0].displayLanguage})")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "Configuration changed, locale: ${newConfig.locales[0]}")
        
        // Make sure our locale setting is preserved across config changes
        val languageCode = LocaleHelper.getSavedLanguage(this)
        val locale = Locale(languageCode)
        
        val config = resources.configuration
        config.setLocale(locale)
        
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
        
        Log.d(TAG, "Reapplied locale after config change: $languageCode")
    }
} 