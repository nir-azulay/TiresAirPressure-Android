package com.example.tiresairpressuremanagement.data.ble

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.tiresairpressuremanagement.domain.models.BleCommand
import com.example.tiresairpressuremanagement.domain.models.BleDevice
import com.example.tiresairpressuremanagement.domain.models.DeviceSettings
import com.example.tiresairpressuremanagement.domain.models.DeviceStatus
import com.example.tiresairpressuremanagement.utils.BleConstants
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import android.app.Activity
import android.media.MediaPlayer
import com.example.tiresairpressuremanagement.R
import android.bluetooth.BluetoothStatusCodes
import org.json.JSONObject

class BleManager(private val context: Context) {
    private val TAG = "BleManager"
    private var bluetoothGatt: BluetoothGatt? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    private val _deviceStatus = MutableStateFlow<DeviceStatus?>(null)
    val deviceStatus: StateFlow<DeviceStatus?> = _deviceStatus
    
    private val _scanResults = MutableStateFlow<List<BleDevice>>(emptyList())
    val scanResults: StateFlow<List<BleDevice>> = _scanResults
    
    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }
    
    private val bleScanner by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }
    
    // Initialize MediaPlayer with the connection sound
    private val mediaPlayer: MediaPlayer = MediaPlayer.create(context, R.raw.connection_sound)
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: "Unknown Device"
            val deviceAddress = device.address
            val rssi = result.rssi

            // Log all found devices
            Log.d("BleManager", "Device found: $deviceName - $deviceAddress, RSSI: $rssi")
            
            // Filter for devices that are likely to be our target BLE devices
            // You can adjust these criteria based on your specific device characteristics
            if (isLikelyTargetDevice(device, deviceName, rssi)) {
                // Add to the list
                val bleDevice = BleDevice(deviceName, deviceAddress, rssi)
                val currentList = _scanResults.value.toMutableList()
                
                // Check if device with same address already exists in the list
                val existingDeviceIndex = currentList.indexOfFirst { it.address == deviceAddress }
                if (existingDeviceIndex >= 0) {
                    // Update the existing device with new RSSI
                    currentList[existingDeviceIndex] = bleDevice
                } else {
                    // Add new device to the list
                    currentList.add(bleDevice)
                }
                
                // Sort the list by RSSI (higher values = closer devices)
                val sortedList = currentList.sortedByDescending { it.rssi }
                
                _scanResults.value = sortedList
            }
        }
    }
    
    // Helper function to determine if a device is likely our target BLE device
    private fun isLikelyTargetDevice(device: BluetoothDevice, name: String, rssi: Int): Boolean {
        // Filter criteria - customize these based on your specific device characteristics
        
        // 1. RSSI threshold - ignore very weak signals (likely too far away)
        if (rssi < -90) return false
        
        // 2. Name-based filtering - if your devices have a specific naming pattern
        // For example, if your devices start with "TPMS" or contain "Tire"
        val namePattern = name.lowercase()
        if (namePattern.contains("tpms") || 
            namePattern.contains("tire") || 
            namePattern.contains("pressure")) {
            return true
        }
        
        // 3. If the device has no name but is a BLE device with reasonable signal strength
        if (name == "Unknown Device" && rssi > -80) {
            return true
        }
        
        // 4. Accept all devices with strong signal for testing
        // You can remove this in production if you have more specific criteria
        if (rssi > -70) {
            return true
        }
        
        return false
    }
    
    private fun debugBleServices(gatt: BluetoothGatt) {
        Log.d(TAG, "=== DEBUG: BLE SERVICES ===")
        gatt.services.forEach { service ->
            Log.d(TAG, "Service Found: ${service.uuid}")
            service.characteristics.forEach { characteristic ->
                val properties = StringBuilder()
                val props = characteristic.properties
                if ((props and BluetoothGattCharacteristic.PROPERTY_READ) != 0) properties.append("READ ")
                if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) properties.append("WRITE ")
                if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) properties.append("WRITE_NO_RESPONSE ")
                if ((props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) properties.append("NOTIFY ")
                
                Log.d(TAG, "  Characteristic: ${characteristic.uuid}")
                Log.d(TAG, "    Properties: $properties")
            }
        }
        Log.d(TAG, "=== END DEBUG ===")
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            android.util.Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")
            
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    android.util.Log.d(TAG, "Connected to GATT server")
                    
                    // שמירת המכשיר כנבחר
                    val deviceAddress = gatt.device?.address
                    if (deviceAddress != null) {
                        android.util.Log.d(TAG, "Saving connected device address: $deviceAddress")
                        saveSelectedDevice(deviceAddress)
                    }
                    
                    // השמעת צליל
                    mediaPlayer.start()
                    
                    // Important: Discover services before updating connection state
                    android.util.Log.d(TAG, "Starting service discovery")
                    val discoverStarted = gatt.discoverServices()
                    android.util.Log.d(TAG, "Service discovery started: $discoverStarted")
                    
                    if (discoverStarted == false) {
                        android.util.Log.e(TAG, "Failed to start service discovery")
                        disconnect()
                        return
                    }
                    
                    // עדכון מצב החיבור יתבצע רק לאחר גילוי השירותים
                    // _connectionState.value = ConnectionState.Connected will be set after service discovery
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    android.util.Log.d(TAG, "Disconnected from GATT server")
                    
                    // עדכון מצב החיבור
                    _connectionState.value = ConnectionState.Disconnected
                    android.util.Log.d(TAG, "Connection state set to DISCONNECTED")
                    
                    // סגירת לקוח GATT
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    
                    // Clear characteristics
                    notifyCharacteristic = null
                    commandCharacteristic = null
                    
                    // ניסיון להתחבר מחדש אם יש כתובת שמורה
                    val sharedPreferences = context.getSharedPreferences("DeviceSettings", Context.MODE_PRIVATE)
                    val savedDeviceAddress = sharedPreferences.getString("selectedDeviceAddress", null)
                    if (savedDeviceAddress != null && status != BluetoothGatt.GATT_SUCCESS) {
                        android.util.Log.d(TAG, "Attempting to reconnect to saved device: $savedDeviceAddress")
                        // השהייה קטנה לפני ניסיון חיבור מחדש
                        try {
                            Thread.sleep(1000)
                        } catch (e: InterruptedException) {
                            android.util.Log.e(TAG, "Sleep interrupted", e)
                        }
                        connect(savedDeviceAddress)
                    }
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered: status=$status")
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered successfully")
                
                // Debug all services and characteristics
                debugBleServices(gatt)
                
                val service = gatt.getService(BleConstants.SERVICE_UUID)
                Log.d(TAG, "Looking for service UUID: ${BleConstants.SERVICE_UUID}")
                
                if (service != null) {
                    Log.d(TAG, "Found required service: ${BleConstants.SERVICE_UUID}")
                    notifyCharacteristic = service.getCharacteristic(BleConstants.NOTIFICATION_CHARACTERISTIC_UUID)
                    commandCharacteristic = service.getCharacteristic(BleConstants.COMMAND_CHARACTERISTIC_UUID)
                    
                    Log.d(TAG, "Notify characteristic found: ${notifyCharacteristic != null}")
                    Log.d(TAG, "Command characteristic found: ${commandCharacteristic != null}")
                    
                    if (notifyCharacteristic != null && commandCharacteristic != null) {
                        Log.d(TAG, "Found all required characteristics")
                        
                        // Update connection state first
                        _connectionState.value = ConnectionState.Connected
                        Log.d(TAG, "Connection state set to CONNECTED")
                        
                        // Then enable notifications
                        try {
                            enableNotifications(gatt, notifyCharacteristic!!)
                            Log.d(TAG, "Notifications enabled successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to enable notifications", e)
                            disconnect()
                            return
                        }
                    } else {
                        Log.e(TAG, "Missing required characteristics!")
                        Log.e(TAG, "Notify UUID we're looking for: ${BleConstants.NOTIFICATION_CHARACTERISTIC_UUID}")
                        Log.e(TAG, "Command UUID we're looking for: ${BleConstants.COMMAND_CHARACTERISTIC_UUID}")
                        disconnect()
                    }
                } else {
                    Log.e(TAG, "Required service not found!")
                    disconnect()
                }
            } else {
                Log.e(TAG, "Service discovery failed with status: $status")
                disconnect()
            }
        }
        
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            android.util.Log.d(TAG, "onDescriptorWrite: status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                android.util.Log.d(TAG, "Descriptor write successful")
            } else {
                android.util.Log.e(TAG, "Descriptor write failed with status: $status")
                disconnect()
            }
        }
        
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            when (characteristic.uuid) {
                BleConstants.NOTIFICATION_CHARACTERISTIC_UUID -> {
                    val value = characteristic.value
                    val jsonString = String(value)
                    try {
                        val deviceStatus = moshi.adapter(DeviceStatus::class.java).fromJson(jsonString)
                        if (deviceStatus != null) {
                            onDeviceSettingsReceived(deviceStatus)
                        } else {
                            Log.e(TAG, "Device status is null")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Error parsing device status: ${e.message}")
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            when (characteristic.uuid) {
                BleConstants.NOTIFICATION_CHARACTERISTIC_UUID -> {
                    val jsonString = String(value)
                    try {
                        val deviceStatus = moshi.adapter(DeviceStatus::class.java).fromJson(jsonString)
                        if (deviceStatus != null) {
                            onDeviceSettingsReceived(deviceStatus)
                        } else {
                            Log.e(TAG, "Device status is null")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Error parsing device status: ${e.message}")
                    }
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.d(TAG, "onCharacteristicWrite callback - status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Write successful for characteristic: ${characteristic.uuid}")
            } else {
                Log.e(TAG, "Write failed for characteristic: ${characteristic.uuid}, status: $status")
                // If write fails, you might want to retry or notify the user
                when (status) {
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> Log.e(TAG, "Write not permitted")
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> Log.e(TAG, "Invalid attribute length")
                    BluetoothGatt.GATT_FAILURE -> Log.e(TAG, "Write failed with GATT_FAILURE")
                    else -> Log.e(TAG, "Write failed with status: $status")
                }
            }
        }
    }
    
    fun startScan() {
        Log.d("BleManager", "Starting BLE scan")
        val adapter = bluetoothAdapter
        if (adapter?.isEnabled == true) {
            if (hasRequiredPermissions()) {
                Log.d("BleManager", "Has required permissions, starting scan")
                val scanner = adapter.bluetoothLeScanner
                _scanResults.value = emptyList() // Clear previous results
                
                // Set up scan settings
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // Use low latency, high power scan mode
                    .setReportDelay(0) // Report results immediately
                    .build()
                
                // Start scan with settings
                scanner?.startScan(null, settings, scanCallback)
                Log.d("BleManager", "Scan started with low latency settings")
            } else {
                Log.e("BleManager", "Missing required permissions for BLE scan")
                // Request permissions
                if (context is Activity) {
                    requestRequiredPermissions(context as Activity)
                } else {
                    Log.e("BleManager", "Context is not an Activity, cannot request permissions")
                }
            }
        } else {
            Log.d("BleManager", "Bluetooth is disabled")
        }
    }
    
    fun stopScan() {
        Log.d("BleManager", "Stopping BLE scan")
        val adapter = bluetoothAdapter
        if (adapter?.isEnabled == true) {
            val scanner = adapter.bluetoothLeScanner
            scanner?.stopScan(scanCallback)
            Log.d("BleManager", "Scan stopped")
        }
    }
    
    fun connect(deviceAddress: String) {
        android.util.Log.d(TAG, "Attempting to connect to device: $deviceAddress")
        val adapter = bluetoothAdapter
        if (adapter?.isEnabled != true) {
            android.util.Log.e(TAG, "Cannot connect: Bluetooth is disabled")
            return
        }
        
        try {
            val device = adapter.getRemoteDevice(deviceAddress)
            if (device != null) {
                android.util.Log.d(TAG, "Got remote device, connecting...")
                
                // Close any existing connection
                bluetoothGatt?.disconnect()
                bluetoothGatt?.close()
                bluetoothGatt = null
                
                // Clear characteristics
                notifyCharacteristic = null
                commandCharacteristic = null
                
                // Small delay before connecting
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    android.util.Log.e(TAG, "Sleep interrupted", e)
                }
                
                // Connect with autoConnect = false for immediate connection
                bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(
                        context,
                        false, // Use false for immediate connection
                        gattCallback,
                        BluetoothDevice.TRANSPORT_LE
                    )
                } else {
                    device.connectGatt(context, false, gattCallback)
                }
                
                if (bluetoothGatt == null) {
                    android.util.Log.e(TAG, "Failed to create GATT connection")
                    return
                }
                
                android.util.Log.d(TAG, "Connection request sent to device")
                stopScan() // Stop scanning after connection attempt
            } else {
                android.util.Log.e(TAG, "Failed to get remote device")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error connecting to device", e)
        }
    }
    
    fun disconnect() {
        android.util.Log.d(TAG, "Disconnecting from device")
        
        // עדכון מצב החיבור
        _connectionState.value = ConnectionState.Disconnected
        android.util.Log.d(TAG, "Connection state set to DISCONNECTED")
        
        // ניתוק מהמכשיר
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
    
    fun sendCommand(command: BleCommand) {
        try {
            val currentCommandChar = commandCharacteristic
            val currentGatt = bluetoothGatt
            
            if (currentCommandChar == null) {
                Log.e(TAG, "Cannot send command: commandCharacteristic is null")
                return
            }
            if (currentGatt == null) {
                Log.e(TAG, "Cannot send command: bluetoothGatt is null")
                return
            }
            
            // Manually serialize the BleCommand object
            val jsonString = when (command) {
                is BleCommand.Stop -> {
                    JSONObject().apply {
                        put("command", command.command)
                        put("type", command.type)
                    }.toString()
                }
                is BleCommand.StartTargetPressure -> {
                    JSONObject().apply {
                        put("command", command.command)
                        put("targetPressure", command.targetPressure)
                        put("type", command.type)
                    }.toString()
                }
                is BleCommand.UpdateSettings -> {
                    JSONObject().apply {
                        put("command", command.command)
                        put("DeviceName", command.deviceName)
                        put("wifissid", command.wifiSsid)
                        put("wifipassword", command.wifiPassword)
                        put("RelayCompressorPin", command.relayCompressorPin)
                        put("RelayFreePin", command.relayFreePin)
                        put("SensorPin", command.sensorPin)
                        put("AutoStart", command.autoStart)
                        put("MaxSecForStatus", command.maxSecForStatus)
                        put("SensorValueAt0PSI", command.sensorValueAt0PSI)
                        put("SensorValueAt50PSI", command.sensorValueAt50PSI)
                        put("MessureWait", command.measureWait)
                        put("Precision", command.precision)
                        put("BTPinCode", command.btPinCode)
                        put("type", command.type)
                    }.toString()
                }
                is BleCommand.SimulationMode -> {
                    JSONObject().apply {
                        put("command", command.command)
                        put("enabled", command.enabled)
                        put("type", command.type)
                    }.toString()
                }
            }
            
            Log.d(TAG, "Sending command: $jsonString")
            
            // Check characteristic properties
            val properties = currentCommandChar.properties
            Log.d(TAG, "Command characteristic properties: $properties")
            
            // Verify write property
            if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE) == 0 &&
                (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0) {
                Log.e(TAG, "Command characteristic does not have write property")
                return
            }
            
            // Set write type based on properties
            val writeType = if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Log.d(TAG, "Using new API to write characteristic")
                try {
                    val success = currentGatt.writeCharacteristic(
                        currentCommandChar,
                        jsonString.toByteArray(),
                        writeType
                    )
                    Log.d(TAG, "Write characteristic result: $success")
                    if (success != BluetoothStatusCodes.SUCCESS) {
                        Log.e(TAG, "Failed to write characteristic, status: $success")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing characteristic (new API): ${e.message}")
                }
            } else {
                Log.d(TAG, "Using deprecated API to write characteristic")
                try {
                    @Suppress("DEPRECATION")
                    currentCommandChar.writeType = writeType
                    @Suppress("DEPRECATION")
                    currentCommandChar.value = jsonString.toByteArray()
                    @Suppress("DEPRECATION")
                    val success = currentGatt.writeCharacteristic(currentCommandChar)
                    Log.d(TAG, "Write characteristic result: $success")
                    if (success == false) {
                        Log.e(TAG, "Failed to write characteristic")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing characteristic (old API): ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendCommand: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        android.util.Log.d(TAG, "Enabling notifications for characteristic: ${characteristic.uuid}")
        
        // First enable the local notifications
        val success = gatt.setCharacteristicNotification(characteristic, true)
        if (success == false) {
            android.util.Log.e(TAG, "Failed to enable local notifications")
            throw Exception("Failed to enable local notifications")
        }
        android.util.Log.d(TAG, "Local notifications enabled successfully")
        
        // Then write to the descriptor if needed
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            try {
                val descriptor = characteristic.getDescriptor(BleConstants.CLIENT_CONFIG_DESCRIPTOR_UUID)
                if (descriptor == null) {
                    android.util.Log.e(TAG, "Notification descriptor not found")
                    throw Exception("Notification descriptor not found")
                }
                
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                val writeSuccess = gatt.writeDescriptor(descriptor)
                if (writeSuccess == false) {
                    android.util.Log.e(TAG, "Failed to write to descriptor")
                    throw Exception("Failed to write to descriptor")
                }
                android.util.Log.d(TAG, "Descriptor write initiated")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error writing descriptor", e)
                throw e
            }
        }
    }
    
    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    
    private fun hasPermission(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestRequiredPermissions(activity: Activity) {
        Log.d("BleManager", "Requesting required permissions")
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        
        ActivityCompat.requestPermissions(
            activity,
            permissions,
            REQUEST_BLUETOOTH_PERMISSIONS
        )
    }
    
    private fun saveDeviceName(deviceName: String) {
        val sharedPreferences = context.getSharedPreferences("DeviceSettings", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("deviceName", deviceName)
        editor.apply()
    }

    private fun saveDeviceSettings(deviceStatus: DeviceStatus) {
        val sharedPreferences = context.getSharedPreferences("DeviceSettings", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putFloat("pressure", deviceStatus.pressure)
        editor.putInt("operationMode", deviceStatus.operationMode)
        editor.apply()
    }

    // Call this function when device settings are received
    private fun onDeviceSettingsReceived(deviceStatus: DeviceStatus) {
        android.util.Log.d(TAG, "Received device status: $deviceStatus")
        saveDeviceSettings(deviceStatus)
        _deviceStatus.value = deviceStatus
    }
    
    fun saveSelectedDevice(deviceAddress: String) {
        val sharedPreferences = context.getSharedPreferences("DeviceSettings", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("selectedDeviceAddress", deviceAddress)
        editor.apply()
    }

    fun connectToSavedDevice() {
        val sharedPreferences = context.getSharedPreferences("DeviceSettings", Context.MODE_PRIVATE)
        val deviceAddress = sharedPreferences.getString("selectedDeviceAddress", null)
        if (deviceAddress != null) {
            Log.d(TAG, "Attempting to connect to saved device: $deviceAddress")
            
            // Check if we're already connected to this device
            if (_connectionState.value == ConnectionState.Connected && 
                bluetoothGatt?.device?.address == deviceAddress) {
                Log.d(TAG, "Already connected to this device, no need to reconnect")
                return
            }
            
            // Small delay before connecting
            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                Log.e(TAG, "Sleep interrupted: ${e.message}")
            }
            
            // Connect to the saved device
            connect(deviceAddress)
        } else {
            Log.d(TAG, "No saved device to connect to")
        }
    }
    
    // Add a method to check if we're connected to a specific device
    fun isConnectedToDevice(deviceAddress: String): Boolean {
        return _connectionState.value == ConnectionState.Connected && 
               bluetoothGatt?.device?.address == deviceAddress
    }
    
    companion object {
        const val REQUEST_BLUETOOTH_PERMISSIONS = 1001
    }
}

enum class ConnectionState {
    Connected,
    Disconnected
} 