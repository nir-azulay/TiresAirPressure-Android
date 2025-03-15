package com.example.tiresairpressuremanagement.auto

import android.content.Context
import android.util.Log
import com.example.tiresairpressuremanagement.data.TireData
import com.example.tiresairpressuremanagement.data.ble.BleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

/**
 * מחלקת מתאם שמקשרת בין נתוני ה-BLE לבין ממשק ה-Android Auto.
 * מחלקה זו אחראית על איסוף הנתונים מהחיישנים והעברתם לממשק המשתמש של Android Auto.
 */
class TireDataAdapter(private val context: Context) {
    private val TAG = "TireDataAdapter"
    
    private val _tiresData = MutableStateFlow<List<TireData>>(emptyList())
    val tiresData: StateFlow<List<TireData>> = _tiresData
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected
    
    /**
     * מתחיל את איסוף הנתונים מהחיישנים.
     * במימוש אמיתי, כאן היינו מתחברים למכשיר ה-BLE ומתחילים לקבל נתונים.
     */
    fun startDataCollection() {
        try {
            Log.d(TAG, "Starting data collection")
            // בשלב זה, נשתמש בנתונים לדוגמה
            _tiresData.value = listOf(
                TireData(32.5f, 25.0f, 100),
                TireData(33.0f, 26.0f, 100),
                TireData(32.0f, 25.5f, 100),
                TireData(32.8f, 25.2f, 100)
            )
            _isConnected.value = true
            Log.d(TAG, "Data collection started with ${_tiresData.value.size} tires")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting data collection", e)
        }
    }
    
    /**
     * מעדכן את נתוני הצמיגים.
     * במימוש אמיתי, כאן היינו מקבלים נתונים מהחיישנים.
     */
    fun updateTireData() {
        try {
            // זו רק סימולציה פשוטה
            val currentData = _tiresData.value
            if (currentData.isNotEmpty()) {
                _tiresData.value = currentData.map { 
                    it.copy(
                        pressure = it.pressure + Random.nextFloat() - 0.5f,
                        temperature = it.temperature + Random.nextFloat() * 2 - 1f
                    )
                }
                Log.d(TAG, "Tire data updated")
            } else {
                Log.d(TAG, "No tire data to update")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating tire data", e)
        }
    }
    
    /**
     * מפסיק את איסוף הנתונים מהחיישנים.
     */
    fun stopDataCollection() {
        try {
            Log.d(TAG, "Stopping data collection")
            _isConnected.value = false
            Log.d(TAG, "Data collection stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping data collection", e)
        }
    }
} 