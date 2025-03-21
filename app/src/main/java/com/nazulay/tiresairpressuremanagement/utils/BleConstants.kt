package com.nazulay.tiresairpressuremanagement.utils

import java.util.UUID
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic

object BleConstants {
    // Service
    val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    
    // Characteristics
    val NOTIFICATION_CHARACTERISTIC_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    val COMMAND_CHARACTERISTIC_UUID: UUID = UUID.fromString("e3223119-9445-4e96-a4a1-85358c4046a2")
    
    // Client Configuration Descriptor
    // The Client Configuration Descriptor UUID is a standard BLE UUID used to enable/disable notifications
    // When we want to receive notifications from the device about pressure changes, we need to write to this descriptor
    // This UUID is standardized by the Bluetooth SIG and is the same for all BLE devices
    val CLIENT_CONFIG_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    
    const val MTU_SIZE = 517
    
    // Operation Modes
    const val MODE_STOP = 0
    const val MODE_INFLATE = 1
    const val MODE_DEFLATE = 2
    const val MODE_SETTINGS = 4
    
    // Commands
    const val COMMAND_STOP = 1
    const val COMMAND_START = 2
    const val COMMAND_UPDATE_SETTINGS = 3
    
    // Predefined Pressure Values (PSI)
    val PREDEFINED_PRESSURES = listOf(8, 12, 16, 20, 30, 35, 38)
    
    // Scan Settings
    const val SCAN_PERIOD = 10000L // 10 seconds
    const val SCAN_INTERVAL = 5000L // 5 seconds
} 
