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
    @Json(name = "MaxSecForStatus") val maxSecForStatus: String = "30",
    @Json(name = "SensorValueAt0PSI") val sensorValueAt0PSI: String = "0.228",
    @Json(name = "SensorValueAt50PSI") val sensorValueAt50PSI: String = "2.853",
    @Json(name = "MessureWait") val measureWait: String = "2",
    @Json(name = "Precision") val precision: String = "1",
    @Json(name = "CurrentPressure") val pressure: String = "0",
    @Json(name = "Software_VER") val softwareVersion: String = "",
    @Json(name = "IP") val ipAddress: String = "",
    @Json(name = "SimulationMode") val simulationMode: String = "0",
    @Json(name = "PressureCalcMethod") val pressureCalcMethod: String = "1",
    @Json(name = "SensorFullScalePSI") val sensorFullScalePSI: String = "50.0",
    @Json(name = "MaxPressurePSI") val maxPressurePSI: String = "50.0",
    @Json(name = "MaxValveOpenSec") val maxValveOpenSec: String = "120",
    @Json(name = "AccessPointPassword") val accessPointPassword: String = "12345678",
    @Json(name = "HasAccessCode") val hasAccessCode: String = "0"
) {
    // Convert to DeviceSettings
    fun toDeviceSettings(): DeviceSettings {
        return DeviceSettings(
            operationMode = operationMode,
            deviceName = deviceName.ifBlank { "esp32-AirUpDownController" },
            wifiSsid = wifiSsid,
            wifiPassword = wifiPassword,
            relayCompressorPin = relayCompressorPin.toIntOrNull()?.takeIf { it in 1..39 } ?: 16,
            relayFreePin = relayFreePin.toIntOrNull()?.takeIf { it in 1..39 } ?: 17,
            sensorPin = sensorPin.toIntOrNull()?.takeIf { it in 32..39 } ?: 34,
            maxSecForStatus = maxSecForStatus.toIntOrNull()?.takeIf { it in 1..300 } ?: 10,
            sensorValueAt0PSI = sensorValueAt0PSI.toFloatOrNull() ?: 410f,
            sensorValueAt50PSI = sensorValueAt50PSI.toFloatOrNull() ?: 3400f,
            measureWait = measureWait.toFloatOrNull()?.takeIf { it in 0.2f..60f } ?: 1f,
            precision = precision.toFloatOrNull()?.takeIf { it in 0.1f..10f } ?: 0.5f,
            currentPressure = pressure.toFloatOrNull() ?: 0f,
            softwareVersion = softwareVersion,
            ipAddress = ipAddress,
            simulationMode = simulationMode == "1" || simulationMode.lowercase() == "true",
            pressureCalcMethod = pressureCalcMethod.toIntOrNull()?.takeIf { it == 1 || it == 2 } ?: 1,
            sensorFullScalePSI = sensorFullScalePSI.toFloatOrNull()?.takeIf { it in 1f..300f } ?: 50f,
            maxPressurePSI = maxPressurePSI.toFloatOrNull()?.takeIf { it in 1f..200f } ?: 50f,
            maxValveOpenSec = maxValveOpenSec.toIntOrNull()?.takeIf { it in 10..3600 } ?: 120,
            accessPointPassword = accessPointPassword.takeIf { it.length >= 8 } ?: "12345678",
            hasAccessCode = hasAccessCode == "1" || hasAccessCode.lowercase() == "true"
        )
    }
} 
