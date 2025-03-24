package com.nazulay.tiresairpressuremanagement

import android.app.Application
import android.content.Context

class TiresAirPressureApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private lateinit var instance: TiresAirPressureApp

        fun getAppContext(): Context = instance.applicationContext
    }
} 