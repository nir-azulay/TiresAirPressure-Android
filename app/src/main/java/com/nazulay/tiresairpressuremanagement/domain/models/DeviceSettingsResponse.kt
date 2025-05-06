package com.nazulay.tiresairpressuremanagement.domain.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeviceSettingsResponse(
    @Json(name = "operationMode") val operationMode: Int = 4,
    @Json(name = "DeviceName") val deviceName: String = "",
    @Json(name = "wifissid") val wifiSsid: String = "",
    @Json(name = "wifipassword") val wifiPassword: String = "",
    @Json(name = "RelayCompressorPin") val relayCompressorPin: String = "0",
    @Json(name = "RelayFreePin") val relayFreePin: String = "0",
    @Json(name = "SensorPin") val sensorPin: String = "0",
    @Json(name = "AutoStart") val autoStart: String = "0",
    @Json(name = "MaxSecForStatus") val maxSecForStatus: String = "30",
    @Json(name = "SensorValueAt0PSI") val sensorValueAt0PSI: String = "0.228",
    @Json(name = "SensorValueAt50PSI") val sensorValueAt50PSI: String = "2.853",
    @Json(name = "MessureWait") val measureWait: String = "2",
    @Json(name = "Precision") val precision: String = "1",
    @Json(name = "BTPinCode") val btPinCode: String = "1234",
    @Json(name = "CurrentPressure") val pressure: String = "0",
    @Json(name = "Software_VER") val softwareVersion: String = "",
    @Json(name = "IP") val ipAddress: String = "",
    @Json(name = "SimulationMode") val simulationMode: String = "0"
) {
    // Convert to DeviceSettings
    fun toDeviceSettings(): DeviceSettings {
        return DeviceSettings(
            operationMode = operationMode,
            deviceName = deviceName,
            wifiSsid = wifiSsid,
            wifiPassword = wifiPassword,
            relayCompressorPin = relayCompressorPin.toIntOrNull() ?: 0,
            relayFreePin = relayFreePin.toIntOrNull() ?: 0,
            sensorPin = sensorPin.toIntOrNull() ?: 0,
            autoStart = autoStart == "1" || autoStart.lowercase() == "true",
            maxSecForStatus = maxSecForStatus.toIntOrNull() ?: 30,
            sensorValueAt0PSI = sensorValueAt0PSI.toFloatOrNull() ?: 0.228f,
            sensorValueAt50PSI = sensorValueAt50PSI.toFloatOrNull() ?: 2.853f,
            measureWait = measureWait.toFloatOrNull() ?: 2f,
            precision = precision.toFloatOrNull() ?: 1f,
            btPinCode = btPinCode,
            currentPressure = pressure.toFloatOrNull() ?: 0f,
            softwareVersion = softwareVersion,
            ipAddress = ipAddress,
            simulationMode = simulationMode == "1" || simulationMode.lowercase() == "true"
        )
    }
} 
