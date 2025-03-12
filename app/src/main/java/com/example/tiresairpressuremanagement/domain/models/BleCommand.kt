package com.example.tiresairpressuremanagement.domain.models

import com.example.tiresairpressuremanagement.utils.BleConstants
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

sealed class BleCommand {
    abstract val command: Int
    abstract val type: String
    
    @JsonClass(generateAdapter = true)
    data class Stop(
        @Json(name = "command") override val command: Int = 1,
        @Json(name = "type") override val type: String = "stop"
    ) : BleCommand()
    
    @JsonClass(generateAdapter = true)
    data class StartTargetPressure(
        @Json(name = "command") override val command: Int = 2,
        @Json(name = "targetPressure") val targetPressure: Float,
        @Json(name = "type") override val type: String = "startTargetPressure"
    ) : BleCommand()
    
    @JsonClass(generateAdapter = true)
    data class UpdateSettings(
        @Json(name = "command") override val command: Int = 3,
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
        @Json(name = "MessureWait") val measureWait: Int,
        @Json(name = "Precision") val precision: Int,
        @Json(name = "BTPinCode") val btPinCode: String,
        @Json(name = "type") override val type: String = "updateSettings"
    ) : BleCommand()

    @JsonClass(generateAdapter = true)
    data class SimulationMode(
        @Json(name = "command") override val command: Int = 4,
        @Json(name = "enabled") val enabled: Boolean,
        @Json(name = "type") override val type: String = "simulationMode"
    ) : BleCommand()
} 