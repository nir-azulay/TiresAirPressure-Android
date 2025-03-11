package com.example.tiresairpressuremanagement.data.repository

import android.content.Context
import com.example.tiresairpressuremanagement.data.ble.BleManager
import com.example.tiresairpressuremanagement.domain.models.BleCommand
import com.example.tiresairpressuremanagement.domain.models.BleDevice
import com.example.tiresairpressuremanagement.domain.models.DeviceStatus
import kotlinx.coroutines.flow.StateFlow

class BleRepository(context: Context) {
    private val bleManager = BleManager(context)
    
    val connectionState: StateFlow<Boolean> = bleManager.connectionState
    val deviceStatus: StateFlow<DeviceStatus?> = bleManager.deviceStatus
    val scanResults: StateFlow<List<BleDevice>> = bleManager.scanResults
    
    fun startScan() {
        bleManager.startScan()
    }
    
    fun stopScan() {
        bleManager.stopScan()
    }
    
    fun connect(address: String) {
        bleManager.connect(address)
    }
    
    fun disconnect() {
        bleManager.disconnect()
    }
    
    fun sendCommand(command: BleCommand) {
        bleManager.sendCommand(command)
    }
} 