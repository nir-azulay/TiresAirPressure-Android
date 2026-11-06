package com.nazulay.tiresairpressuremanagement.domain.models

data class AppSettings(
    val themeMode: Int = 0, // 0: System, 1: Light, 2: Dark
    val simulationMode: Boolean = false,
    val predefinedPressures: List<Float> = listOf(8f, 10f, 12f, 16f, 20f, 30f, 38f, 40f),
    val minPressureCount: Int = 4,
    val maxPressureCount: Int = 8,
    val debugMode: Boolean = false,
    val fontSizeScale: AppFontSize = AppFontSize.DEFAULT
)

/** Matches iOS `AppFontSize` scale factors. */
enum class AppFontSize(val key: String, val scale: Float) {
    SMALLEST("smallest", 0.72f),
    SMALLER("smaller", 0.85f),
    DEFAULT("default", 1.00f),
    BIGGER("bigger", 1.15f),
    BIGGEST("biggest", 1.30f);

    companion object {
        fun fromKey(key: String?): AppFontSize =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
} 