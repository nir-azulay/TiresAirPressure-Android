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

class BleRepository(
    context: Context,
    private val bleManager: BleManager? = null
) {
    // Use provided BleManager or create a new one if null
    private val _bleManager = bleManager ?: BleManager(context)
    private val scope = CoroutineScope(Dispatchers.Main)
    
    val connectionState: StateFlow<Boolean> = _bleManager.connectionState
        .map { 
            val isConnected = it == ConnectionState.Connected
            Log.d("BleRepository", "Connection state: $it, isConnected: $isConnected")
            isConnected 
        }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Lazily, false)
    
    val rawConnectionState: StateFlow<ConnectionState> = _bleManager.connectionState
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Lazily, ConnectionState.Disconnected)
    
    val deviceStatus: StateFlow<DeviceStatus?> = _bleManager.deviceStatus
    val deviceSettings: StateFlow<DeviceSettings?> = _bleManager.deviceSettings
    val scanResults: StateFlow<List<BleDevice>> = _bleManager.scanResults
    
    init {
        // Don't automatically connect to saved device
        // This will prevent reconnection when creating a new repository
        
        scope.launch {
            connectionState.collect { isConnected ->
                Log.d("BleRepository", "Connection state changed: $isConnected")
            }
        }
    }
    
    fun startScan() {
        _bleManager.startScan()
    }
    
    fun stopScan() {
        _bleManager.stopScan()
    }
    
    fun connect(address: String) {
        _bleManager.connect(address)
    }
    
    fun disconnect() {
        _bleManager.disconnect()
    }
    
    fun sendCommand(command: BleCommand) {
        _bleManager.sendCommand(command)
    }
    
    fun requestDeviceSettings() {
        _bleManager.requestDeviceSettings()
    }
    
    fun connectToSavedDevice() {
        _bleManager.connectToSavedDevice()
    }
} 