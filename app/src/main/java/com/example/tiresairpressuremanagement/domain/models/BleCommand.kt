package com.example.tiresairpressuremanagement.domain.models

sealed class BleCommand {
    data class Stop(val command: Int = 1) : BleCommand()
    
    data class Start(
        val command: Int = 2,
        val targetPressure: Float
    ) : BleCommand()
    
    data class UpdateSettings(
        val command: Int = 3,
        val settings: DeviceSettings
    ) : BleCommand()
} 