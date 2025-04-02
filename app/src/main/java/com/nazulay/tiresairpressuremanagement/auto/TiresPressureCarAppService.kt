package com.nazulay.tiresairpressuremanagement.auto

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import java.util.Locale

/**
 * Android Auto service that allows the app to connect to the Android Auto system in the vehicle.
 * The service provides tire air pressure data and allows control of inflation/deflation.
 */
class TiresPressureCarAppService : CarAppService() {

    override fun attachBaseContext(newBase: Context) {
        // Force English locale
        val localeToSwitchTo = Locale.ENGLISH
        val localeUpdatedContext = updateBaseContextLocale(newBase, localeToSwitchTo)
        super.attachBaseContext(localeUpdatedContext)
    }
    
    private fun updateBaseContextLocale(context: Context, locale: Locale): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }

    override fun createHostValidator(): HostValidator {
        return if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(): Session {
        return TiresPressureSession()
    }
} 
