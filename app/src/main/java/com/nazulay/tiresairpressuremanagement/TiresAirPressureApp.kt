package com.nazulay.tiresairpressuremanagement

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import java.util.Locale

class TiresAirPressureApp : Application() {
    companion object {
        private const val TAG = "TiresAirPressureApp"
    }

    override fun attachBaseContext(base: Context) {
        // Force English locale for the application
        val localeToSwitchTo = Locale.ENGLISH
        val localeUpdatedContext = updateBaseContextLocale(base, localeToSwitchTo)
        super.attachBaseContext(localeUpdatedContext)
        Log.d(TAG, "Base context attached, forced locale to English")
    }
    
    private fun updateBaseContextLocale(context: Context, locale: Locale): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application created")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "Configuration changed")
    }
} 