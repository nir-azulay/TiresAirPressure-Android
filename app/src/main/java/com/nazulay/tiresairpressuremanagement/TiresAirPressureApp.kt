package com.nazulay.tiresairpressuremanagement

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.util.Log

class TiresAirPressureApp : Application() {
    companion object {
        private const val TAG = "TiresAirPressureApp"
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        Log.d(TAG, "Base context attached")
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