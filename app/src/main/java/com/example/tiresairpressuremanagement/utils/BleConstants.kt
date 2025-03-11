package com.example.tiresairpressuremanagement.utils

import java.util.UUID

object BleConstants {
    // Service UUID
    val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    
    // Characteristic UUIDs
    val NOTIFICATION_CHARACTERISTIC_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    val COMMAND_CHARACTERISTIC_UUID: UUID = UUID.fromString("e3223119-9445-4e96-a4a1-85358c4046a2")
    
    // Client Characteristic Config Descriptor UUID
    val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    
    // Operation Modes
    const val MODE_STOP = 1
    const val MODE_INFLATE = 2
    const val MODE_DEFLATE = 3
    const val MODE_SETTINGS = 4
    
    // Commands
    const val COMMAND_STOP = 1
    const val COMMAND_START = 2
    const val COMMAND_UPDATE_SETTINGS = 3
    
    // Predefined Pressure Values (PSI)
    val PREDEFINED_PRESSURES = listOf(8, 10, 12, 16, 20, 30, 38)
} 