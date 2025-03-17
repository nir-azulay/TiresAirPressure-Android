package com.example.tiresairpressuremanagement.auto

import android.content.Context
import android.util.Log
import com.example.tiresairpressuremanagement.domain.models.BleDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * גרסה מינימלית של מתאם הנתונים עבור Android Auto.
 * גרסה זו מספקת רק פונקציונליות בסיסית ללא תלות ברכיבים חיצוניים.
 */
class TireDataAdapter(private val context: Context) {
    private val TAG = "TireDataAdapter"
    
    // תוצאות הסריקה - כרגע יוחזרו נתונים סטטיים
    private val _scanResults = MutableStateFlow<List<BleDevice>>(emptyList())
    
    /**
     * התחלת איסוף נתונים - מימוש ריק לעת עתה
     */
    fun startDataCollection() {
        try {
            Log.d(TAG, "startDataCollection() called - stub implementation")
        } catch (e: Exception) {
            Log.e(TAG, "Error in startDataCollection", e)
        }
    }
    
    /**
     * עצירת איסוף נתונים - מימוש ריק לעת עתה
     */
    fun stopDataCollection() {
        try {
            Log.d(TAG, "stopDataCollection() called - stub implementation")
        } catch (e: Exception) {
            Log.e(TAG, "Error in stopDataCollection", e)
        }
    }
    
    /**
     * מתחיל סריקה של מכשירי BLE
     */
    fun startScan() {
        try {
            Log.d(TAG, "Starting scan (stub implementation)")
            
            // סימולציה של קבלת תוצאות לאחר זמן מה
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val mockResults = listOf(
                    BleDevice("מכשיר לחץ אוויר 1", "00:11:22:33:44:55", -65),
                    BleDevice("צמיג חכם", "AA:BB:CC:DD:EE:FF", -72)
                )
                Log.d(TAG, "Generated mock scan results with ${mockResults.size} devices")
                _scanResults.value = mockResults
            }, 3000) // המתן 3 שניות לפני הצגת תוצאות מדומות
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting scan", e)
        }
    }
    
    /**
     * מפסיק את הסריקה
     */
    fun stopScan() {
        try {
            Log.d(TAG, "Stopping scan (stub implementation)")
            // אין צורך לעשות כלום בגרסה זו
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        }
    }
    
    /**
     * מחזיר את תוצאות הסריקה
     */
    fun getScanResults(): StateFlow<List<BleDevice>> {
        return _scanResults
    }
} 