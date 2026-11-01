package com.nazulay.tiresairpressuremanagement.ui.screens.settings

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.net.wifi.WifiManager
import android.net.wifi.ScanResult
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nazulay.tiresairpressuremanagement.data.repository.BleRepository
import com.nazulay.tiresairpressuremanagement.domain.models.BleCommand
import com.nazulay.tiresairpressuremanagement.domain.models.DeviceSettings
import com.nazulay.tiresairpressuremanagement.domain.models.PressureUnit
import com.nazulay.tiresairpressuremanagement.domain.models.TemperatureUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.nazulay.tiresairpressuremanagement.data.ble.BleManager
import com.nazulay.tiresairpressuremanagement.R
import kotlinx.coroutines.flow.SharingStarted
import com.nazulay.tiresairpressuremanagement.data.local.AppSettingsManager
import kotlinx.coroutines.delay
import com.nazulay.tiresairpressuremanagement.data.ble.ConnectionState
import com.nazulay.tiresairpressuremanagement.domain.models.DeviceStatus
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.nazulay.tiresairpressuremanagement.utils.LocaleHelper
import com.nazulay.tiresairpressuremanagement.utils.getActivity
import com.nazulay.tiresairpressuremanagement.MainActivity

data class WifiNetworkInfo(
    val ssid: String,
    val level: Int
)

