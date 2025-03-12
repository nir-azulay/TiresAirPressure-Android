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

class SettingsViewModel(
    private val bleRepository: BleRepository,
    private val application: Application
) : ViewModel() {
    
    companion object {
        fun provideFactory(
            application: Application
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    bleRepository = BleRepository(application),
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
            measureWait = 2,
            precision = 1,
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
    
    val isConnected = bleRepository.connectionState
    
    init {
        viewModelScope.launch {
            isConnected.collect { connected ->
                Log.d("SettingsViewModel", "Connection state changed: $connected")
            }
        }
    }
    
    fun scanWifiNetworks() {
        wifiManager.startScan()
        val results = wifiManager.scanResults
        _availableNetworks.value = results.map { it.SSID }.distinct()
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
        measureWait: Int = _settings.value.measureWait,
        precision: Int = _settings.value.precision,
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
        
        // שליחת ההגדרות החדשות למכשיר
        bleRepository.sendCommand(
            BleCommand.UpdateSettings(
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
        )
    }
    
    fun setSimulationMode(enabled: Boolean) {
        _settings.value = _settings.value.copy(simulationMode = enabled)
        bleRepository.sendCommand(BleCommand.SimulationMode(enabled = enabled))
    }
    
    fun saveSettings(onSuccess: () -> Unit) {
        viewModelScope.launch {
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
            // מחכים קצת לפני שמנתקים כדי לתת למכשיר זמן לקבל את ההגדרות
            kotlinx.coroutines.delay(1000)
            bleRepository.disconnect()
            onSuccess()
        }
    }
} 