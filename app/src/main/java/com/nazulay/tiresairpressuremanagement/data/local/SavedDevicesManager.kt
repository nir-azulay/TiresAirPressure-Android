package com.nazulay.tiresairpressuremanagement.data.local

import android.content.Context
import android.util.Log

class SavedDevicesManager(private val context: Context) {
    companion object {
        private const val TAG = "SavedDevicesManager"
        private const val PREFS_NAME = "saved_devices"
        private const val KEY_DEVICE_1 = "device_1"
        private const val KEY_DEVICE_2 = "device_2"
        private const val KEY_DEVICE_1_NAME = "device_1_name"
        private const val KEY_DEVICE_2_NAME = "device_2_name"
        private const val KEY_DEVICE_1_CODE = "device_1_code"
        private const val KEY_DEVICE_2_CODE = "device_2_code"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class SavedDevice(
        val address: String,
        val name: String
    )

    fun getSavedDevices(): List<SavedDevice> {
        val devices = mutableListOf<SavedDevice>()
        
        // Get first device
        prefs.getString(KEY_DEVICE_1, null)?.let { address ->
            val name = prefs.getString(KEY_DEVICE_1_NAME, "") ?: ""
            devices.add(SavedDevice(address, name))
            Log.d(TAG, "Retrieved device 1: $name ($address)")
        }
        
        // Get second device
        prefs.getString(KEY_DEVICE_2, null)?.let { address ->
            val name = prefs.getString(KEY_DEVICE_2_NAME, "") ?: ""
            devices.add(SavedDevice(address, name))
            Log.d(TAG, "Retrieved device 2: $name ($address)")
        }
        
        Log.d(TAG, "Total saved devices retrieved: ${devices.size}")
        return devices
    }

    fun saveDevice(address: String, name: String) {
        Log.d(TAG, "Saving device: $name ($address)")
        
        // Get current devices
        val device1Address = prefs.getString(KEY_DEVICE_1, null)
        val device2Address = prefs.getString(KEY_DEVICE_2, null)
        
        Log.d(TAG, "Current saved devices - Device 1: $device1Address, Device 2: $device2Address")

        // If this device is already saved as device 1, just update its name
        if (address == device1Address) {
            Log.d(TAG, "Device already saved as device 1, updating name")
            prefs.edit()
                .putString(KEY_DEVICE_1_NAME, name)
                .apply()
            Log.d(TAG, "Device 1 name updated to: $name")
            return
        }

        // If this device is already saved as device 2, move it to position 1
        if (address == device2Address) {
            Log.d(TAG, "Device already saved as device 2, moving to position 1")
            val device2Code = prefs.getString(KEY_DEVICE_2_CODE, "") ?: ""
            prefs.edit()
                .putString(KEY_DEVICE_1, address)
                .putString(KEY_DEVICE_1_NAME, name)
                .putString(KEY_DEVICE_1_CODE, device2Code)
                .remove(KEY_DEVICE_2)
                .remove(KEY_DEVICE_2_NAME)
                .remove(KEY_DEVICE_2_CODE)
                .apply()
            Log.d(TAG, "Device moved to position 1: $name ($address)")
            return
        }

        // Always save the new device as device 1 (most recent)
        // Move current device 1 to position 2 if it exists
        Log.d(TAG, "Saving new device as device 1, moving old device 1 to position 2")
        val editor = prefs.edit()
        
        // Move current device 1 to position 2 (only if device 1 exists)
        if (device1Address != null) {
            editor.putString(KEY_DEVICE_2, device1Address)
            editor.putString(KEY_DEVICE_2_NAME, prefs.getString(KEY_DEVICE_1_NAME, ""))
            editor.putString(KEY_DEVICE_2_CODE, prefs.getString(KEY_DEVICE_1_CODE, ""))
        }
        
        // Save new device as device 1
        editor.putString(KEY_DEVICE_1, address)
        editor.putString(KEY_DEVICE_1_NAME, name)
        editor.putString(KEY_DEVICE_1_CODE, "")
        editor.apply()
        
        Log.d(TAG, "New device saved as device 1: $name ($address)")
        
        // Verify the save worked
        val savedDevice1 = prefs.getString(KEY_DEVICE_1, null)
        val savedDevice1Name = prefs.getString(KEY_DEVICE_1_NAME, "")
        Log.d(TAG, "Verification - Device 1 is now: $savedDevice1Name ($savedDevice1)")
    }

    fun clearSavedDevices() {
        prefs.edit().clear().apply()
    }
    
    fun clearOldDevices() {
        Log.d(TAG, "Clearing all saved devices to start fresh")
        prefs.edit().clear().apply()
    }
    
    fun ensureOnlyRecentDevice(address: String, name: String) {
        Log.d(TAG, "Ensuring only recent device is saved: $name ($address)")
        val accessCode = getAccessCode(address)
        
        // Clear all devices first
        clearOldDevices()
        
        // Save only the recent device as device 1
        saveDevice(address, name)
        if (accessCode.isNotEmpty())
            setAccessCode(address, accessCode)
        
        Log.d(TAG, "Device list cleaned up, only recent device saved")
    }
    
    fun deleteDevice(address: String) {
        Log.d(TAG, "Deleting device: $address")
        
        val device1Address = prefs.getString(KEY_DEVICE_1, null)
        val device2Address = prefs.getString(KEY_DEVICE_2, null)
        
        when (address) {
            device1Address -> {
                Log.d(TAG, "Deleting device 1: $address")
                if (device2Address != null) {
                    // Move device 2 to position 1
                    val device2Name = prefs.getString(KEY_DEVICE_2_NAME, "") ?: ""
                    val device2Code = prefs.getString(KEY_DEVICE_2_CODE, "") ?: ""
                    prefs.edit()
                        .putString(KEY_DEVICE_1, device2Address)
                        .putString(KEY_DEVICE_1_NAME, device2Name)
                        .putString(KEY_DEVICE_1_CODE, device2Code)
                        .remove(KEY_DEVICE_2)
                        .remove(KEY_DEVICE_2_NAME)
                        .remove(KEY_DEVICE_2_CODE)
                        .apply()
                    Log.d(TAG, "Moved device 2 to position 1")
                } else {
                    // No device 2, just clear device 1
                    prefs.edit()
                        .remove(KEY_DEVICE_1)
                        .remove(KEY_DEVICE_1_NAME)
                        .remove(KEY_DEVICE_1_CODE)
                        .apply()
                    Log.d(TAG, "Cleared device 1, no devices remaining")
                }
            }
            device2Address -> {
                Log.d(TAG, "Deleting device 2: $address")
                prefs.edit()
                    .remove(KEY_DEVICE_2)
                    .remove(KEY_DEVICE_2_NAME)
                    .remove(KEY_DEVICE_2_CODE)
                    .apply()
                Log.d(TAG, "Removed device 2")
            }
            else -> {
                Log.w(TAG, "Device not found for deletion: $address")
            }
        }
    }
    
    fun reorderDevices(deviceAddresses: List<String>) {
        Log.d(TAG, "Reordering devices: $deviceAddresses")
        
        if (deviceAddresses.isEmpty()) {
            Log.w(TAG, "Empty device list provided for reordering")
            return
        }
        
        val savedDevice1Code = prefs.getString(KEY_DEVICE_1_CODE, "") ?: ""
        val savedDevice2Code = prefs.getString(KEY_DEVICE_2_CODE, "") ?: ""
        val savedDevice1Address = prefs.getString(KEY_DEVICE_1, null)
        val savedDevice2Address = prefs.getString(KEY_DEVICE_2, null)
        fun savedCode(address: String): String {
            return when (address) {
                savedDevice1Address -> savedDevice1Code
                savedDevice2Address -> savedDevice2Code
                else -> ""
            }
        }
        val editor = prefs.edit()
        
        // Clear all devices first
        editor.remove(KEY_DEVICE_1)
        editor.remove(KEY_DEVICE_1_NAME)
        editor.remove(KEY_DEVICE_1_CODE)
        editor.remove(KEY_DEVICE_2)
        editor.remove(KEY_DEVICE_2_NAME)
        editor.remove(KEY_DEVICE_2_CODE)
        
        // Save devices in new order
        if (deviceAddresses.isNotEmpty()) {
            val device1Address = deviceAddresses[0]
            val device1Name = getDeviceNameByAddress(device1Address)
            editor.putString(KEY_DEVICE_1, device1Address)
            editor.putString(KEY_DEVICE_1_NAME, device1Name)
            editor.putString(KEY_DEVICE_1_CODE, savedCode(device1Address))
            Log.d(TAG, "Set device 1: $device1Name ($device1Address)")
        }
        
        if (deviceAddresses.size > 1) {
            val device2Address = deviceAddresses[1]
            val device2Name = getDeviceNameByAddress(device2Address)
            editor.putString(KEY_DEVICE_2, device2Address)
            editor.putString(KEY_DEVICE_2_NAME, device2Name)
            editor.putString(KEY_DEVICE_2_CODE, savedCode(device2Address))
            Log.d(TAG, "Set device 2: $device2Name ($device2Address)")
        }
        
        editor.apply()
        Log.d(TAG, "Device reordering completed")
    }
    
    fun getAccessCode(address: String): String {
        return when (address) {
            prefs.getString(KEY_DEVICE_1, null) -> prefs.getString(KEY_DEVICE_1_CODE, "") ?: ""
            prefs.getString(KEY_DEVICE_2, null) -> prefs.getString(KEY_DEVICE_2_CODE, "") ?: ""
            else -> ""
        }
    }

    fun setAccessCode(address: String, code: String) {
        val key = when (address) {
            prefs.getString(KEY_DEVICE_1, null) -> KEY_DEVICE_1_CODE
            prefs.getString(KEY_DEVICE_2, null) -> KEY_DEVICE_2_CODE
            else -> return
        }
        prefs.edit().putString(key, code).apply()
    }

    private fun getDeviceNameByAddress(address: String): String {
        val device1Address = prefs.getString(KEY_DEVICE_1, null)
        val device2Address = prefs.getString(KEY_DEVICE_2, null)
        
        return when (address) {
            device1Address -> prefs.getString(KEY_DEVICE_1_NAME, "") ?: ""
            device2Address -> prefs.getString(KEY_DEVICE_2_NAME, "") ?: ""
            else -> "Unknown Device"
        }
    }
} 