class SettingsViewModel(
    private val bleManager: BleManager,
    private val application: Application
) : ViewModel() {
    
    companion object {
        private const val TAG = "SettingsViewModel"
        
        // Theme mode constants - keep only dark mode as reference
        const val THEME_MODE_DARK = 2
        
        fun provideFactory(
            application: Application,
            bleManager: BleManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                    return SettingsViewModel(bleManager, application) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
    
    private val wifiManager = application.getSystemService(Context.WIFI_SERVICE) as WifiManager
    
    private val appSettingsManager = AppSettingsManager.getInstance(application)
    
    // App Settings
    val appSettings = appSettingsManager.settings
    
    // Available WiFi networks (from BLE controller and/or local Wi-Fi scan)
    private val _availableNetworks = MutableStateFlow<List<WifiNetworkInfo>>(emptyList())
    val availableNetworks: StateFlow<List<WifiNetworkInfo>> = _availableNetworks.asStateFlow()
    
    // BLE Device Settings
    private val _localDeviceSettings = MutableStateFlow<DeviceSettings?>(null)
    val deviceSettings: StateFlow<DeviceSettings?> = _localDeviceSettings
    
    // BLE Device Status
    val deviceStatus: StateFlow<DeviceStatus?> = bleManager.deviceStatus
    
    // Connection state
    val connectionState = bleManager.connectionState
    
    // Boolean connection state (true/false)
    val isConnected = bleManager.connectionState.map { state ->
        state == ConnectionState.Connected
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), bleManager.connectionState.value == ConnectionState.Connected)
    
    // Detailed connection status
    val connectionStatus = connectionState.map { state: ConnectionState ->
        when (state) {
            ConnectionState.Connected -> 
                application.getString(R.string.connected)
            ConnectionState.Connecting -> 
                application.getString(R.string.connecting)
            ConnectionState.Disconnected -> 
                application.getString(R.string.disconnected)
            ConnectionState.PermissionDenied -> 
                application.getString(R.string.permission_denied)
            ConnectionState.Scanning -> 
                application.getString(R.string.scanning)
            ConnectionState.BluetoothDisabled -> 
                application.getString(R.string.bluetooth_disabled)
            ConnectionState.Error -> 
                application.getString(R.string.connection_error)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), mapConnectionStateToString(bleManager.connectionState.value))
    
    // Flag to track if settings have been modified
    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()
    
    // Predefined pressure states
    private val _predefinedPressure1 = MutableStateFlow(8f)
    private val _predefinedPressure2 = MutableStateFlow(10f)
    private val _predefinedPressure3 = MutableStateFlow(12f)
    private val _predefinedPressure4 = MutableStateFlow(16f)
    private val _predefinedPressure5 = MutableStateFlow(20f)
    private val _predefinedPressure6 = MutableStateFlow(30f)
    private val _predefinedPressure7 = MutableStateFlow(38f)
    private val _predefinedPressure8 = MutableStateFlow(40f)
    
    // Language state
    private val _currentLanguage = MutableStateFlow(LocaleHelper.getSelectedLanguage(application))
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()
    
    // Debug Mode State (from AppSettingsManager)
    val debugMode: StateFlow<Boolean> = appSettings.map { it.debugMode }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), appSettings.value.debugMode)
    
    init {
        Log.d(TAG, "Initializing SettingsViewModel")
        Log.d(TAG, "Initial Connection State from BleManager: ${bleManager.connectionState.value}")
        
        // Predefined pressures are handled by AppSettingsManager

        // Observe device settings from BleManager
        viewModelScope.launch {
            bleManager.deviceSettings.collect { settings ->
                if (settings != null) {
                    // Add detailed debug logging of raw values from BLE device
                    Log.d(TAG, "Received device settings from BLE device: $settings")
                    Log.d(TAG, "Raw sensor values - At 0 PSI: ${settings.sensorValueAt0PSI} (${settings.sensorValueAt0PSI.javaClass.simpleName})")
                    Log.d(TAG, "Raw sensor values - At 50 PSI: ${settings.sensorValueAt50PSI} (${settings.sensorValueAt50PSI.javaClass.simpleName})")
                }
                
                if (_localDeviceSettings.value == null) {
                    // Initialize local settings on first load
                    _localDeviceSettings.value = settings
                }
            }
        }

        // Observe Wi-Fi networks reported by the ESP32 controller over BLE
        viewModelScope.launch {
            bleManager.bleWifiNetworks.collect { bleList ->
                if (bleList.isNotEmpty()) {
                    val merged = (bleList.map { WifiNetworkInfo(it.first, it.second) } + _availableNetworks.value)
                        .filter { it.ssid.isNotBlank() }
                        .distinctBy { it.ssid }
                        .sortedByDescending { it.level }
                    _availableNetworks.value = merged
                }
            }
        }
    }
    
    // Public method to reconnect to saved device if needed
    fun connectToSavedDeviceIfNeeded() {
        viewModelScope.launch {
            Log.d(TAG, "Attempting to check connection status: current state = ${connectionState.value}")
            if (connectionState.value != ConnectionState.Connected) {
                Log.d(TAG, "Manually trying to connect to saved device via BleManager")
                try {
                    bleManager.connectToSavedDevice()
                    delay(1000)
                    Log.d(TAG, "After connection attempt, state is now: ${connectionState.value}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error connecting to saved device: ${e.message}", e)
                }
            } else {
                Log.d(TAG, "Already connected, requesting settings refresh via BleManager")
                requestDeviceSettings()
            }
        }
    }
    
    // Request current settings from the device
    fun requestDeviceSettings() {
        Log.d(TAG, "Requesting device settings via BleManager")
        try {
            bleManager.requestDeviceSettings()
            bleManager.sendCommand(BleCommand.RequestSettings())
            
            // Update local settings from BleManager only if there are no unsaved changes
            if (!_hasUnsavedChanges.value) {
                viewModelScope.launch {
                    delay(1000) // Give device time to respond
                    _localDeviceSettings.value = bleManager.deviceSettings.value
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting device settings: ${e.message}")
        }
    }
    
    fun scanWifiNetworks() {
        try {
            Log.d(TAG, "Starting WiFi scan (BLE controller + local Wi-Fi)")
            // Always request Wi-Fi scan from the connected ESP32 controller over BLE
            if (connectionState.value == ConnectionState.Connected) {
                bleManager.sendCommand(BleCommand.ScanWifi(rescan = true))
            }

            val context = application.applicationContext
            if (!hasWifiScanningPermissions(context) || !wifiManager.isWifiEnabled) {
                Log.w(TAG, "Local WiFi scan skipped (relying on BLE controller Wi-Fi scan)")
                return
            }

            val success = wifiManager.startScan()
            if (!success) {
                val results = try { wifiManager.scanResults ?: emptyList() } catch (e: SecurityException) { emptyList() }
                val localNets = results.filter { !it.SSID.isNullOrEmpty() }.map { WifiNetworkInfo(it.SSID, it.level) }
                val bleNets = bleManager.bleWifiNetworks.value.map { WifiNetworkInfo(it.first, it.second) }
                _availableNetworks.value = (bleNets + localNets).distinctBy { it.ssid }.sortedByDescending { it.level }
                return
            }

            viewModelScope.launch {
                delay(2000)
                val results = try {
                    wifiManager.scanResults ?: emptyList()
                } catch (e: SecurityException) {
                    emptyList()
                }
                val localNets = results.filter { !it.SSID.isNullOrEmpty() }.map { WifiNetworkInfo(it.SSID, it.level) }
                val bleNets = bleManager.bleWifiNetworks.value.map { WifiNetworkInfo(it.first, it.second) }
                _availableNetworks.value = (bleNets + localNets).distinctBy { it.ssid }.sortedByDescending { it.level }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning WiFi networks: ${e.message}", e)
        }
    }
    
    // Helper for permission check
    private fun hasWifiScanningPermissions(context: Context): Boolean {
         val neededPermissions = mutableListOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
         )
         // Location is required for scanning on newer Android versions
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
             neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
         }
        
        return neededPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun updateDeviceSettings(
        deviceName: String? = null,
        wifiSsid: String? = null,
        wifiPassword: String? = null,
        relayCompressorPin: Int? = null,
        relayFreePin: Int? = null,
        sensorPin: Int? = null,
        maxSecForStatus: Int? = null,
        sensorValueAt0PSI: Float? = null,
        sensorValueAt50PSI: Float? = null,
        measureWait: Float? = null,
        precision: Float? = null,
        pressureCalcMethod: Int? = null,
        sensorFullScalePSI: Float? = null,
        maxPressurePSI: Float? = null,
        maxValveOpenSec: Int? = null,
        accessPointPassword: String? = null,
        accessCode: String? = null,
        clearAccessCode: Boolean? = null
    ) {
        val currentSettings = _localDeviceSettings.value
        if (currentSettings != null) {
             // Create the settings object with potentially updated values
            val updatedSettings = currentSettings.copy(
                deviceName = deviceName ?: currentSettings.deviceName,
                wifiSsid = wifiSsid ?: currentSettings.wifiSsid,
                wifiPassword = wifiPassword ?: currentSettings.wifiPassword,
                relayCompressorPin = relayCompressorPin ?: currentSettings.relayCompressorPin,
                relayFreePin = relayFreePin ?: currentSettings.relayFreePin,
                sensorPin = sensorPin ?: currentSettings.sensorPin,
                maxSecForStatus = maxSecForStatus ?: currentSettings.maxSecForStatus,
                sensorValueAt0PSI = sensorValueAt0PSI ?: currentSettings.sensorValueAt0PSI,
                sensorValueAt50PSI = sensorValueAt50PSI ?: currentSettings.sensorValueAt50PSI,
                measureWait = measureWait ?: currentSettings.measureWait,
                precision = precision ?: currentSettings.precision,
                pressureCalcMethod = pressureCalcMethod ?: currentSettings.pressureCalcMethod,
                sensorFullScalePSI = sensorFullScalePSI ?: currentSettings.sensorFullScalePSI,
                maxPressurePSI = maxPressurePSI ?: currentSettings.maxPressurePSI,
                maxValveOpenSec = maxValveOpenSec ?: currentSettings.maxValveOpenSec,
                accessPointPassword = accessPointPassword ?: currentSettings.accessPointPassword,
                accessCode = accessCode ?: currentSettings.accessCode,
                clearAccessCode = clearAccessCode ?: currentSettings.clearAccessCode
            )
            
            Log.d(TAG, "Updating local device settings: $updatedSettings")
            
            // Only update local settings without sending to device
            _localDeviceSettings.value = updatedSettings
            _hasUnsavedChanges.value = true
        } else {
             Log.w(TAG, "Cannot update null device settings from updateDeviceSettings")
        }
    }
    
    // Renamed the original save function to be called internally
    private fun saveDeviceSettingsInternal(settingsToSave: DeviceSettings, onComplete: () -> Unit) {
        viewModelScope.launch {
            Log.d(TAG, "Saving device settings via BleManager: $settingsToSave")
            bleManager.sendCommand(BleCommand.UpdateSettings( // Use UpdateSettings
                wifiSsid = settingsToSave.wifiSsid,
                wifiPassword = settingsToSave.wifiPassword,
                deviceName = settingsToSave.deviceName,
                relayCompressorPin = settingsToSave.relayCompressorPin,
                relayFreePin = settingsToSave.relayFreePin,
                sensorPin = settingsToSave.sensorPin,
                sensorValueAt0PSI = settingsToSave.sensorValueAt0PSI,
                sensorValueAt50PSI = settingsToSave.sensorValueAt50PSI,
                precision = settingsToSave.precision,
                maxSecForStatus = settingsToSave.maxSecForStatus,
                measureWait = settingsToSave.measureWait,
                simulationMode = settingsToSave.simulationMode,
                pressureCalcMethod = settingsToSave.pressureCalcMethod,
                sensorFullScalePSI = settingsToSave.sensorFullScalePSI,
                maxPressurePSI = settingsToSave.maxPressurePSI,
                maxValveOpenSec = settingsToSave.maxValveOpenSec,
                accessPointPassword = settingsToSave.accessPointPassword,
                accessCode = settingsToSave.accessCode,
                clearAccessCode = settingsToSave.clearAccessCode
            ))
            _hasUnsavedChanges.value = false
            delay(1500) 
            onComplete()
        }
    }
    
    // Public save function called by the UI - implements simplified restart flow
    fun saveDeviceSettingsAndReconnect(forceReconnect: () -> Unit = {}) {
        val settingsToSave = _localDeviceSettings.value ?: run {
            Log.w(TAG, "Cannot save null device settings")
            return
        }

        viewModelScope.launch {
            // Check if we're actually connected before trying to send commands
            if (connectionState.value != ConnectionState.Connected) {
                Log.w(TAG, "Cannot save settings - not connected to device")
                return@launch
            }

            Log.d(TAG, "Saving device settings via BleManager: $settingsToSave")
            
            try {
                bleManager.sendCommand(BleCommand.UpdateSettings(
                    // ... map settingsToSave fields to command fields ...
                    wifiSsid = settingsToSave.wifiSsid,
                    wifiPassword = settingsToSave.wifiPassword,
                    deviceName = settingsToSave.deviceName,
                    relayCompressorPin = settingsToSave.relayCompressorPin,
                    relayFreePin = settingsToSave.relayFreePin,
                    sensorPin = settingsToSave.sensorPin,
                    sensorValueAt0PSI = settingsToSave.sensorValueAt0PSI,
                    sensorValueAt50PSI = settingsToSave.sensorValueAt50PSI,
                    precision = settingsToSave.precision,
                    maxSecForStatus = settingsToSave.maxSecForStatus,
                    measureWait = settingsToSave.measureWait,
                    simulationMode = settingsToSave.simulationMode,
                    pressureCalcMethod = settingsToSave.pressureCalcMethod,
                    sensorFullScalePSI = settingsToSave.sensorFullScalePSI,
                    maxPressurePSI = settingsToSave.maxPressurePSI,
                    maxValveOpenSec = settingsToSave.maxValveOpenSec,
                    accessPointPassword = settingsToSave.accessPointPassword,
                    accessCode = settingsToSave.accessCode,
                    clearAccessCode = settingsToSave.clearAccessCode
                ))
                
                _hasUnsavedChanges.value = false

                // The device will reset itself after receiving the settings
                Log.d(TAG, "Settings saved. The device will reset and reconnect automatically.")
                
                // Wait for device to process the settings and reset
                delay(2000) // Wait 2 seconds for device to process
                
                // Force disconnect to ensure clean state
                Log.d(TAG, "Disconnecting to allow device reset")
                bleManager.disconnect()
                
                // Wait for device to reset
                delay(3000) // Wait 3 seconds for device reset
                
                // Now trigger reconnection
                Log.d(TAG, "Triggering reconnection after device reset")
                forceReconnect()
                
                // After reconnection, sync debug mode with the restarted ESP32
                viewModelScope.launch {
                    // Wait for reconnection to complete
                    delay(3000)
                    val currentDebugMode = appSettingsManager.settings.value.debugMode
                    Log.d(TAG, "Syncing debug mode after ESP32 restart: $currentDebugMode")
                    bleManager.sendCommand(BleCommand.DebugMode(enabled = currentDebugMode))
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error saving device settings: ${e.message}", e)
                // Still mark as saved to avoid UI issues
                _hasUnsavedChanges.value = false
            }
        }
    }
    
    // Add a new method to discard changes
    fun discardChanges() {
        viewModelScope.launch {
            // Reset local settings to the actual device settings
            _localDeviceSettings.value = bleManager.deviceSettings.value
            _hasUnsavedChanges.value = false
            Log.d(TAG, "Discarded all unsaved changes")
        }
    }
    
    /**
     * מנקה את רשימת הרשתות האלחוטיות הזמינות
     * משמש לסגירת רשימת הרשתות לאחר בחירת רשת
     */
    fun clearAvailableNetworks() {
        Log.d(TAG, "Clearing available networks list")
        _availableNetworks.value = emptyList()
    }
    
    // Replace the updateThemeMode method with a no-op
    fun updateThemeMode(mode: Int) {
        // No-op - we're enforcing dark mode only
        Log.d(TAG, "Theme mode change requested but ignored - using dark mode only")
    }
    
    // App Settings Functions
    fun updateSimulationMode(enabled: Boolean) {
        // Update app settings
        appSettingsManager.updateSimulationMode(enabled)
        
        // Update device settings
        val currentSettings = _localDeviceSettings.value
        if (currentSettings != null) {
            // Create updated settings with new simulation mode
            val updatedSettings = currentSettings.copy(simulationMode = enabled)
            
            // Update local settings
            _localDeviceSettings.value = updatedSettings
            
            // Mark that we have unsaved changes
            _hasUnsavedChanges.value = true
            
            // Log the change
            Log.d(TAG, "Updated device simulation mode to: $enabled")
            
            // Immediately send the command to update simulation mode on the device
            viewModelScope.launch {
                try {
                    Log.d(TAG, "Sending SimulationMode command to device: $enabled")
                    bleManager.sendCommand(BleCommand.SimulationMode(enabled = enabled))
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending simulation mode command: ${e.message}")
                }
            }
        } else {
            Log.w(TAG, "Cannot update simulation mode: device settings are null")
        }
    }
    
    fun updatePredefinedPressures(pressures: List<Float>) {
        appSettingsManager.updatePredefinedPressures(pressures)
    }
    
    fun updatePressureCount(minCount: Int, maxCount: Int) {
        appSettingsManager.updatePressureCount(minCount, maxCount)
    }
    
    fun addPressure(value: Float = 0f) {
        val currentSettings = appSettingsManager.settings.value
        val currentPressures = currentSettings.predefinedPressures
        
        // Only add if we're below the max count
        if (currentPressures.size < currentSettings.maxPressureCount) {
            val newPressures = currentPressures + value
            appSettingsManager.updatePredefinedPressures(newPressures)
        }
    }
    
    fun removePressure(index: Int) {
        val currentSettings = appSettingsManager.settings.value
        val currentPressures = currentSettings.predefinedPressures.toMutableList()
        
        // Only remove if we're above min count
        if (currentPressures.size > currentSettings.minPressureCount && index >= 0 && index < currentPressures.size) {
            currentPressures.removeAt(index)
            appSettingsManager.updatePredefinedPressures(currentPressures)
        }
    }

    // Public method to force refresh the connection state
    fun forceRefreshConnectionState() {
        viewModelScope.launch {
            Log.d(TAG, "Forcing refresh of connection state")
            try {
                // Check if the current state matches reality
                val currentState = connectionState.value
                Log.d(TAG, "Current connection state is: $currentState")
                
                if (currentState != ConnectionState.Connected) {
                    // If we're not connected according to the state, try to trigger an update
                    Log.d(TAG, "Not connected according to state, requesting device settings to update state")
                    // Just request the connection state update without updating settings
                    bleManager.requestDeviceSettings()
                } else {
                    // We're connected according to the state, verify by requesting settings
                    Log.d(TAG, "Connected according to state, verifying by requesting settings")
                    // Request device settings regardless of unsaved changes
                    // This ensures we at least get initial settings from the device
                    bleManager.requestDeviceSettings()
                    bleManager.sendCommand(BleCommand.RequestSettings())
                    
                    // If we don't have unsaved changes, update our local copy with the device settings
                    if (!_hasUnsavedChanges.value) {
                        viewModelScope.launch {
                            // Give device time to respond
                            delay(1000)
                            // Update local settings if we received them and still have no local settings
                            if (_localDeviceSettings.value == null && bleManager.deviceSettings.value != null) {
                                Log.d(TAG, "Updating local settings from BleManager after request")
                                _localDeviceSettings.value = bleManager.deviceSettings.value
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking connection state: ${e.message}", e)
            }
        }
    }

    // Helper function for initial value of connectionStatus
    private fun mapConnectionStateToString(state: ConnectionState): String {
         return when (state) {
            ConnectionState.Connected -> application.getString(R.string.connected)
            ConnectionState.Connecting -> application.getString(R.string.connecting)
            ConnectionState.Disconnected -> application.getString(R.string.disconnected)
            ConnectionState.PermissionDenied -> application.getString(R.string.permission_denied)
            ConnectionState.Scanning -> application.getString(R.string.scanning)
            ConnectionState.BluetoothDisabled -> application.getString(R.string.bluetooth_disabled)
            ConnectionState.Error -> application.getString(R.string.connection_error)
        }
    }

    // Add this method to update language
    fun updateLanguage(languageCode: String) {
        viewModelScope.launch {
            _currentLanguage.update { languageCode }
            // Get the activity reference from the application context
            val activity = application.getActivity()
            if (activity is MainActivity) {
                // Update language immediately
                activity.updateLanguage(languageCode)
            }
        }
    }

    fun setDebugMode(enabled: Boolean) {
        appSettingsManager.updateDebugMode(enabled)
        Log.d(TAG, "setDebugMode: Sending DebugMode command to BLE device. enabled=$enabled")
        // Send BLE command to activate/deactivate debug mode
        bleManager.sendCommand(com.nazulay.tiresairpressuremanagement.domain.models.BleCommand.DebugMode(enabled = enabled))
    }
} 
