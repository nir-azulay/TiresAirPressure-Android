package com.nazulay.tiresairpressuremanagement.data.repository

import android.content.Context
import android.util.Log
import com.nazulay.tiresairpressuremanagement.data.ble.BleManager
import com.nazulay.tiresairpressuremanagement.data.local.SavedDevicesManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.nazulay.tiresairpressuremanagement.domain.models.BleCommand
import com.nazulay.tiresairpressuremanagement.domain.models.BleDevice
import com.nazulay.tiresairpressuremanagement.domain.models.DeviceSettings
import com.nazulay.tiresairpressuremanagement.domain.models.DeviceStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.nazulay.tiresairpressuremanagement.data.ble.ConnectionState

class BleRepository(
    private val context: Context,
    private val bleManager: BleManager? = null
) {
    private val savedDevicesManager = SavedDevicesManager(context)
    private var isAutoConnecting = false
    
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Connection state - Restore internal state
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Device settings
    private val _deviceSettings = MutableStateFlow<DeviceSettings?>(null)
    val deviceSettings: StateFlow<DeviceSettings?> = _deviceSettings.asStateFlow()

    // Device status
    private val _deviceStatus = MutableStateFlow<DeviceStatus?>(null)
    val deviceStatus: StateFlow<DeviceStatus?> = _deviceStatus.asStateFlow()

    // Scan results - Restore internal state (if needed, or keep direct)
    // For simplicity, let's keep scanResults direct for now
    val scanResults: StateFlow<List<BleDevice>> = bleManager?.scanResults 
        ?: MutableStateFlow(emptyList())
    
    init {
        Log.d(TAG, "Initializing BleRepository")
        // Restore observing all states from BleManager
        bleManager?.let { manager ->
            repositoryScope.launch {
                Log.d(TAG, "Starting to collect connectionState from BleManager")
                manager.connectionState.collect { stateFromManager ->
                    Log.d(TAG, "Repository received state from Manager: $stateFromManager")
                    if (_connectionState.value != stateFromManager) {
                        _connectionState.value = stateFromManager
                        Log.d(TAG, "Repository updated its connectionState to: $stateFromManager")
                    } else {
                        Log.d(TAG, "Repository state already matches Manager state ($stateFromManager), no update needed.")
                    }
                }
            }
            
            repositoryScope.launch {
                Log.d(TAG, "Starting to collect deviceStatus from BleManager")
                manager.deviceStatus.collect { status ->
                    _deviceStatus.value = status
                }
            }
            
            repositoryScope.launch {
                Log.d(TAG, "Starting to collect deviceSettings from BleManager")
                manager.deviceSettings.collect { settings ->
                    _deviceSettings.value = settings
                }
            }
        } ?: run {
            Log.w(TAG, "BleManager is null in BleRepository init, cannot observe states.")
        }
    }
    
    fun startScan() {
        bleManager?.startScan()
    }
    
    fun stopScan() {
        bleManager?.stopScan()
    }
    
    fun connect(address: String) {
        bleManager?.connect(address)
    }
    
    fun disconnect() {
        bleManager?.disconnect()
    }
    
    fun sendCommand(command: BleCommand) {
        bleManager?.sendCommand(command)
    }
    
    fun requestDeviceSettings() {
        bleManager?.requestDeviceSettings()
    }
    
    fun connectToSavedDevice() {
        bleManager?.connectToSavedDevice()
    }

    fun saveConnectedDevice(address: String, name: String) {
        savedDevicesManager.saveDevice(address, name)
    }

    fun getSavedDevices(): List<SavedDevicesManager.SavedDevice> {
        return savedDevicesManager.getSavedDevices()
    }

    suspend fun connectToSavedDevices() {
        if (isAutoConnecting) {
            Log.d("BleRepository", "Already attempting to connect to saved devices")
            return
        }

        isAutoConnecting = true
        val savedDevices = getSavedDevices()
        
        for (device in savedDevices) {
            Log.d("BleRepository", "Attempting to connect to saved device: ${device.name} (${device.address})")
            try {
                connect(device.address)
                delay(5000) // Wait 5 seconds between connection attempts
                
                // Check the internal state flow's value again
                if (_connectionState.value == ConnectionState.Connected) { 
                    Log.d("BleRepository", "Successfully connected to saved device: ${device.name}")
                    break
                }
            } catch (e: Exception) {
                Log.e("BleRepository", "Failed to connect to saved device: ${device.name}", e)
            }
        }
        
        isAutoConnecting = false
    }

    fun onDeviceConnected(device: BluetoothDevice) {
        repositoryScope.launch {
            Log.d("BleRepository", "Device connected: ${device.name} (${device.address})")
            saveConnectedDevice(device.address, device.name ?: "Unknown Device")
        }
    }

    companion object {
        private const val TAG = "BleRepository"
    }
} 
