package com.nazulay.tiresairpressuremanagement.domain.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeviceSettings(
    @Json(name = "operationMode") val operationMode: Int = 4,
    @Json(name = "DeviceName") val deviceName: String,
    @Json(name = "wifissid") val wifiSsid: String,
    @Json(name = "wifipassword") val wifiPassword: String,
    @Json(name = "RelayCompressorPin") val relayCompressorPin: Int,
    @Json(name = "RelayFreePin") val relayFreePin: Int,
    @Json(name = "SensorPin") val sensorPin: Int,
    @Json(name = "AutoStart") val autoStart: Boolean,
    @Json(name = "MaxSecForStatus") val maxSecForStatus: Int,
    @Json(name = "SensorValueAt0PSI") val sensorValueAt0PSI: Int,
    @Json(name = "SensorValueAt50PSI") val sensorValueAt50PSI: Int,
    @Json(name = "MessureWait") val measureWait: Float,
    @Json(name = "Precision") val precision: Float,
    @Json(name = "BTPinCode") val btPinCode: String,
    @Json(name = "CurrentPressure") val currentPressure: Float,
    @Json(name = "Software_VER") val softwareVersion: String,
    @Json(name = "IP") val ipAddress: String,
    @Json(name = "SimulationMode") val simulationMode: Boolean
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
