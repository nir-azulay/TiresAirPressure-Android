package com.nazulay.tiresairpressuremanagement.domain.models

data class AppSettings(
    val themeMode: Int = 0, // 0: System, 1: Light, 2: Dark
    val simulationMode: Boolean = false,
    val predefinedPressures: List<Float> = listOf(8f, 10f, 12f, 16f, 20f, 30f, 38f, 40f),
    val minPressureCount: Int = 4,
    val maxPressureCount: Int = 8
) 