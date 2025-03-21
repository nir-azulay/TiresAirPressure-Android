package com.nazulay.tiresairpressuremanagement.ui.screens.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nazulay.tiresairpressuremanagement.data.repository.BleRepository
import com.nazulay.tiresairpressuremanagement.data.ble.BleManager
import com.nazulay.tiresairpressuremanagement.domain.models.BleCommand
import com.nazulay.tiresairpressuremanagement.domain.models.BleDevice
import com.nazulay.tiresairpressuremanagement.domain.models.DeviceStatus
import com.nazulay.tiresairpressuremanagement.utils.BleConstants
import com.nazulay.tiresairpressuremanagement.utils.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.ViewModelProvider
import android.content.Context

class MainViewModel(
    private val bleRepository: BleRepository,
    private val applicationContext: Context
) : ViewModel() {
    
    companion object {
        fun provideFactory(
            application: Application,
            bleManager: BleManager? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(
                    bleRepository = BleRepository(application, bleManager),
                    applicationContext = application
                ) as T
            }
        }
    }
    
    private val _targetPressure = MutableStateFlow(30f)
    val targetPressure: StateFlow<Float> = _targetPressure.asStateFlow()
    
    private val _isInflating = MutableStateFlow(false)
    val isInflating: StateFlow<Boolean> = _isInflating.asStateFlow()
    
    private val _currentPressure = MutableStateFlow(0f)
    val currentPressure: StateFlow<Float> = _currentPressure.asStateFlow()
    
    val isConnected = bleRepository.connectionState
    val scanResults = bleRepository.scanResults
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted
    
    val predefinedPressures = BleConstants.PREDEFINED_PRESSURES
    
    init {
        checkPermissions()
        viewModelScope.launch {
            bleRepository.deviceStatus.collect { status ->
                status?.let {
                    _currentPressure.value = it.pressure
                    
                    // Sync inflation state with device operation mode
                    when (it.operationMode) {
                        DeviceStatus.MODE_INFLATE, DeviceStatus.MODE_DEFLATE -> {
                            if (!_isInflating.value) {
                                android.util.Log.d("MainViewModel", "Syncing UI: Device is in INFLATE/DEFLATE mode, updating to inflating state")
                                _isInflating.value = true
                            }
                        }
                        DeviceStatus.MODE_STOP -> {
                            if (_isInflating.value) {
                                android.util.Log.d("MainViewModel", "Syncing UI: Device is in STOP mode, updating to stopped state")
                                _isInflating.value = false
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun checkPermissions() {
        _permissionGranted.value = PermissionManager.hasRequiredPermissions(applicationContext)
    }
    
    fun startScan() {
        _isScanning.value = true
        bleRepository.startScan()
    }
    
    fun stopScan() {
        _isScanning.value = false
        bleRepository.stopScan()
    }
    
    fun connectToDevice(device: BleDevice) {
        bleRepository.connect(device.address)
    }
    
    fun disconnect() {
        bleRepository.disconnect()
    }
    
    fun setTargetPressure(pressure: Float) {
        _targetPressure.value = pressure
    }
    
    fun increaseTargetPressure() {
        _targetPressure.value += 0.5f
    }
    
    fun decreaseTargetPressure() {
        _targetPressure.value -= 0.5f
    }
    
    fun toggleInflation() {
        _isInflating.value = !_isInflating.value
        if (_isInflating.value) {
            startInflation()
        } else {
            stopInflation()
        }
    }
    
    private fun startInflation() {
        bleRepository.sendCommand(BleCommand.StartTargetPressure(targetPressure = _targetPressure.value))
    }
    
    private fun stopInflation() {
        bleRepository.sendCommand(BleCommand.Stop())
    }
    
    override fun onCleared() {
        super.onCleared()
        bleRepository.disconnect()
    }
} 
