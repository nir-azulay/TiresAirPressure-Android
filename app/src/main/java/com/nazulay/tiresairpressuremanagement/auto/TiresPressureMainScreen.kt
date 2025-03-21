package com.nazulay.tiresairpressuremanagement.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.nazulay.tiresairpressuremanagement.data.ble.BleManager
import com.nazulay.tiresairpressuremanagement.data.ble.ConnectionState
import com.nazulay.tiresairpressuremanagement.domain.models.BleCommand
import com.nazulay.tiresairpressuremanagement.domain.models.DeviceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.nazulay.tiresairpressuremanagement.utils.BleConstants
import androidx.core.graphics.drawable.IconCompat
import android.graphics.drawable.Icon
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * המסך הראשי של האפליקציה ב-Android Auto.
 * מציג את לחץ האוויר הנוכחי ומאפשר למשתמש להתחיל או להפסיק ניפוח.
 */
class TiresPressureMainScreen(
    carContext: CarContext,
    private val bleManager: BleManager
) : Screen(carContext), DefaultLifecycleObserver {
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private var currentPressure: Float = 0.0f
    private var isInflating: Boolean = false
    private var isDeflating: Boolean = false
    private var targetPressure: Float = 0.0f
    private var connectionState: ConnectionState = ConnectionState.Disconnected
    private var operationMode: Int = DeviceStatus.MODE_STOP
    private var isTargetPressureReached: Boolean = false  // New flag to track target pressure reached state
    private val TAG = "TiresPressureMainScreen"

    init {
        lifecycle.addObserver(this)
        
        // מעקב אחר שינויים במצב החיבור
        bleManager.connectionState
            .onEach { state ->
                Log.d(TAG, "Connection state changed: $state")
                connectionState = state
                invalidate()
            }
            .launchIn(coroutineScope)
        
        // מעקב אחר שינויים בלחץ האוויר
        bleManager.currentPressure
            .onEach { pressure ->
                val changed = currentPressure != pressure
                Log.d(TAG, "Pressure flow updated: $pressure (changed: $changed)")
                currentPressure = pressure
                if (changed) {
                    Log.d(TAG, "Pressure changed, invalidating UI")
                    invalidate()
                }
            }
            .launchIn(coroutineScope)
            
        // Monitor device status directly for additional debugging and UI state syncing
        bleManager.deviceStatus
            .onEach { status ->
                Log.d(TAG, "Device status update: $status")
                if (status != null) {
                    Log.d(TAG, "Device pressure from status: ${status.pressure}")
                    Log.d(TAG, "Operation mode: ${status.operationMode}")
                    
                    // Update operation mode
                    val previousMode = operationMode
                    operationMode = status.operationMode

                    // Update inflation state based on operation mode
                    when (operationMode) {
                        DeviceStatus.MODE_STOP -> {
                            if (isInflating || isDeflating) {
                                Log.d(TAG, "Device stopped inflation/deflation")
                                isInflating = false
                                isDeflating = false
                                invalidate()
                            }
                        }
                        DeviceStatus.MODE_INFLATE -> {
                            if (!isInflating) {
                                Log.d(TAG, "Device started inflation")
                                isInflating = true
                                isDeflating = false
                                isTargetPressureReached = false  // Reset the flag when starting new operation
                                invalidate()
                            }
                        }
                        DeviceStatus.MODE_DEFLATE -> {
                            if (!isDeflating) {
                                Log.d(TAG, "Device started deflation")
                                isDeflating = true
                                isInflating = false
                                isTargetPressureReached = false  // Reset the flag when starting new operation
                                invalidate()
                            }
                        }
                    }
                    
                    // If operation mode changed, update the UI
                    if (previousMode != operationMode) {
                        invalidate()
                    }
                }
            }
            .launchIn(coroutineScope)

        // Monitor target reached events
        bleManager.targetReachedEvent
            .onEach { (targetPressure, currentPressure) ->
                Log.d(TAG, "Target reached event: target=$targetPressure, current=$currentPressure")
                isTargetPressureReached = true
                invalidate()
            }
            .launchIn(coroutineScope)
            
        // Start a periodic refresh to ensure UI updates
        startPeriodicRefresh()
    }
    
    private fun startPeriodicRefresh() {
        coroutineScope.launch {
            while (isActive) {
                // Force refresh every second to ensure data is updated
                if (connectionState == ConnectionState.Connected) {
                    Log.d(TAG, "Requesting fresh pressure reading, current value: $currentPressure")
                    bleManager.refreshPressureReading()
                    
                    // Add a small delay before invalidating to allow the BLE response to arrive
                    delay(200)
                }
                invalidate()
                delay(500) // Decreased to 1 second for more responsive updates
            }
        }
    }
    
    override fun onGetTemplate(): Template {
        return if (connectionState != ConnectionState.Connected) {
            // תצוגת חיבור
            MessageTemplate.Builder(carContext.getString(com.nazulay.tiresairpressuremanagement.R.string.not_connected))
                .setTitle(carContext.getString(com.nazulay.tiresairpressuremanagement.R.string.app_name))
                .setHeaderAction(Action.APP_ICON)
                .addAction(
                    Action.Builder()
                        .setTitle(carContext.getString(com.nazulay.tiresairpressuremanagement.R.string.connect))
                        .setIcon(
                            CarIcon.Builder(IconCompat.createWithResource(
                                carContext, 
                                android.R.drawable.stat_sys_data_bluetooth
                            )).build()
                        )
                        .setOnClickListener { 
                            Log.d(TAG, "Connect button clicked")
                            bleManager.connectToSavedDevice() 
                        }
                        .build()
                )
                .build()
        } else {
            // תצוגת לחץ אוויר ובקרת ניפוח
            buildMainTemplate()
        }
    }
    
    private fun buildMainTemplate(): Template {
        Log.d(TAG, "Building template with pressure: $currentPressure, operation mode: $operationMode")
        
        val isActiveOperation = operationMode == DeviceStatus.MODE_INFLATE || operationMode == DeviceStatus.MODE_DEFLATE
        
        val paneBuilder = Pane.Builder()

        // Row for current pressure
        paneBuilder.addRow(
            Row.Builder()
                .setTitle(carContext.getString(com.nazulay.tiresairpressuremanagement.R.string.auto_current_pressure) + " " + 
                         String.format(carContext.getString(com.nazulay.tiresairpressuremanagement.R.string.auto_pressure_format), currentPressure))
                .build()
        )
        
        // Always show target pressure row
        paneBuilder.addRow(
            Row.Builder()
                .setTitle(carContext.getString(com.nazulay.tiresairpressuremanagement.R.string.auto_target_pressure) + " " + 
                         String.format(carContext.getString(com.nazulay.tiresairpressuremanagement.R.string.auto_pressure_format), 
                         if (targetPressure > 0) targetPressure else 0.0f))
                .build()
        )

        // Create operation status text
        val operationText = when {
            isTargetPressureReached -> 
                carContext.getString(com.nazulay.tiresairpressuremanagement.R.string.pressure_reached)
            operationMode == DeviceStatus.MODE_INFLATE -> 
                carContext.getString(com.nazulay.tiresairpressuremanagement.R.string.mode_inflating)
            operationMode == DeviceStatus.MODE_DEFLATE -> 
                carContext.getString(com.nazulay.tiresairpressuremanagement.R.string.mode_deflating)
            else -> carContext.getString(com.nazulay.tiresairpressuremanagement.R.string.auto_select_target)
        }

        // Always create a row with operation status and control button
        paneBuilder.addRow(
            Row.Builder()
                .setTitle(operationText)
                .addAction(
                    Action.Builder()
                        .setTitle(
                            when (operationMode) {
                                DeviceStatus.MODE_STOP -> carContext.getString(com.nazulay.tiresairpressuremanagement.R.string.start)
                                else -> carContext.getString(com.nazulay.tiresairpressuremanagement.R.string.stop)
                            }
                        )
                        .setIcon(
                            when (operationMode) {
                                DeviceStatus.MODE_STOP -> 
                                    CarIcon.Builder(IconCompat.createWithResource(
                                        carContext, 
                                        android.R.drawable.ic_media_play
                                    )).build()
                                else ->
                                    CarIcon.Builder(IconCompat.createWithResource(
                                        carContext, 
                                        android.R.drawable.ic_media_pause
                                    )).build()
                            }
                        )
                        .setOnClickListener {
                            if (operationMode != DeviceStatus.MODE_STOP) {
                                stopInflation()
                            } else {
                                isTargetPressureReached = false  // Reset the flag when starting new operation
                                screenManager.push(TiresPressureTargetScreen(carContext, bleManager))
                            }
                        }
                        .build()
                )
                .build()
        )
        
        return PaneTemplate.Builder(paneBuilder.build())
            .setHeaderAction(Action.APP_ICON)
            .setTitle(carContext.getString(com.nazulay.tiresairpressuremanagement.R.string.auto_app_title))
            .build()
    }
    
    /**
     * התחלת תהליך ניפוח עם לחץ היעד המוגדר.
     * 
     * @param pressure לחץ היעד לניפוח (ב-PSI)
     */
    fun startInflation(pressure: Float) {
        targetPressure = pressure
        isInflating = true
        isTargetPressureReached = false  // Reset the flag when starting new inflation
        invalidate()
        
        // Actually send the command to the device
        coroutineScope.launch {
            bleManager.sendCommand(BleCommand.StartTargetPressure(targetPressure = pressure))
        }
    }
    
    private fun stopInflation() {
        coroutineScope.launch {
            bleManager.sendCommand(BleCommand.Stop())
            // Let the device status update handle the UI state
            // The isInflating/isDeflating flags will be updated when we receive the status update
        }
    }
    
    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        // Request latest data when screen resumes
        invalidate()
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        // שליחת פקודת עצירה אם המסך נסגר במהלך ניפוח
        if (isInflating || isDeflating) {
            stopInflation()
        }
    }
} 
