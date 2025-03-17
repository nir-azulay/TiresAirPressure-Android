package com.example.tiresairpressuremanagement.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.tiresairpressuremanagement.R
import com.example.tiresairpressuremanagement.domain.models.BleDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * מסך בסיסי להצגה באנדרואיד אוטו
 */
class TiresAirPressureScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {
    private val TAG = "TiresAirPressureScreen"
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val tireDataAdapter = TireDataAdapter(carContext)
    
    private var isScanning = false
    private var scanResults = listOf<BleDevice>()
    
    private enum class ScreenState {
        MAIN, SCANNING
    }
    
    private var currentScreenState = ScreenState.MAIN
    private var dotsCount = 0
    
    init {
        Log.d(TAG, "Screen initialized")
        lifecycle.addObserver(this)
    }
    
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.d(TAG, "onStart called")
        startAutoInvalidate()
    }
    
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Log.d(TAG, "onStop called")
        stopAutoInvalidate()
    }
    
    private var autoInvalidateJob: kotlinx.coroutines.Job? = null
    
    private fun startAutoInvalidate() {
        Log.d(TAG, "startAutoInvalidate() called")
        stopAutoInvalidate()
        
        autoInvalidateJob = coroutineScope.launch {
            try {
                while (true) {
                    delay(500)
                    invalidate()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in auto invalidate", e)
            }
        }
    }
    
    private fun stopAutoInvalidate() {
        Log.d(TAG, "stopAutoInvalidate() called")
        autoInvalidateJob?.cancel()
        autoInvalidateJob = null
    }
    
    override fun onGetTemplate(): Template {
        Log.d(TAG, "onGetTemplate() called, state: $currentScreenState")
        
        return try {
            when (currentScreenState) {
                ScreenState.SCANNING -> getScanningTemplate()
                else -> getMainTemplate()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting template", e)
            MessageTemplate.Builder("שגיאה בטעינת המסך. נסה שנית.")
                .setTitle("בקרת לחץ אוויר")
                .setHeaderAction(Action.BACK)
                .build()
        }
    }
    
    private fun getMainTemplate(): Template {
        Log.d(TAG, "getMainTemplate() called")
        
        return MessageTemplate.Builder("לחץ על לחצן הסריקה כדי להתחיל")
            .setTitle("בקרת לחץ אוויר")
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle("סריקה")
                    .setOnClickListener {
                        Log.d(TAG, "Scan button clicked")
                        startScan()
                    }
                    .build()
            )
            .build()
    }
    
    private fun startScan() {
        Log.d(TAG, "startScan() called")
        try {
            currentScreenState = ScreenState.SCANNING
            isScanning = true
            tireDataAdapter.startScan()
            
            // התחל לעקוב אחרי תוצאות הסריקה
            coroutineScope.launch {
                try {
                    tireDataAdapter.getScanResults().collectLatest { devices ->
                        Log.d(TAG, "Scan results updated: ${devices.size} devices found")
                        scanResults = devices
                        invalidate()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error collecting scan results", e)
                }
            }
            
            invalidate()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting scan", e)
        }
    }
    
    private fun getScanningTemplate(): Template {
        Log.d(TAG, "getScanningTemplate() called")
        
        val message = if (scanResults.isEmpty()) {
            "סורק התקנים..." + getLoadingDots()
        } else {
            "נמצאו ${scanResults.size} התקנים"
        }
        
        return MessageTemplate.Builder(message)
            .setTitle("סריקת התקנים")
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle("חזור")
                    .setOnClickListener {
                        Log.d(TAG, "Back button clicked")
                        stopScan()
                    }
                    .build()
            )
            .build()
    }
    
    private fun stopScan() {
        Log.d(TAG, "stopScan() called")
        try {
            isScanning = false
            tireDataAdapter.stopScan()
            currentScreenState = ScreenState.MAIN
            invalidate()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        }
    }
    
    private fun getLoadingDots(): String {
        dotsCount = (dotsCount + 1) % 4
        return ".".repeat(dotsCount)
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        Log.d(TAG, "onDestroy called")
        coroutineScope.cancel()
        lifecycle.removeObserver(this)
        super.onDestroy(owner)
    }
} 