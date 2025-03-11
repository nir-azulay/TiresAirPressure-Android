package com.example.tiresairpressuremanagement.ui.screens.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tiresairpressuremanagement.data.repository.BleRepository
import com.example.tiresairpressuremanagement.domain.models.BleCommand
import com.example.tiresairpressuremanagement.domain.models.BleDevice
import com.example.tiresairpressuremanagement.domain.models.DeviceStatus
import com.example.tiresairpressuremanagement.utils.BleConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val bleRepository = BleRepository(application)
    
    val isConnected: StateFlow<Boolean> = bleRepository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    
    val deviceStatus: StateFlow<DeviceStatus?> = bleRepository.deviceStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    
    val scanResults: StateFlow<List<BleDevice>> = bleRepository.scanResults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    
    private val _targetPressure = MutableStateFlow(12f) // Default target pressure
    val targetPressure: StateFlow<Float> = _targetPressure
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning
    
    val predefinedPressures = BleConstants.PREDEFINED_PRESSURES
    
    fun startScan() {
        _isScanning.value = true
        bleRepository.startScan()
    }
    
    fun stopScan() {
        _isScanning.value = false
        bleRepository.stopScan()
    }
    
    fun connect(device: BleDevice) {
        bleRepository.connect(device.address)
    }
    
    fun disconnect() {
        bleRepository.disconnect()
    }
    
    fun setTargetPressure(pressure: Float) {
        _targetPressure.value = pressure
    }
    
    fun startOperation() {
        bleRepository.sendCommand(
            BleCommand.Start(targetPressure = targetPressure.value)
        )
    }
    
    fun stopOperation() {
        bleRepository.sendCommand(BleCommand.Stop())
    }
    
    override fun onCleared() {
        super.onCleared()
        bleRepository.disconnect()
    }
} 