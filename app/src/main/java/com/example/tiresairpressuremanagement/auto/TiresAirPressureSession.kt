package com.example.tiresairpressuremanagement.auto

import android.content.Intent
import android.util.Log
import androidx.car.app.Screen
import androidx.car.app.Session

/**
 * מחלקת ה-Session עבור Android Auto.
 * מחלקה זו אחראית על ניהול מסכי האפליקציה ב-Android Auto.
 */
class TiresAirPressureSession : Session() {
    private val TAG = "TiresAirPressureSession"

    override fun onCreateScreen(intent: Intent): Screen {
        Log.d(TAG, "Creating screen for Android Auto")
        try {
            return TiresAirPressureScreen(carContext)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating screen", e)
            throw e
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        Log.d(TAG, "New intent received in Android Auto session")
        try {
            super.onNewIntent(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling new intent", e)
        }
    }
} 