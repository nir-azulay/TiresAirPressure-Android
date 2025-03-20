package com.example.tiresairpressuremanagement.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.tiresairpressuremanagement.data.ble.BleManager
import com.example.tiresairpressuremanagement.data.ble.ConnectionState
import com.example.tiresairpressuremanagement.domain.models.BleCommand
import com.example.tiresairpressuremanagement.domain.models.DeviceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.example.tiresairpressuremanagement.utils.BleConstants
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
                                invalidate()
                            }
                        }
                        DeviceStatus.MODE_DEFLATE -> {
                            if (!isDeflating) {
                                Log.d(TAG, "Device started deflation")
                                isDeflating = true
                                isInflating = false
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
                delay(1000) // Decreased to 1 second for more responsive updates
            }
        }
    }
    
    override fun onGetTemplate(): Template {
        return if (connectionState != ConnectionState.Connected) {
            // תצוגת חיבור
            MessageTemplate.Builder(carContext.getString(com.example.tiresairpressuremanagement.R.string.not_connected))
                .setTitle(carContext.getString(com.example.tiresairpressuremanagement.R.string.app_name))
                .setHeaderAction(Action.APP_ICON)
                .addAction(
                    Action.Builder()
                        .setTitle(carContext.getString(com.example.tiresairpressuremanagement.R.string.connect))
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

        // Add single row for both current and target pressure with RTL markers
        val currentPressureText = "\u200F" + "לחץ נוכחי: " + String.format("%.1f PSI", currentPressure) + "\u200F"
        val targetPressureText = if (isActiveOperation && targetPressure > 0) {
            "\u200F" + "לחץ יעד: " + String.format("%.1f PSI", targetPressure) + "\u200F"
        } else {
            " "
        }

        paneBuilder.addRow(
            Row.Builder()
                .setTitle(currentPressureText)
                .addText(targetPressureText)
                .build()
        )
        
        // Add operation mode row if there's an active operation
        if (isActiveOperation) {
            val operationText = when (operationMode) {
                DeviceStatus.MODE_INFLATE -> "\u200F" + carContext.getString(com.example.tiresairpressuremanagement.R.string.mode_inflating) + "\u200F"
                DeviceStatus.MODE_DEFLATE -> "\u200F" + carContext.getString(com.example.tiresairpressuremanagement.R.string.mode_deflating) + "\u200F"
                else -> ""
            }
            
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle(operationText)
                    .build()
            )
            
            // Add status message if target pressure is reached
            if (targetPressure > 0 && (
                (operationMode == DeviceStatus.MODE_INFLATE && currentPressure >= targetPressure) ||
                (operationMode == DeviceStatus.MODE_DEFLATE && currentPressure <= targetPressure)
            )) {
                paneBuilder.addRow(
                    Row.Builder()
                        .setTitle("\u200F" + carContext.getString(com.example.tiresairpressuremanagement.R.string.pressure_reached) + "\u200F")
                        .build()
                )
            }
        }
        
        // Add the action button (start/stop)
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("\u200F" + " " + "\u200F")  // Empty title with RTL markers for better layout
                .addAction(
                    Action.Builder()
                        .setTitle(
                            if (isActiveOperation) 
                                carContext.getString(com.example.tiresairpressuremanagement.R.string.stop) 
                            else 
                                carContext.getString(com.example.tiresairpressuremanagement.R.string.start)
                        )
                        .setIcon(
                            if (isActiveOperation)
                                CarIcon.Builder(IconCompat.createWithResource(
                                    carContext, 
                                    android.R.drawable.ic_media_pause
                                )).build()
                            else
                                CarIcon.Builder(IconCompat.createWithResource(
                                    carContext, 
                                    android.R.drawable.ic_media_play
                                )).build()
                        )
                        .setOnClickListener {
                            if (isActiveOperation) {
                                stopInflation()
                            } else {
                                screenManager.push(TiresPressureTargetScreen(carContext, bleManager))
                            }
                        }
                        .build()
                )
                .build()
        )
        
        return PaneTemplate.Builder(paneBuilder.build())
            .setHeaderAction(Action.APP_ICON)
            .setTitle("\u200F" + carContext.getString(com.example.tiresairpressuremanagement.R.string.app_name) + "\u200F")
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