package com.example.tiresairpressuremanagement.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import com.example.tiresairpressuremanagement.data.ble.BleManager

/**
 * מחלקה המנהלת את ה-Session של האפליקציה במערכת Android Auto.
 * מחלקה זו אחראית ליצירת המסכים השונים ולניהול מצב החיבור ל-BLE.
 */
class TiresPressureSession : Session() {
    
    private lateinit var bleManager: BleManager
    
    override fun onCreateScreen(intent: Intent): Screen {
        // יוצרים מנהל BLE לתקשורת עם המכשיר
        bleManager = BleManager(carContext)
        
        // מחזירים את המסך הראשי של האפליקציה
        return TiresPressureMainScreen(carContext, bleManager)
    }
} 