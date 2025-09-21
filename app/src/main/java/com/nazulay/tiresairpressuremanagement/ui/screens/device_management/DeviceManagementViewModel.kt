package com.nazulay.tiresairpressuremanagement.ui.screens.device_management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nazulay.tiresairpressuremanagement.data.ble.BleManager
import com.nazulay.tiresairpressuremanagement.data.local.SavedDevicesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceManagementViewModel(
    private val bleManager: BleManager
) : ViewModel() {
    
    private val _savedDevices = MutableStateFlow<List<SavedDevicesManager.SavedDevice>>(emptyList())
    val savedDevices: StateFlow<List<SavedDevicesManager.SavedDevice>> = _savedDevices.asStateFlow()
    
    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()
    
    init {
        loadSavedDevices()
    }
    
    private fun loadSavedDevices() {
        viewModelScope.launch {
            val devices = bleManager.bleRepository?.getSavedDevices() ?: emptyList()
            _savedDevices.value = devices
        }
    }
    
    fun deleteDevice(address: String) {
        viewModelScope.launch {
            _isDeleting.value = true
            try {
                bleManager.deleteDevice(address)
                loadSavedDevices() // Refresh the list
            } finally {
                _isDeleting.value = false
            }
        }
    }
    
    fun moveDeviceUp(address: String) {
        val currentDevices = _savedDevices.value.toMutableList()
        val currentIndex = currentDevices.indexOfFirst { it.address == address }
        
        if (currentIndex > 0) {
            // Swap with the device above
            val device = currentDevices.removeAt(currentIndex)
            currentDevices.add(currentIndex - 1, device)
            
            // Update the order in storage
            val newOrder = currentDevices.map { it.address }
            bleManager.reorderDevices(newOrder)
            
            // Update the UI
            _savedDevices.value = currentDevices
        }
    }
    
    fun moveDeviceDown(address: String) {
        val currentDevices = _savedDevices.value.toMutableList()
        val currentIndex = currentDevices.indexOfFirst { it.address == address }
        
        if (currentIndex < currentDevices.size - 1) {
            // Swap with the device below
            val device = currentDevices.removeAt(currentIndex)
            currentDevices.add(currentIndex + 1, device)
            
            // Update the order in storage
            val newOrder = currentDevices.map { it.address }
            bleManager.reorderDevices(newOrder)
            
            // Update the UI
            _savedDevices.value = currentDevices
        }
    }
    
    fun refreshDevices() {
        loadSavedDevices()
    }
}
