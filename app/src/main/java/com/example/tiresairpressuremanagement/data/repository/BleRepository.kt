package com.example.tiresairpressuremanagement.data.repository

import android.content.Context
import com.example.tiresairpressuremanagement.data.ble.BleManager
import com.example.tiresairpressuremanagement.data.ble.ConnectionState
import com.example.tiresairpressuremanagement.domain.models.BleCommand
import com.example.tiresairpressuremanagement.domain.models.BleDevice
import com.example.tiresairpressuremanagement.domain.models.DeviceSettings
import com.example.tiresairpressuremanagement.domain.models.DeviceStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import android.util.Log
import kotlinx.coroutines.launch

class BleRepository(context: Context) {
    private val bleManager = BleManager(context)
    private val scope = CoroutineScope(Dispatchers.Main)
    
    val connectionState: StateFlow<Boolean> = bleManager.connectionState
        .map { 
            val isConnected = it == ConnectionState.Connected
            Log.d("BleRepository", "Connection state: $it, isConnected: $isConnected")
            isConnected 
        }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Lazily, false)
    
    val rawConnectionState: StateFlow<ConnectionState> = bleManager.connectionState
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Lazily, ConnectionState.Disconnected)
    
    val deviceStatus: StateFlow<DeviceStatus?> = bleManager.deviceStatus
    val deviceSettings: StateFlow<DeviceSettings?> = bleManager.deviceSettings
    val scanResults: StateFlow<List<BleDevice>> = bleManager.scanResults
    
    init {
        connectToSavedDevice()
        
        scope.launch {
            connectionState.collect { isConnected ->
                Log.d("BleRepository", "Connection state changed: $isConnected")
            }
        }
    }
    
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
    
    fun requestDeviceSettings() {
        bleManager.requestDeviceSettings()
    }
    
    fun connectToSavedDevice() {
        bleManager.connectToSavedDevice()
    }
} 