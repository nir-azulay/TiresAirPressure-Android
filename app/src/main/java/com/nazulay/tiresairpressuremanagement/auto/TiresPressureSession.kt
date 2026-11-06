package com.nazulay.tiresairpressuremanagement.auto

import android.content.Intent
import android.util.Log
import androidx.car.app.Screen
import androidx.car.app.Session
import com.nazulay.tiresairpressuremanagement.data.ble.BleManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.nazulay.tiresairpressuremanagement.data.repository.BleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.nazulay.tiresairpressuremanagement.data.ble.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * מחלקה המנהלת את ה-Session של האפליקציה במערכת Android Auto.
 * מחלקה זו אחראית ליצירת המסכים השונים ולניהול מצב החיבור ל-BLE.
 */
class TiresPressureSession : Session(), DefaultLifecycleObserver {
    
    private lateinit var bleManager: BleManager
    private lateinit var bleRepository: BleRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val TAG = "TiresPressureSession"
    
    override fun onCreateScreen(intent: Intent): Screen {
        // Add this session as a lifecycle observer to catch lifecycle events
        lifecycle.addObserver(this)
        
        // Initialize singleton BLE Repository & BLE Manager
        bleRepository = BleRepository.getInstance(carContext)
        bleManager = BleManager.getInstance(carContext, bleRepository)
        
        // Automatically try to connect to last saved device with enhanced reconnection logic
        connectWithRetry()
        
        // מחזירים את המסך הראשי של האפליקציה
        return TiresPressureMainScreen(carContext, bleManager)
    }
    
    private fun connectWithRetry() {
        scope.launch {
            Log.d(TAG, "Attempting to connect to saved device")
            // First connection attempt
            bleRepository.connectToSavedDevices()
            
            // Check connection after a delay and retry if needed
            delay(10000) // Wait 10 seconds
            if (bleManager.connectionState.value != ConnectionState.Connected) {
                Log.d(TAG, "First connection attempt failed, trying again")
                bleRepository.connectToSavedDevices()
                
                // Third attempt if needed
                delay(10000) // Wait another 10 seconds
                if (bleManager.connectionState.value != ConnectionState.Connected) {
                    Log.d(TAG, "Second connection attempt failed, trying one last time")
                    bleRepository.connectToSavedDevices()
                }
            }
        }
    }
    
    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        // Try to reconnect if disconnected
        scope.launch {
            if (bleManager.connectionState.value != ConnectionState.Connected) {
                Log.d(TAG, "Session resumed but not connected, attempting reconnection")
                connectWithRetry()
            }
        }
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        // Disconnect when the session is destroyed to clean up resources
        bleManager.disconnect()
    }
} 
