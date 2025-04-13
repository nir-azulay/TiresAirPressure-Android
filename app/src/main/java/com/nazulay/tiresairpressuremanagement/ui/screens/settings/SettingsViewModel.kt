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
    
    // Available WiFi networks - Store ScanResult to get signal strength
    private val _availableNetworks = MutableStateFlow<List<ScanResult>>(emptyList())
    val availableNetworks: StateFlow<List<ScanResult>> = _availableNetworks.asStateFlow()
    
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
    
    init {
        Log.d(TAG, "Initializing SettingsViewModel")
        Log.d(TAG, "Initial Connection State from BleManager: ${bleManager.connectionState.value}")
        
        // Predefined pressures are handled by AppSettingsManager

        // Observe device settings from BleManager
        viewModelScope.launch {
            bleManager.deviceSettings.collect { settings ->
                if (_localDeviceSettings.value == null) {
                    // Initialize local settings on first load
                    _localDeviceSettings.value = settings
                }
            }
        }

        // The ViewModel now automatically reflects the state from the single BleManager instance.
        // No need for explicit collection here for connection/device states.
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
    private fun requestDeviceSettings() {
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
            Log.d(TAG, "Starting WiFi scan")
            val context = application.applicationContext
            
            // Permissions check (simplified, assumes MainActivity handles requests)
            if (!hasWifiScanningPermissions(context)) {
                Log.w(TAG, "Required permissions not granted for WiFi scan")
                // Consider emitting a specific state or error message here
                _availableNetworks.value = emptyList() // Clear previous results
                // Maybe set a separate error state flow?
                return
            }
            
            if (!wifiManager.isWifiEnabled) {
                Log.w(TAG, "WiFi is disabled, cannot scan networks")
                _availableNetworks.value = emptyList()
                return
            }
            
            // API >= 30 requires location services to be enabled for WiFi scanning
            // Add check if needed based on target SDK and testing
            
            val success = wifiManager.startScan()
            if (!success) {
                Log.w(TAG, "wifiManager.startScan() returned false")
                // May happen if scans are too frequent
                // Get previous results if available
                 val results = try { wifiManager.scanResults ?: emptyList() } catch (e: SecurityException) { emptyList() }
                 _availableNetworks.value = results.filter { !it.SSID.isNullOrEmpty() }.distinctBy { it.SSID }
                 Log.d(TAG, "Failed to start new scan, using previous results: ${results.size}")
                 return
            }
            
            // It takes some time for scan results to be available. 
            // A BroadcastReceiver for SCAN_RESULTS_AVAILABLE_ACTION is the proper way,
            // but for simplicity here, we'll query after a short delay.
            viewModelScope.launch {
                delay(2000) // Wait 2 seconds for scan results
                val results = try {
                     wifiManager.scanResults ?: emptyList()
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException getting scan results: ${e.message}")
                    emptyList()
                }
                
                // Filter out empty SSIDs and duplicates, sort by signal strength
                val networks = results
                    .filter { !it.SSID.isNullOrEmpty() }
                    .distinctBy { it.SSID } // Keep only one entry per SSID
                    .sortedByDescending { it.level } // Strongest signal first
                    
                Log.d(TAG, "Found ${networks.size} unique WiFi networks: ${networks.map { it.SSID + " (" + it.level + ")" }}")
                _availableNetworks.value = networks
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error scanning WiFi networks: ${e.message}", e)
            // Update UI to show error?
            _availableNetworks.value = emptyList()
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
        autoStart: Boolean? = null,
        maxSecForStatus: Int? = null,
        sensorValueAt0PSI: Int? = null,
        sensorValueAt50PSI: Int? = null,
        measureWait: Float? = null,
        precision: Float? = null,
        btPinCode: String? = null
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
                autoStart = autoStart ?: currentSettings.autoStart,
                maxSecForStatus = maxSecForStatus ?: currentSettings.maxSecForStatus,
                sensorValueAt0PSI = sensorValueAt0PSI ?: currentSettings.sensorValueAt0PSI,
                sensorValueAt50PSI = sensorValueAt50PSI ?: currentSettings.sensorValueAt50PSI,
                measureWait = measureWait ?: currentSettings.measureWait,
                precision = precision ?: currentSettings.precision,
                btPinCode = btPinCode ?: currentSettings.btPinCode
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
                autoStart = settingsToSave.autoStart,
                btPinCode = settingsToSave.btPinCode,
                // Note: Predefined pressures are NOT part of UpdateSettings command
                // They are managed locally by AppSettingsManager
                // Note: operationMode, currentPressure, softwareVersion, ipAddress 
                //       are status/read-only, not part of settings update command
                simulationMode = settingsToSave.simulationMode // Simulation mode IS part of update
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
            Log.d(TAG, "Saving device settings via BleManager: $settingsToSave")
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
                autoStart = settingsToSave.autoStart,
                btPinCode = settingsToSave.btPinCode,
                simulationMode = settingsToSave.simulationMode
            ))
            
            _hasUnsavedChanges.value = false

            // Don't disconnect - let the device handle reconnection automatically
            // The device will reset itself after receiving the settings
            Log.d(TAG, "Settings saved. The device will reset and reconnect automatically.")
            
            // Schedule a delayed call to force reconnection after 5 seconds
            delay(5000) // Wait 5 seconds
            Log.d(TAG, "Calling forceReconnect callback to trigger onResume")
            forceReconnect() // Call the provided callback to force reconnection
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
                    // If we don't have unsaved changes, update our local copy with the device settings
                    if (!_hasUnsavedChanges.value) {
                        requestDeviceSettings()
                    } else {
                        // Just ping the device without overwriting our unsaved changes
                        bleManager.requestDeviceSettings()
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
} 
