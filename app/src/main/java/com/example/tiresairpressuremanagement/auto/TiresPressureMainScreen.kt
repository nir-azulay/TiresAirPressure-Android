package com.example.tiresairpressuremanagement.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.tiresairpressuremanagement.data.ble.BleManager
import com.example.tiresairpressuremanagement.data.ble.ConnectionState
import com.example.tiresairpressuremanagement.domain.models.BleCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.example.tiresairpressuremanagement.utils.BleConstants
import androidx.core.graphics.drawable.IconCompat
import android.graphics.drawable.Icon

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
    private var targetPressure: Float = 0.0f
    private var connectionState: ConnectionState = ConnectionState.Disconnected
    
    init {
        lifecycle.addObserver(this)
        
        // מעקב אחר שינויים במצב החיבור
        bleManager.connectionState
            .onEach { state ->
                connectionState = state
                invalidate()
            }
            .launchIn(coroutineScope)
        
        // מעקב אחר שינויים בלחץ האוויר
        bleManager.currentPressure
            .onEach { pressure ->
                currentPressure = pressure
                invalidate()
            }
            .launchIn(coroutineScope)
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
                        .setOnClickListener { bleManager.connectToSavedDevice() }
                        .build()
                )
                .build()
        } else {
            // תצוגת לחץ אוויר ובקרת ניפוח
            buildMainTemplate()
        }
    }
    
    private fun buildMainTemplate(): Template {
        val currentPressureText = String.format("%.1f PSI", currentPressure)
        
        val inflateDeflateRow = Row.Builder()
            .addText(
                if (isInflating) 
                    carContext.getString(com.example.tiresairpressuremanagement.R.string.stop) 
                else 
                    carContext.getString(com.example.tiresairpressuremanagement.R.string.inflate)
            )
            .addAction(
                Action.Builder()
                    .setTitle(
                        if (isInflating) 
                            carContext.getString(com.example.tiresairpressuremanagement.R.string.stop) 
                        else 
                            carContext.getString(com.example.tiresairpressuremanagement.R.string.inflate)
                    )
                    .setOnClickListener {
                        if (isInflating) {
                            // שליחת פקודת עצירה
                            stopInflation()
                        } else {
                            // מעבר למסך בחירת לחץ יעד
                            screenManager.push(TiresPressureTargetScreen(carContext, bleManager))
                        }
                    }
                    .build()
            )
            .build()
        
        // הוספת שורת מידע על לחץ היעד אם בתהליך ניפוח
        val paneBuilder = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle(carContext.getString(com.example.tiresairpressuremanagement.R.string.auto_current_pressure))
                    .addText(currentPressureText)
                    .build()
            )
        
        // הוספת שורת לחץ יעד רק אם בניפוח
        if (isInflating && targetPressure > 0) {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle(carContext.getString(com.example.tiresairpressuremanagement.R.string.target_pressure))
                    .addText(String.format("%.1f PSI", targetPressure))
                    .build()
            )
        }
        
        // הוספת שורת פעולת ניפוח/עצירה
        paneBuilder.addRow(inflateDeflateRow)
        
        return PaneTemplate.Builder(paneBuilder.build())
            .setHeaderAction(Action.APP_ICON)
            .setTitle(carContext.getString(com.example.tiresairpressuremanagement.R.string.app_name))
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
    }
    
    private fun stopInflation() {
        coroutineScope.launch {
            bleManager.sendCommand(BleCommand.Stop())
            isInflating = false
            invalidate()
        }
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        // שליחת פקודת עצירה אם המסך נסגר במהלך ניפוח
        if (isInflating) {
            stopInflation()
        }
    }
} 