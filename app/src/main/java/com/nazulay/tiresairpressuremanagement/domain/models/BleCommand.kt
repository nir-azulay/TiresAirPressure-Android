package com.nazulay.tiresairpressuremanagement.domain.models

import com.nazulay.tiresairpressuremanagement.utils.BleConstants
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// See BLE_Communication_Documentation.md for BLE command protocol details, especially for debug mode (command 7)
sealed class BleCommand {
    // Command constants
    companion object {
        const val CMD_STOP = 1
        const val CMD_START = 2
        const val CMD_UPDATE_SETTINGS = 3
        const val CMD_SIMULATION_MODE = 4
        const val CMD_LOG_LEVEL = 5
        const val CMD_GET_SETTINGS = 6
        const val CMD_REQUEST_STATUS = 7
        const val CMD_AUTHENTICATE = 8
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
        val maxSecForStatus: Int,
        val sensorValueAt0PSI: Float,
        val sensorValueAt50PSI: Float,
        val measureWait: Float,
        val precision: Float,
        val simulationMode: Boolean,
        val pressureCalcMethod: Int,
        val sensorFullScalePSI: Float = 50f,
        val maxPressurePSI: Float = 50f,
        val maxValveOpenSec: Int = 120,
        val accessPointPassword: String = "12345678",
        val accessCode: String = "",
        val clearAccessCode: Boolean = false,
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
    
    data class RequestStatus(
        override val command: Int = CMD_REQUEST_STATUS,
        override val type: String = "command"
    ) : BleCommand()
    
    data class DebugMode(
        override val command: Int = 7,
        val enabled: Boolean,
        override val type: String = "command"
    ) : BleCommand()

    data class Authenticate(
        val accessCode: String,
        override val command: Int = CMD_AUTHENTICATE,
        override val type: String = "command"
    ) : BleCommand()
} 
