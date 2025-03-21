package com.nazulay.tiresairpressuremanagement.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import com.nazulay.tiresairpressuremanagement.data.ble.BleManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * מחלקה המנהלת את ה-Session של האפליקציה במערכת Android Auto.
 * מחלקה זו אחראית ליצירת המסכים השונים ולניהול מצב החיבור ל-BLE.
 */
class TiresPressureSession : Session(), DefaultLifecycleObserver {
    
    private lateinit var bleManager: BleManager
    
    override fun onCreateScreen(intent: Intent): Screen {
        // Add this session as a lifecycle observer to catch lifecycle events
        lifecycle.addObserver(this)
        
        // יוצרים מנהל BLE לתקשורת עם המכשיר
        bleManager = BleManager(carContext)
        
        // Automatically try to connect to last saved device when session starts
        bleManager.connectToSavedDevice()
        
        // מחזירים את המסך הראשי של האפליקציה
        return TiresPressureMainScreen(carContext, bleManager)
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        // Disconnect when the session is destroyed to clean up resources
        bleManager.disconnect()
    }
} 
