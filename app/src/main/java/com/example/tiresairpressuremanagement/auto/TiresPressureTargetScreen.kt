package com.example.tiresairpressuremanagement.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import com.example.tiresairpressuremanagement.data.ble.BleManager
import com.example.tiresairpressuremanagement.domain.models.BleCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.example.tiresairpressuremanagement.utils.BleConstants

/**
 * מסך בחירת לחץ יעד לניפוח הצמיג.
 * המסך מציג את ערכי הלחץ המוגדרים מראש ומאפשר למשתמש לבחור לחץ יעד.
 */
class TiresPressureTargetScreen(
    carContext: CarContext,
    private val bleManager: BleManager
) : Screen(carContext) {
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    
    // ערכי לחץ מוגדרים מראש (בהתאם לדרישות המשתמש)
    private val predefinedPressures = listOf(8, 10, 12, 16, 20, 30, 38, 40)
    
    override fun onGetTemplate(): Template {
        // יצירת תבנית רשימה עם ערכי לחץ קבועים מראש
        val itemListBuilder = ItemList.Builder()
        
        // הוספת כל ערכי הלחץ המוגדרים מראש לרשימה
        predefinedPressures.forEach { pressure ->
            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle("$pressure PSI")
                    .setOnClickListener { 
                        startInflation(pressure.toFloat()) 
                    }
                    .build()
            )
        }
        
        // בניית תבנית המסך עם רשימת הערכים
        return ListTemplate.Builder()
            .setSingleList(itemListBuilder.build())
            .setTitle(carContext.getString(com.example.tiresairpressuremanagement.R.string.select_target_pressure))
            .setHeaderAction(Action.BACK)
            .build()
    }
    
    /**
     * פונקציה להתחלת ניפוח עם לחץ יעד מוגדר.
     * 
     * @param targetPressure לחץ היעד הרצוי (ב-PSI)
     */
    private fun startInflation(targetPressure: Float) {
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
                // אם מסיבה כלשהי המסך הראשי לא נמצא, נחזור למסך הראשון בסטק
                screenManager.popToRoot()
            }
        }
    }
} 