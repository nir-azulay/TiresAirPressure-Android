package com.example.tiresairpressuremanagement.auto

import android.content.Intent
import android.util.Log
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template

/**
 * מחלקת ה-Session עבור Android Auto.
 * מחלקה זו אחראית על ניהול מסכי האפליקציה ב-Android Auto.
 */
class TiresAirPressureSession : Session() {
    private val TAG = "TiresAirPressureSession"

    override fun onCreateScreen(intent: Intent): Screen {
        Log.d(TAG, "Creating screen for Android Auto")
        try {
            Log.d(TAG, "Intent action: ${intent.action}")
            Log.d(TAG, "Intent data: ${intent.dataString}")
            
            // ניסיון ליצור את המסך
            return TiresAirPressureScreen(carContext)
        } catch (e: Exception) {
            Log.e(TAG, "שגיאה ביצירת המסך", e)
            
            // כאן נחזיר מסך שגיאה בטוח במקום לזרוק חריגה
            return object : Screen(carContext) {
                override fun onGetTemplate(): Template {
                    return MessageTemplate.Builder("התרחשה שגיאה בלתי צפויה")
                        .setTitle("שגיאה")
                        .build()
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        Log.d(TAG, "New intent received in Android Auto session")
        try {
            Log.d(TAG, "Intent action: ${intent.action}")
            Log.d(TAG, "Intent data: ${intent.dataString}")
            super.onNewIntent(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling new intent", e)
        }
    }
} 