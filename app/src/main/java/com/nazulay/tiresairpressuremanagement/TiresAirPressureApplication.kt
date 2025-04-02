package com.nazulay.tiresairpressuremanagement

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.nazulay.tiresairpressuremanagement.utils.LocaleHelper
import com.nazulay.tiresairpressuremanagement.utils.ActivityProvider
import java.util.*

class TiresAirPressureApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize ActivityProvider to track current activity
        ActivityProvider.init(this)
    }
    
    override fun attachBaseContext(base: Context) {
        // Apply selected language
        val languageCode = LocaleHelper.getSelectedLanguage(base)
        super.attachBaseContext(LocaleHelper.setLocale(base, languageCode))
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        
        // Re-apply selected language when configuration changes
        val languageCode = LocaleHelper.getSelectedLanguage(this)
        LocaleHelper.setLocale(this, languageCode)
    }
} 