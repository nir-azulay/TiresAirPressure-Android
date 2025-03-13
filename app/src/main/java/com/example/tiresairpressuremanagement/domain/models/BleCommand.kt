package com.example.tiresairpressuremanagement.domain.models

import com.example.tiresairpressuremanagement.utils.BleConstants
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

sealed class BleCommand {
    // Command constants
    companion object {
        const val CMD_STOP = 1
        const val CMD_START = 2
        const val CMD_UPDATE_SETTINGS = 3
        const val CMD_SIMULATION_MODE = 4
        const val CMD_LOG_LEVEL = 5
        const val CMD_GET_SETTINGS = 6
    }
    
    abstract val command: Int
    abstract val type: String
    
    data class Stop(
        override val command: Int = CMD_STOP,
        override val type: String = "command"
    ) : BleCommand()
    
    data class StartTargetPressure(
        override val command: Int = CMD_START,
        val targetPressure: Float,
        override val type: String = "command"
    ) : BleCommand()
    
    data class UpdateSettings(
        override val command: Int = CMD_UPDATE_SETTINGS,
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
        override val command: Int = CMD_SIMULATION_MODE,
        val enabled: Boolean,
        override val type: String = "command"
    ) : BleCommand()
    
    data class RequestSettings(
        override val command: Int = CMD_GET_SETTINGS,
        override val type: String = "command"
    ) : BleCommand()
} 