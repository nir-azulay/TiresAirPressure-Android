package com.example.tiresairpressuremanagement.domain.models

import com.example.tiresairpressuremanagement.utils.BleConstants
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

sealed class BleCommand {
    abstract val command: String
    abstract val type: String
    
    data class Stop(
        override val command: String = "stop",
        override val type: String = "command"
    ) : BleCommand()
    
    data class StartTargetPressure(
        override val command: String = "start",
        val targetPressure: Float,
        override val type: String = "command"
    ) : BleCommand()
    
    data class UpdateSettings(
        override val command: String = "updateSettings",
        val deviceName: String,
        val wifiSsid: String,
        val wifiPassword: String,
        val relayCompressorPin: Int,
        val relayFreePin: Int,
        val sensorPin: Int,
        val autoStart: Boolean,
        val maxSecForStatus: Int,
        val sensorValueAt0PSI: Int,
        val sensorValueAt50PSI: Int,
        val measureWait: Float,
        val precision: Float,
        val btPinCode: String,
        override val type: String = "settings"
    ) : BleCommand()
    
    data class SimulationMode(
        override val command: String = "simulationMode",
        val enabled: Boolean,
        override val type: String = "command"
    ) : BleCommand()
    
    data class RequestSettings(
        override val command: String = "getSettings",
        override val type: String = "command"
    ) : BleCommand()
} 