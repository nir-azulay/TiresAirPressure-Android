package com.nazulay.tiresairpressuremanagement.data.repository

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.nazulay.tiresairpressuremanagement.data.ble.BleManager
import com.nazulay.tiresairpressuremanagement.data.ble.ConnectionState
import com.nazulay.tiresairpressuremanagement.data.local.SavedDevicesManager
import com.nazulay.tiresairpressuremanagement.domain.models.BleCommand
import com.nazulay.tiresairpressuremanagement.domain.models.BleDevice
import com.nazulay.tiresairpressuremanagement.domain.models.DeviceSettings
import com.nazulay.tiresairpressuremanagement.domain.models.DeviceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BleRepository(
    context: Context,
    initialBleManager: BleManager? = null
) {
    private val appContext = context.applicationContext
    private val savedDevicesManager = SavedDevicesManager(appContext)
    private var isAutoConnecting = false
    private var bleManager: BleManager? = null
    private var collectJob: Job? = null

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _deviceSettings = MutableStateFlow<DeviceSettings?>(null)
    val deviceSettings: StateFlow<DeviceSettings?> = _deviceSettings.asStateFlow()

    private val _deviceStatus = MutableStateFlow<DeviceStatus?>(null)
    val deviceStatus: StateFlow<DeviceStatus?> = _deviceStatus.asStateFlow()

    private val _scanResults = MutableStateFlow<List<BleDevice>>(emptyList())
    val scanResults: StateFlow<List<BleDevice>> = _scanResults.asStateFlow()

    init {
        Log.d(TAG, "Initializing BleRepository")
        if (initialBleManager != null) {
            attachBleManager(initialBleManager)
        }
    }

    fun attachBleManager(manager: BleManager) {
        if (this.bleManager === manager && collectJob?.isActive == true) return
        this.bleManager = manager
        collectJob?.cancel()
        collectJob = repositoryScope.launch {
            launch {
                manager.connectionState.collect { stateFromManager ->
                    _connectionState.value = stateFromManager
                }
            }
            launch {
                manager.deviceStatus.collect { status ->
                    _deviceStatus.value = status
                }
            }
            launch {
                manager.deviceSettings.collect { settings ->
                    _deviceSettings.value = settings
                }
            }
            launch {
                manager.scanResults.collect { results ->
                    _scanResults.value = results
                }
            }
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

    fun clearOldDevices() {
        savedDevicesManager.clearOldDevices()
    }

    fun ensureOnlyRecentDevice(address: String, name: String) {
        savedDevicesManager.ensureOnlyRecentDevice(address, name)
    }

    fun deleteDevice(address: String) {
        savedDevicesManager.deleteDevice(address)
    }

    fun reorderDevices(deviceAddresses: List<String>) {
        savedDevicesManager.reorderDevices(deviceAddresses)
    }

    fun getAccessCode(address: String): String {
        return savedDevicesManager.getAccessCode(address)
    }

    fun setAccessCode(address: String, code: String) {
        savedDevicesManager.setAccessCode(address, code)
    }

    suspend fun connectToSavedDevices() {
        if (isAutoConnecting) {
            Log.d(TAG, "Already attempting to connect to saved devices")
            return
        }

        isAutoConnecting = true
        val savedDevices = getSavedDevices()

        for (device in savedDevices) {
            Log.d(TAG, "Attempting to connect to saved device: ${device.name} (${device.address})")
            try {
                connect(device.address)
                delay(5000)

                if (_connectionState.value == ConnectionState.Connected ||
                    bleManager?.connectionState?.value == ConnectionState.Connected
                ) {
                    Log.d(TAG, "Successfully connected to saved device: ${device.name}")
                    break
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to saved device: ${device.name}", e)
            }
        }

        isAutoConnecting = false
    }

    fun onDeviceConnected(device: BluetoothDevice) {
        repositoryScope.launch {
            Log.d(TAG, "Device connected: ${device.name} (${device.address})")
            saveConnectedDevice(device.address, device.name ?: "Unknown Device")
        }
    }

    companion object {
        private const val TAG = "BleRepository"

        @Volatile
        private var instance: BleRepository? = null

        fun getInstance(context: Context, bleManager: BleManager? = null): BleRepository {
            val repo = instance ?: synchronized(this) {
                instance ?: BleRepository(context.applicationContext, bleManager).also { instance = it }
            }
            if (bleManager != null) {
                repo.attachBleManager(bleManager)
            }
            return repo
        }
    }
}
