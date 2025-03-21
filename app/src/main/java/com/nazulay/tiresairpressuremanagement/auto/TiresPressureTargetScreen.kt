package com.nazulay.tiresairpressuremanagement.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import com.nazulay.tiresairpressuremanagement.data.ble.BleManager
import com.nazulay.tiresairpressuremanagement.domain.models.BleCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.nazulay.tiresairpressuremanagement.utils.BleConstants
import androidx.core.graphics.drawable.IconCompat
import com.nazulay.tiresairpressuremanagement.R
import android.util.Log

/**
 * מסך בחירת לחץ יעד לניפוח הצמיג.
 * המסך מציג את ערכי הלחץ המוגדרים מראש ומאפשר למשתמש לבחור לחץ יעד.
 */
class TiresPressureTargetScreen(
    carContext: CarContext,
    private val bleManager: BleManager
) : Screen(carContext) {
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private val TAG = "TiresPressureTargetScreen"
    
    // ערכי לחץ מוגדרים מראש (בהתאם לדרישות המשתמש)
    private val predefinedPressures = listOf(8, 10, 12, 16, 20, 30, 38, 40)
    
    override fun onGetTemplate(): Template {
        Log.d(TAG, "Building target pressure selection template")
        
        // יצירת תבנית רשימה עם ערכי לחץ קבועים מראש
        val itemListBuilder = ItemList.Builder()
        
        // הוספת כל ערכי הלחץ המוגדרים מראש לרשימה
        predefinedPressures.forEach { pressure ->
            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle(String.format(carContext.getString(R.string.auto_pressure_format), pressure.toFloat()))
                    // Adding an icon is required for each row in Android Auto
                    .setImage(
                        CarIcon.Builder(
                            IconCompat.createWithResource(
                                carContext,
                                android.R.drawable.ic_menu_compass
                            )
                        ).build()
                    )
                    .setOnClickListener { 
                        Log.d(TAG, "Selected pressure: $pressure")
                        startInflation(pressure.toFloat()) 
                    }
                    .build()
            )
        }
        
        // בניית תבנית המסך עם רשימת הערכים
        return ListTemplate.Builder()
            .setSingleList(itemListBuilder.build())
            .setTitle(carContext.getString(com.nazulay.tiresairpressuremanagement.R.string.select_target_pressure))
            .setHeaderAction(Action.BACK)
            .build()
    }
    
    /**
     * פונקציה להתחלת ניפוח עם לחץ יעד מוגדר.
     * 
     * @param targetPressure לחץ היעד הרצוי (ב-PSI)
     */
    private fun startInflation(targetPressure: Float) {
        Log.d(TAG, "Starting inflation with target pressure: $targetPressure")
        
        coroutineScope.launch {
            // שליחת פקודת התחלת ניפוח עם לחץ היעד
            bleManager.sendCommand(BleCommand.StartTargetPressure(targetPressure = targetPressure))
            
            // חזרה למסך הראשי עם עדכון שהתחיל תהליך ניפוח
            screenManager.pop()
            
            try {
                // עדכון המסך הראשי שהניפוח פעיל (יתבצע בחזרה למסך הראשי)
                val mainScreen = screenManager.top as? TiresPressureMainScreen
                mainScreen?.startInflation(targetPressure)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating main screen: ${e.message}")
                // אם מסיבה כלשהי המסך הראשי לא נמצא, נחזור למסך הראשון בסטק
                screenManager.popToRoot()
            }
        }
    }
} 
