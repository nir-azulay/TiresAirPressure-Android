package com.nazulay.tiresairpressuremanagement.auto

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * שירות Android Auto המאפשר לאפליקציה להתחבר למערכת Android Auto ברכב.
 * השירות מספק את נתוני לחץ האוויר בצמיגים ומאפשר שליטה בניפוח/שחרור אוויר.
 */
class TiresPressureCarAppService : CarAppService() {

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
