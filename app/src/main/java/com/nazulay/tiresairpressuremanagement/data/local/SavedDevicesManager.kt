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
        }
        
        // Get second device
        prefs.getString(KEY_DEVICE_2, null)?.let { address ->
            val name = prefs.getString(KEY_DEVICE_2_NAME, "") ?: ""
            devices.add(SavedDevice(address, name))
        }
        
        return devices
    }

    fun saveDevice(address: String, name: String) {
        Log.d(TAG, "Saving device: $address, $name")
        
        // Get current devices
        val device1Address = prefs.getString(KEY_DEVICE_1, null)
        val device2Address = prefs.getString(KEY_DEVICE_2, null)

        // If this device is already saved as device 1, just update its name
        if (address == device1Address) {
            prefs.edit()
                .putString(KEY_DEVICE_1_NAME, name)
                .apply()
            return
        }

        // If this device is already saved as device 2, move it to position 1
        if (address == device2Address) {
            prefs.edit()
                .putString(KEY_DEVICE_1, address)
                .putString(KEY_DEVICE_1_NAME, name)
                .remove(KEY_DEVICE_2)
                .remove(KEY_DEVICE_2_NAME)
                .apply()
            return
        }

        // Move device 1 to position 2, and save new device as device 1
        prefs.edit()
            .putString(KEY_DEVICE_2, device1Address)
            .putString(KEY_DEVICE_2_NAME, prefs.getString(KEY_DEVICE_1_NAME, ""))
            .putString(KEY_DEVICE_1, address)
            .putString(KEY_DEVICE_1_NAME, name)
            .apply()
    }

    fun clearSavedDevices() {
        prefs.edit().clear().apply()
    }
} 