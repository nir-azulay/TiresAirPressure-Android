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
import kotlinx.coroutines.flow.firstOrNull
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
    private var currentPressure = 0.0f
    private var targetPressure = 30.0f // ערך ברירת מחדל
    private var isInflating = false
    
    private enum class ScreenState {
        MAIN, SCANNING, CONNECTED, OPERATING
    }
    
    private var currentScreenState = ScreenState.MAIN
    private var dotsCount = 0
    private var connectedDevice: BleDevice? = null
    
    init {
        Log.d(TAG, "Screen initialized")
        lifecycle.addObserver(this)
    }
    
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.d(TAG, "onStart called")
        startAutoInvalidate()
        checkConnectionState()
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
    
    private fun checkConnectionState() {
        coroutineScope.launch {
            try {
                val isConnected = tireDataAdapter.isConnected.firstOrNull() ?: false
                Log.d(TAG, "Connection state check: $isConnected")
                
                if (isConnected) {
                    currentScreenState = ScreenState.CONNECTED
                    // Start listening for pressure updates
                    tireDataAdapter.getPressureUpdates().collectLatest { pressure ->
                        currentPressure = pressure
                        invalidate()
                    }
                }
                
                invalidate()
            } catch (e: Exception) {
                Log.e(TAG, "Error checking connection state", e)
            }
        }
    }
    
    override fun onGetTemplate(): Template {
        Log.d(TAG, "onGetTemplate() called, state: $currentScreenState")
        
        return try {
            when (currentScreenState) {
                ScreenState.SCANNING -> getScanningTemplate()
                ScreenState.CONNECTED, ScreenState.OPERATING -> getConnectedTemplate()
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
        
        val paneBuilder = Pane.Builder()
        
        // כותרת ותיאור
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("בקרת לחץ אוויר בצמיגים")
                .addText("לא מחובר למכשיר. לחץ על 'סריקה' כדי לחפש מכשירים זמינים.")
                .build()
        )
        
        // הסבר נוסף על האפליקציה
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("מה תוכל לעשות?")
                .addText("• בדיקת לחץ אוויר בצמיגים\n• ניפוח אוטומטי ללחץ היעד\n• שמירת הגדרות אישיות")
                .build()
        )
        
        // עדכון של חיבור אחרון
        val sharedPreferences = carContext.getSharedPreferences("device_settings", android.content.Context.MODE_PRIVATE)
        val lastDeviceName = sharedPreferences.getString("selected_device_name", null)
        
        if (lastDeviceName != null) {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("חיבור אחרון")
                    .addText(lastDeviceName)
                    .build()
            )
        }
        
        val actionList = ArrayList<Action>()
        
        val scanAction = Action.Builder()
            .setTitle("סריקת מכשירים")
            .setOnClickListener {
                Log.d(TAG, "Scan button clicked")
                startScan()
            }
            .build()
        
        actionList.add(scanAction)
        
        if (lastDeviceName != null) {
            val reconnectAction = Action.Builder()
                .setTitle("חיבור מחדש")
                .setOnClickListener {
                    Log.d(TAG, "Reconnect button clicked")
                    tireDataAdapter.connectToSavedDevice()
                }
                .build()
            
            actionList.add(reconnectAction)
        }
        
        val actionStrip = ActionStrip.Builder()
            .addAction(scanAction)
            .build()
        
        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("מנהל לחץ אוויר")
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(actionStrip)
            .build()
    }
    
    private fun getConnectedTemplate(): Template {
        Log.d(TAG, "getConnectedTemplate() called")
        
        val paneBuilder = Pane.Builder()
        
        // הוספת שורה של לחץ נוכחי
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("לחץ נוכחי")
                .addText("$currentPressure PSI")
                .build()
        )
        
        // הוספת שורה של לחץ יעד
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("לחץ יעד")
                .addText("$targetPressure PSI")
                .build()
        )
        
        // הוספת כפתורים לשינוי לחץ היעד
        val decreaseAction = Action.Builder()
            .setTitle("-")
            .setOnClickListener {
                targetPressure = (targetPressure - 0.5f).coerceAtLeast(0f)
                invalidate()
            }
            .build()
            
        val increaseAction = Action.Builder()
            .setTitle("+")
            .setOnClickListener {
                targetPressure = (targetPressure + 0.5f).coerceAtMost(100f)
                invalidate()
            }
            .build()
        
        // הוספת פעולת התחל/עצור
        val startStopAction = Action.Builder()
            .setTitle(if (isInflating) "עצור" else "התחל")
            .setBackgroundColor(CarColor.createCustom(if (isInflating) 0xFFE53935.toInt() else 0xFF4CAF50.toInt(), 0))
            .setOnClickListener {
                toggleInflation()
            }
            .build()
        
        // יצירת ActionStrip עם הפעולות הנדרשות
        val actionStripBuilder = ActionStrip.Builder()
        
        // הוספת פעולות שינוי הלחץ
        actionStripBuilder.addAction(decreaseAction)
        actionStripBuilder.addAction(increaseAction)
        actionStripBuilder.addAction(startStopAction)
        
        // הוספת ערכים קבועים של לחץ יעד - התאמה לערכים שבמסך הראשי
        val pressureActions = listOf(8f, 10f, 12f, 16f, 20f, 30f, 38f)
        
        // הוספת כפתור אחד לפחות של לחץ קבוע
        actionStripBuilder.addAction(
            Action.Builder()
                .setTitle("${pressureActions[0]} PSI")
                .setOnClickListener {
                    targetPressure = pressureActions[0]
                    invalidate()
                }
                .build()
        )
        
        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("בקרת לחץ אוויר")
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStripBuilder.build())
            .build()
    }
    
    private fun toggleInflation() {
        Log.d(TAG, "toggleInflation() called, current state: $isInflating")
        isInflating = !isInflating
        
        if (isInflating) {
            currentScreenState = ScreenState.OPERATING
            tireDataAdapter.startInflation(targetPressure)
            
            if (currentPressure >= targetPressure) {
                isInflating = false
                currentScreenState = ScreenState.CONNECTED
                tireDataAdapter.stopInflation()
                showToast("לחץ היעד כבר הושג")
            }
        } else {
            currentScreenState = ScreenState.CONNECTED
            tireDataAdapter.stopInflation()
        }
        
        invalidate()
    }
    
    private fun showToast(message: String) {
        androidx.car.app.CarToast.makeText(carContext, message, androidx.car.app.CarToast.LENGTH_SHORT).show()
    }
    
    private fun startScan() {
        Log.d(TAG, "startScan() called")
        try {
            currentScreenState = ScreenState.SCANNING
            isScanning = true
            dotsCount = 0
            scanResults = emptyList()
            tireDataAdapter.startScan()
            
            coroutineScope.launch {
                tireDataAdapter.getScanResults().collect { devices ->
                    scanResults = devices
                    Log.d(TAG, "Received ${devices.size} scan results")
                    invalidate()
                }
            }
            
            coroutineScope.launch {
                kotlinx.coroutines.delay(30000)
                if (isScanning) {
                    stopScan()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting scan", e)
            isScanning = false
            currentScreenState = ScreenState.MAIN
            invalidate()
        }
    }
    
    private fun stopScan() {
        Log.d(TAG, "stopScan() called")
        if (isScanning) {
            isScanning = false
            tireDataAdapter.stopScan()
            invalidate()
        }
    }
    
    private fun getScanningTemplate(): Template {
        Log.d(TAG, "getScanningTemplate() called")
        
        if (scanResults.isNotEmpty()) {
            Log.d(TAG, "Showing ${scanResults.size} scan results")
            
            val listBuilder = ItemList.Builder()
            
            scanResults.forEach { device ->
                val title = device.name ?: "מכשיר לא ידוע"
                val address = device.address
                
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(title)
                        .addText(address)
                        .setOnClickListener {
                            Log.d(TAG, "Clicked on device: $title ($address)")
                            connectToDevice(device)
                        }
                        .build()
                )
            }
            
            return ListTemplate.Builder()
                .setTitle("מכשירים זמינים")
                .setHeaderAction(Action.BACK)
                .setSingleList(listBuilder.build())
                .build()
        } else {
            val loadingText = "סורק"
            val dots = ".".repeat((dotsCount % 4) + 1)
            
            val stopAction = Action.Builder()
                .setTitle("עצור")
                .setOnClickListener {
                    stopScan()
                    currentScreenState = ScreenState.MAIN
                    invalidate()
                }
                .build()
            
            val actionStrip = ActionStrip.Builder()
                .addAction(stopAction)
                .build()
            
            return MessageTemplate.Builder("$loadingText$dots\nמחפש מכשירים בסביבה...")
                .setTitle("סריקת מכשירים")
                .setHeaderAction(Action.BACK)
                .setActionStrip(actionStrip)
                .build()
        }
    }
    
    private fun connectToDevice(device: BleDevice) {
        Log.d(TAG, "connectToDevice() called for ${device.name}")
        tireDataAdapter.connectToDevice(device)
        
        currentScreenState = ScreenState.CONNECTED
        connectedDevice = device
        
        coroutineScope.launch {
            tireDataAdapter.getPressureUpdates().collect { pressure ->
                currentPressure = pressure
                
                if (isInflating && currentPressure >= targetPressure) {
                    isInflating = false
                    tireDataAdapter.stopInflation()
                    showToast("הגעת ללחץ היעד")
                }
                
                invalidate()
            }
        }
        
        coroutineScope.launch {
            tireDataAdapter.isConnected.collect { connected ->
                if (!connected && currentScreenState != ScreenState.MAIN) {
                    currentScreenState = ScreenState.MAIN
                    invalidate()
                }
            }
        }
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        Log.d(TAG, "onDestroy called")
        coroutineScope.cancel()
        lifecycle.removeObserver(this)
        super.onDestroy(owner)
    }
} 