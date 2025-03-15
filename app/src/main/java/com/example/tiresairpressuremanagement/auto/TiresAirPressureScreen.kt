package com.example.tiresairpressuremanagement.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.tiresairpressuremanagement.R
import com.example.tiresairpressuremanagement.data.TireData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * המסך הראשי של האפליקציה ב-Android Auto.
 * מסך זה מציג את לחץ האוויר בצמיגים ומאפשר לבצע פעולות בסיסיות.
 */
class TiresAirPressureScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {
    private val TAG = "TiresAirPressureScreen"
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val tireDataAdapter = TireDataAdapter(carContext)
    private var tiresData: List<TireData> = emptyList()
    private var isConnected = false
    private var isScanning = false
    
    init {
        lifecycle.addObserver(this)
        Log.d(TAG, "Screen initialized")
    }
    
    override fun onStart(owner: LifecycleOwner) {
        try {
            super.onStart(owner)
            Log.d(TAG, "onStart called")
            startDataCollection()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStart", e)
        }
    }
    
    override fun onStop(owner: LifecycleOwner) {
        try {
            super.onStop(owner)
            Log.d(TAG, "onStop called")
            stopDataCollection()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStop", e)
        }
    }
    
    override fun onGetTemplate(): Template {
        Log.d(TAG, "onGetTemplate called, isScanning=$isScanning, isConnected=$isConnected")
        try {
            // פשוט החזר תבנית בסיסית לבדיקה
            return MessageTemplate.Builder("Tire Pressure Management")
                .setTitle("Tire Pressure App")
                .setHeaderAction(Action.BACK)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onGetTemplate", e)
            // במקרה של שגיאה, החזר תבנית פשוטה
            return MessageTemplate.Builder("Error loading data")
                .setTitle("Tire Pressure Management")
                .build()
        }
    }
    
    private fun getLoadingTemplate(): Template {
        Log.d(TAG, "Creating loading template")
        return MessageTemplate.Builder(carContext.getString(R.string.scanning_for_devices))
            .setTitle(carContext.getString(R.string.scanning_for_devices))
            .build()
    }
    
    private fun getDisconnectedTemplate(): Template {
        Log.d(TAG, "Creating disconnected template")
        try {
            val row = Row.Builder()
                .setTitle(carContext.getString(R.string.not_connected))
                .addText(carContext.getString(R.string.tap_to_connect))
                .build()
                
            val itemList = ItemList.Builder()
                .addItem(row)
                .build()
                
            return ListTemplate.Builder()
                .setTitle(carContext.getString(R.string.app_name))
                .setSingleList(itemList)
                .setHeaderAction(Action.BACK)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating disconnected template", e)
            return MessageTemplate.Builder("Not connected")
                .setTitle("Tire Pressure Management")
                .build()
        }
    }
    
    private fun getConnectedTemplate(): Template {
        Log.d(TAG, "Creating connected template with ${tiresData.size} tires")
        try {
            val listBuilder = ItemList.Builder()
            
            // אם אין נתונים, הוסף שורה אחת עם הודעה
            if (tiresData.isEmpty()) {
                val row = Row.Builder()
                    .setTitle("No tire data available")
                    .build()
                listBuilder.addItem(row)
            } else {
                // הוסף שורה עבור כל צמיג
                tiresData.forEachIndexed { index, tireData ->
                    val tirePosition = when (index) {
                        0 -> carContext.getString(R.string.front_left)
                        1 -> carContext.getString(R.string.front_right)
                        2 -> carContext.getString(R.string.rear_left)
                        3 -> carContext.getString(R.string.rear_right)
                        else -> "Tire ${index + 1}"
                    }
                    
                    val pressureText = "${tireData.pressure} PSI"
                    val temperatureText = "${tireData.temperature}°C"
                    
                    val row = Row.Builder()
                        .setTitle(tirePosition)
                        .addText(pressureText)
                        .addText(temperatureText)
                        .build()
                        
                    listBuilder.addItem(row)
                }
            }
            
            return ListTemplate.Builder()
                .setTitle(carContext.getString(R.string.tire_pressure))
                .setSingleList(listBuilder.build())
                .setHeaderAction(Action.BACK)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating connected template", e)
            return MessageTemplate.Builder("Error displaying tire data")
                .setTitle("Tire Pressure Management")
                .build()
        }
    }
    
    private fun startDataCollection() {
        Log.d(TAG, "Starting data collection")
        try {
            isScanning = true
            invalidate()
            
            coroutineScope.launch {
                try {
                    delay(2000) // סימולציה של סריקה
                    isScanning = false
                    
                    // התחל לאסוף נתונים
                    tireDataAdapter.startDataCollection()
                    
                    // עקוב אחרי שינויים בנתונים
                    launch {
                        try {
                            tireDataAdapter.tiresData.collectLatest { data ->
                                tiresData = data
                                Log.d(TAG, "Received tire data: ${data.size} items")
                                invalidate()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error collecting tire data", e)
                        }
                    }
                    
                    // עקוב אחרי שינויים בסטטוס החיבור
                    launch {
                        try {
                            tireDataAdapter.isConnected.collectLatest { connected ->
                                isConnected = connected
                                Log.d(TAG, "Connection status changed: $connected")
                                invalidate()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error collecting connection status", e)
                        }
                    }
                    
                    // התחל לעדכן את הנתונים כל 5 שניות
                    while (isActive && isConnected) {
                        try {
                            delay(5000)
                            tireDataAdapter.updateTireData()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating tire data", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in data collection coroutine", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting data collection", e)
        }
    }
    
    private fun stopDataCollection() {
        Log.d(TAG, "Stopping data collection")
        try {
            tireDataAdapter.stopDataCollection()
            coroutineScope.coroutineContext.cancelChildren()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping data collection", e)
        }
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        Log.d(TAG, "onDestroy called")
        try {
            coroutineScope.cancel()
            lifecycle.removeObserver(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }
} 