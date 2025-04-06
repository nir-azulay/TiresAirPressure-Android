package com.nazulay.tiresairpressuremanagement.utils

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionManager {
    private const val TAG = "PermissionManager"
    
    // Request code for Bluetooth permission
    const val REQUEST_BLUETOOTH_PERMISSIONS = 101
    
    // Request code for Bluetooth enable
    const val REQUEST_ENABLE_BLUETOOTH = 102

    fun requiredPermissions(context: Context): List<String> {
        val basePermissions = mutableListOf<String>()
        
        // Always add location permissions (required for BLE scanning on some devices)
        basePermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        basePermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        
        // Add Bluetooth permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 and above
            basePermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            basePermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            basePermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            // Android 11 and below
            basePermissions.add(Manifest.permission.BLUETOOTH)
            basePermissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        
        // Add WiFi permissions
        basePermissions.add(Manifest.permission.ACCESS_WIFI_STATE)
        basePermissions.add(Manifest.permission.CHANGE_WIFI_STATE)
        
        // Add notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            basePermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        return basePermissions
    }

    fun hasRequiredPermissions(context: Context): Boolean {
        return requiredPermissions(context).all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getMissingPermissions(context: Context): List<String> {
        return requiredPermissions(context).filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
    
    // Check if Bluetooth is enabled
    fun isBluetoothEnabled(context: Context): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter
        return bluetoothAdapter?.isEnabled == true
    }
    
    // Request to enable Bluetooth
    fun requestEnableBluetooth(activity: Activity) {
        try {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request Bluetooth enable: ${e.message}")
            // Fallback for devices that don't support the standard intent
            try {
                val settingsIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                activity.startActivity(settingsIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open Bluetooth settings: ${e2.message}")
            }
        }
    }
    
    // Request required permissions with retry mechanism for Chinese head units
    fun requestRequiredPermissions(activity: Activity) {
        val missingPermissions = getMissingPermissions(activity).toTypedArray()
        
        if (missingPermissions.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: ${missingPermissions.joinToString()}")
            
            try {
                ActivityCompat.requestPermissions(
                    activity,
                    missingPermissions,
                    REQUEST_BLUETOOTH_PERMISSIONS
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting permissions: ${e.message}")
                // Some Chinese head units may throw exceptions when requesting permissions
                // Try individually for each permission
                missingPermissions.forEach { permission ->
                    try {
                        ActivityCompat.requestPermissions(
                            activity,
                            arrayOf(permission),
                            REQUEST_BLUETOOTH_PERMISSIONS
                        )
                    } catch (e2: Exception) {
                        Log.e(TAG, "Error requesting permission $permission: ${e2.message}")
                    }
                }
            }
        }
    }
} 
