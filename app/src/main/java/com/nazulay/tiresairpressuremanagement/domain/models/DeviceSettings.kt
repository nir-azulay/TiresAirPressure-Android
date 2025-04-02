package com.nazulay.tiresairpressuremanagement.domain.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeviceSettings(
    @Json(name = "operationMode") val operationMode: Int = 4,
    @Json(name = "DeviceName") val deviceName: String = "",
    @Json(name = "wifissid") val wifiSsid: String = "",
    @Json(name = "wifipassword") val wifiPassword: String = "",
    @Json(name = "RelayCompressorPin") val relayCompressorPin: Int = 0,
    @Json(name = "RelayFreePin") val relayFreePin: Int = 0,
    @Json(name = "SensorPin") val sensorPin: Int = 0,
    @Json(name = "AutoStart") val autoStart: Boolean = false,
    @Json(name = "MaxSecForStatus") val maxSecForStatus: Int = 30,
    @Json(name = "SensorValueAt0PSI") val sensorValueAt0PSI: Int = 0,
    @Json(name = "SensorValueAt50PSI") val sensorValueAt50PSI: Int = 1023,
    @Json(name = "MessureWait") val measureWait: Float = 2f,
    @Json(name = "Precision") val precision: Float = 1f,
    @Json(name = "BTPinCode") val btPinCode: String = "1234",
    @Json(name = "CurrentPressure") val currentPressure: Float = 0f,
    @Json(name = "Software_VER") val softwareVersion: String = "",
    @Json(name = "IP") val ipAddress: String = "",
    @Json(name = "SimulationMode") val simulationMode: Boolean = false,
    @Json(name = "PredefinedPressure1") val predefinedPressure1: Float = 8f,
    @Json(name = "PredefinedPressure2") val predefinedPressure2: Float = 10f,
    @Json(name = "PredefinedPressure3") val predefinedPressure3: Float = 12f,
    @Json(name = "PredefinedPressure4") val predefinedPressure4: Float = 16f,
    @Json(name = "PredefinedPressure5") val predefinedPressure5: Float = 20f,
    @Json(name = "PredefinedPressure6") val predefinedPressure6: Float = 30f,
    @Json(name = "PredefinedPressure7") val predefinedPressure7: Float = 38f,
    @Json(name = "PredefinedPressure8") val predefinedPressure8: Float = 40f
)

enum class PressureUnit {
    PSI,
    BAR,
    KPA
}

enum class TemperatureUnit {
    CELSIUS,
    FAHRENHEIT
} 
