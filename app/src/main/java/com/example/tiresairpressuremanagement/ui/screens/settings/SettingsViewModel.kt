package com.example.tiresairpressuremanagement.ui.screens.settings

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tiresairpressuremanagement.data.repository.BleRepository
import com.example.tiresairpressuremanagement.domain.models.BleCommand
import com.example.tiresairpressuremanagement.domain.models.DeviceSettings
import com.example.tiresairpressuremanagement.domain.models.PressureUnit
import com.example.tiresairpressuremanagement.domain.models.TemperatureUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.example.tiresairpressuremanagement.data.ble.ConnectionState
import com.example.tiresairpressuremanagement.data.ble.BleManager

class SettingsViewModel(
    private val bleRepository: BleRepository,
    private val application: Application
) : ViewModel() {
    
    companion object {
        private const val TAG = "SettingsViewModel"
        
        fun provideFactory(
            application: Application,
            bleManager: BleManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    bleRepository = BleRepository(application, bleManager),
                    application = application
                ) as T
            }
        }
    }
    
    private val wifiManager = application.getSystemService(Context.WIFI_SERVICE) as WifiManager
    
    private val _settings = MutableStateFlow(
        DeviceSettings(
            deviceName = "",
            wifiSsid = "",
            wifiPassword = "",
            relayCompressorPin = 0,
            relayFreePin = 0,
            sensorPin = 0,
            autoStart = false,
            maxSecForStatus = 30,
            sensorValueAt0PSI = 0,
            sensorValueAt50PSI = 1023,
            measureWait = 2f,
            precision = 1f,
            btPinCode = "1234",
            currentPressure = 0f,
            softwareVersion = "",
            ipAddress = "",
            simulationMode = false
        )
    )
    val settings: StateFlow<DeviceSettings> = _settings.asStateFlow()
    
    private val _availableNetworks = MutableStateFlow<List<String>>(emptyList())
    val availableNetworks: StateFlow<List<String>> = _availableNetworks
    
    // Boolean connection state (true/false)
    val isConnected = bleRepository.connectionState
    
    // Raw connection state (Connected, Connecting, Disconnected)
    val connectionState = bleRepository.rawConnectionState
    
    // Create a derived state flow that provides more detailed connection information
    val connectionStatus = connectionState.map { state ->
        when (state) {
            com.example.tiresairpressuremanagement.data.ble.ConnectionState.Connected -> "מחובר"
            com.example.tiresairpressuremanagement.data.ble.ConnectionState.Connecting -> "מתחבר..."
            com.example.tiresairpressuremanagement.data.ble.ConnectionState.Disconnected -> "מנותק"
        }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, "מנותק")
    
    init {
        Log.d(TAG, "Initializing SettingsViewModel")
        
        // Monitor connection state
        viewModelScope.launch {
            connectionState.collect { state ->
                Log.d(TAG, "Connection state changed: $state")
                if (state == com.example.tiresairpressuremanagement.data.ble.ConnectionState.Connected) {
                    // When connected, load settings from shared preferences
                    loadSettingsFromSharedPreferences()
                    // Request current settings from device
                    requestDeviceSettings()
                }
            }
        }
        
        // Monitor device status updates
        viewModelScope.launch {
            bleRepository.deviceStatus.collect { status ->
                Log.d(TAG, "Device status update received: $status")
                if (status != null) {
                    // Update current pressure in settings
                    _settings.update { currentSettings ->
                        currentSettings.copy(
                            currentPressure = status.pressure
                        )
                    }
                }
            }
        }
        
        // Monitor device settings updates
        viewModelScope.launch {
            bleRepository.deviceSettings.collect { deviceSettings ->
                Log.d(TAG, "Device settings update received: $deviceSettings")
                if (deviceSettings != null) {
                    // Update all settings
                    _settings.value = deviceSettings
                    Log.d(TAG, "Updated settings with device settings: $deviceSettings")
                }
            }
        }
        
        // Load initial settings from shared preferences
        loadSettingsFromSharedPreferences()
        
        // Removed: Don't try to connect to saved device in init
        // This will prevent reconnection when navigating to settings
    }
    
    // Public method to reconnect to saved device if needed
    fun connectToSavedDeviceIfNeeded() {
        viewModelScope.launch {
            if (connectionState.value != com.example.tiresairpressuremanagement.data.ble.ConnectionState.Connected) {
                Log.d(TAG, "Manually trying to connect to saved device")
                bleRepository.connectToSavedDevice()
            } else {
                Log.d(TAG, "Already connected, no need to reconnect")
            }
        }
    }
    
    // Request current settings from the device
    private fun requestDeviceSettings() {
        Log.d(TAG, "Requesting device settings")
        // Send a command to request settings from the device
        try {
            // First try the direct method
            bleRepository.requestDeviceSettings()
            
            // As a fallback, also try sending the command directly
            bleRepository.sendCommand(BleCommand.RequestSettings())
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting device settings: ${e.message}")
        }
    }
    
    // Load settings from shared preferences
    private fun loadSettingsFromSharedPreferences() {
        Log.d(TAG, "Loading settings from shared preferences")
        val sharedPreferences = application.getSharedPreferences("DeviceSettings", Context.MODE_PRIVATE)
        
        // Load all settings from shared preferences
        val deviceName = sharedPreferences.getString("deviceName", "") ?: ""
        val wifiSsid = sharedPreferences.getString("wifiSsid", "") ?: ""
        val wifiPassword = sharedPreferences.getString("wifiPassword", "") ?: ""
        val relayCompressorPin = sharedPreferences.getInt("relayCompressorPin", 0)
        val relayFreePin = sharedPreferences.getInt("relayFreePin", 0)
        val sensorPin = sharedPreferences.getInt("sensorPin", 0)
        val autoStart = sharedPreferences.getBoolean("autoStart", false)
        val maxSecForStatus = sharedPreferences.getInt("maxSecForStatus", 30)
        val sensorValueAt0PSI = sharedPreferences.getInt("sensorValueAt0PSI", 0)
        val sensorValueAt50PSI = sharedPreferences.getInt("sensorValueAt50PSI", 1023)
        val measureWait = sharedPreferences.getFloat("measureWait", 2f)
        val precision = sharedPreferences.getFloat("precision", 1f)
        val btPinCode = sharedPreferences.getString("btPinCode", "1234") ?: "1234"
        val currentPressure = sharedPreferences.getFloat("pressure", 0f)
        val softwareVersion = sharedPreferences.getString("softwareVersion", "") ?: ""
        val ipAddress = sharedPreferences.getString("ipAddress", "") ?: ""
        val simulationMode = sharedPreferences.getBoolean("simulationMode", false)
        
        // Update settings state
        _settings.value = DeviceSettings(
            deviceName = deviceName,
            wifiSsid = wifiSsid,
            wifiPassword = wifiPassword,
            relayCompressorPin = relayCompressorPin,
            relayFreePin = relayFreePin,
            sensorPin = sensorPin,
            autoStart = autoStart,
            maxSecForStatus = maxSecForStatus,
            sensorValueAt0PSI = sensorValueAt0PSI,
            sensorValueAt50PSI = sensorValueAt50PSI,
            measureWait = measureWait,
            precision = precision,
            btPinCode = btPinCode,
            currentPressure = currentPressure,
            softwareVersion = softwareVersion,
            ipAddress = ipAddress,
            simulationMode = simulationMode
        )
        
        Log.d(TAG, "Loaded settings from SharedPreferences: ${_settings.value}")
    }
    
    // Save current pressure to shared preferences
    private fun saveCurrentPressureToSharedPreferences(pressure: Float) {
        val sharedPreferences = application.getSharedPreferences("DeviceSettings", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putFloat("pressure", pressure)
        editor.apply()
    }
    
    // Save all settings to shared preferences
    private fun saveSettingsToSharedPreferences() {
        Log.d(TAG, "Saving settings to shared preferences")
        val sharedPreferences = application.getSharedPreferences("DeviceSettings", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        
        val currentSettings = _settings.value
        editor.putString("deviceName", currentSettings.deviceName)
        editor.putString("wifiSsid", currentSettings.wifiSsid)
        editor.putString("wifiPassword", currentSettings.wifiPassword)
        editor.putInt("relayCompressorPin", currentSettings.relayCompressorPin)
        editor.putInt("relayFreePin", currentSettings.relayFreePin)
        editor.putInt("sensorPin", currentSettings.sensorPin)
        editor.putBoolean("autoStart", currentSettings.autoStart)
        editor.putInt("maxSecForStatus", currentSettings.maxSecForStatus)
        editor.putInt("sensorValueAt0PSI", currentSettings.sensorValueAt0PSI)
        editor.putInt("sensorValueAt50PSI", currentSettings.sensorValueAt50PSI)
        editor.putFloat("measureWait", currentSettings.measureWait)
        editor.putFloat("precision", currentSettings.precision)
        editor.putString("btPinCode", currentSettings.btPinCode)
        editor.putBoolean("simulationMode", currentSettings.simulationMode)
        
        editor.apply()
    }
    
    fun scanWifiNetworks() {
        try {
            Log.d(TAG, "Starting WiFi scan")
            
            // Get the current activity context
            val context = application.applicationContext
            
            // Check if we have the necessary permissions
            val permissionCheck = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val locationPermission = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                val wifiStatePermission = context.checkSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE)
                val wifiChangePermission = context.checkSelfPermission(android.Manifest.permission.CHANGE_WIFI_STATE)
                
                locationPermission == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                wifiStatePermission == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                wifiChangePermission == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true // On older Android versions, permissions are granted at install time
            }
            
            if (!permissionCheck) {
                Log.w(TAG, "Required permissions not granted, cannot scan WiFi networks")
                _availableNetworks.value = listOf("Location and WiFi permissions required for scanning")
                return
            }
            
            // Check if WiFi is enabled
            if (!wifiManager.isWifiEnabled) {
                Log.w(TAG, "WiFi is disabled, cannot scan networks")
                _availableNetworks.value = listOf("WiFi is disabled")
                return
            }
            
            // Start scan safely
            val success = wifiManager.startScan()
            if (!success) {
                Log.w(TAG, "Failed to start WiFi scan")
                _availableNetworks.value = listOf("Failed to start WiFi scan")
                return
            }
            
            // Get scan results safely
            val results = try {
                wifiManager.scanResults ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting WiFi scan results: ${e.message}")
                emptyList()
            }
            
            // Filter out empty SSIDs and duplicates
            val networks = results
                .filter { !it.SSID.isNullOrEmpty() }
                .map { it.SSID }
                .distinct()
            
            Log.d(TAG, "Found ${networks.size} WiFi networks: $networks")
            _availableNetworks.value = if (networks.isEmpty()) {
                listOf("No networks found")
            } else {
                networks
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning WiFi networks: ${e.message}", e)
            _availableNetworks.value = listOf("Error scanning: ${e.message}")
        }
    }
    
    fun updateSettings(
        deviceName: String = _settings.value.deviceName,
        wifiSsid: String = _settings.value.wifiSsid,
        wifiPassword: String = _settings.value.wifiPassword,
        relayCompressorPin: Int = _settings.value.relayCompressorPin,
        relayFreePin: Int = _settings.value.relayFreePin,
        sensorPin: Int = _settings.value.sensorPin,
        autoStart: Boolean = _settings.value.autoStart,
        maxSecForStatus: Int = _settings.value.maxSecForStatus,
        sensorValueAt0PSI: Int = _settings.value.sensorValueAt0PSI,
        sensorValueAt50PSI: Int = _settings.value.sensorValueAt50PSI,
        measureWait: Float = _settings.value.measureWait,
        precision: Float = _settings.value.precision,
        btPinCode: String = _settings.value.btPinCode
    ) {
        val newSettings = _settings.value.copy(
            deviceName = deviceName,
            wifiSsid = wifiSsid,
            wifiPassword = wifiPassword,
            relayCompressorPin = relayCompressorPin,
            relayFreePin = relayFreePin,
            sensorPin = sensorPin,
            autoStart = autoStart,
            maxSecForStatus = maxSecForStatus,
            sensorValueAt0PSI = sensorValueAt0PSI,
            sensorValueAt50PSI = sensorValueAt50PSI,
            measureWait = measureWait,
            precision = precision,
            btPinCode = btPinCode
        )
        _settings.value = newSettings
        
        // Save settings to shared preferences
        saveSettingsToSharedPreferences()
    }
    
    fun setSimulationMode(enabled: Boolean) {
        _settings.value = _settings.value.copy(simulationMode = enabled)
        bleRepository.sendCommand(BleCommand.SimulationMode(enabled = enabled))
        
        // Save simulation mode to shared preferences
        val sharedPreferences = application.getSharedPreferences("DeviceSettings", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putBoolean("simulationMode", enabled)
        editor.apply()
    }
    
    fun saveSettings(onSuccess: () -> Unit) {
        viewModelScope.launch {
            Log.d(TAG, "Saving settings to device: ${_settings.value}")
            
            // Save settings to shared preferences first
            saveSettingsToSharedPreferences()
            
            // Send settings to device
            bleRepository.sendCommand(
                BleCommand.UpdateSettings(
                    deviceName = _settings.value.deviceName,
                    wifiSsid = _settings.value.wifiSsid,
                    wifiPassword = _settings.value.wifiPassword,
                    relayCompressorPin = _settings.value.relayCompressorPin,
                    relayFreePin = _settings.value.relayFreePin,
                    sensorPin = _settings.value.sensorPin,
                    autoStart = _settings.value.autoStart,
                    maxSecForStatus = _settings.value.maxSecForStatus,
                    sensorValueAt0PSI = _settings.value.sensorValueAt0PSI,
                    sensorValueAt50PSI = _settings.value.sensorValueAt50PSI,
                    measureWait = _settings.value.measureWait,
                    precision = _settings.value.precision,
                    btPinCode = _settings.value.btPinCode
                )
            )
            
            // Wait a bit before disconnecting to give the device time to receive the settings
            kotlinx.coroutines.delay(1000)
            bleRepository.disconnect()
            onSuccess()
        }
    }
} 