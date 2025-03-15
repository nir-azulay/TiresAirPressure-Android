package com.example.tiresairpressuremanagement.data

/**
 * מחלקה המייצגת את נתוני הצמיג.
 * 
 * @param pressure לחץ האוויר בצמיג (ב-PSI)
 * @param temperature טמפרטורת הצמיג (במעלות צלזיוס)
 * @param batteryLevel רמת הסוללה של החיישן (באחוזים)
 */
data class TireData(
    val pressure: Float,
    val temperature: Float,
    val batteryLevel: Int
) 