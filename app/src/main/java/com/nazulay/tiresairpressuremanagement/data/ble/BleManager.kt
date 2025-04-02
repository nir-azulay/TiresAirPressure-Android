package com.nazulay.tiresairpressuremanagement.data.ble

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.nazulay.tiresairpressuremanagement.domain.models.BleCommand
import com.nazulay.tiresairpressuremanagement.domain.models.BleDevice
import com.nazulay.tiresairpressuremanagement.domain.models.DeviceSettings
import com.nazulay.tiresairpressuremanagement.domain.models.DeviceStatus
import com.nazulay.tiresairpressuremanagement.domain.models.DeviceSettingsResponse
import com.nazulay.tiresairpressuremanagement.utils.BleConstants
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import com.squareup.moshi.FromJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import android.app.Activity
import android.media.MediaPlayer
import com.nazulay.tiresairpressuremanagement.R
import android.bluetooth.BluetoothStatusCodes
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import androidx.core.app.NotificationCompat
import android.app.PendingIntent
import android.content.Intent
import com.nazulay.tiresairpressuremanagement.data.repository.BleRepository

class BleManager(
    private val context: Context,
    private val bleRepository: BleRepository? = null
) {
    private val TAG = "BleManager"
    private var bluetoothGatt: BluetoothGatt? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    
    // משתנה לעקוב אחרי פקודות ניפוח/הוצאת אוויר כדי להתעלם מהודעת הסטטוס הראשונה
    private var ignoreNextStatusUpdate = false
    
    // Simplified message handling now that MTU size is correct
    private var settingsRequestAttempts = 0
    private val MAX_SETTINGS_REQUEST_ATTEMPTS = 3
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .add(DeviceSettingsResponseAdapter())
        .build()
    
    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName
    
    private var connectionStartTime: Long = 0
    
    private val _deviceStatus = MutableStateFlow<DeviceStatus?>(null)
    val deviceStatus: StateFlow<DeviceStatus?> = _deviceStatus
    
    private val _deviceSettings = MutableStateFlow<DeviceSettings?>(null)
    val deviceSettings: StateFlow<DeviceSettings?> = _deviceSettings
    
    private val _scanResults = MutableStateFlow<List<BleDevice>>(emptyList())
    val scanResults: StateFlow<List<BleDevice>> = _scanResults
    
    // Add a flow for target reached events (targetPressure, currentPressure)
    private val _targetReachedEvent = MutableSharedFlow<Pair<Float, Float>>(replay = 1, extraBufferCapacity = 1)
    val targetReachedEvent: SharedFlow<Pair<Float, Float>> = _targetReachedEvent
    
    // Variables for controlling scan result sorting
    private var lastSortTime = 0L
    private var sortCount = 0
    private val MAX_SORT_COUNT = 5
    private val SORT_INTERVAL = 10000L // 10 seconds in milliseconds
    private val unsortedScanResults = mutableListOf<BleDevice>()
    
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
    
    private val _currentPressure = MutableStateFlow(0f)
    val currentPressure: StateFlow<Float> = _currentPressure

    private val _targetPressure = MutableStateFlow(30f)
    val targetPressure: StateFlow<Float> = _targetPressure

    private val _isInflating = MutableStateFlow(false)
    val isInflating: StateFlow<Boolean> = _isInflating
    
    private var connectedDevice: BluetoothDevice? = null
    private var gatt: BluetoothGatt? = null
    private var isGattOperationsStarted = false
    
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
                
                // Check if device with same address already exists in the list
                val existingDeviceIndex = unsortedScanResults.indexOfFirst { it.address == deviceAddress }
                if (existingDeviceIndex >= 0) {
                    // Update the existing device with new RSSI
                    unsortedScanResults[existingDeviceIndex] = bleDevice
                } else {
                    // Add new device to the list
                    unsortedScanResults.add(bleDevice)
                    
                    // מיד מוסיף את המכשיר החדש לרשימה המוצגת (ללא מיון)
                    val currentList = _scanResults.value.toMutableList()
                    currentList.add(bleDevice)
                    _scanResults.value = currentList
                    
                    Log.d("BleManager", "Added new device to displayed list: $deviceName")
                }
                
                // Check if it's time to sort and update the list
                val currentTime = System.currentTimeMillis()
                if (sortCount < MAX_SORT_COUNT && currentTime - lastSortTime >= SORT_INTERVAL) {
                    // Sort the list by RSSI (higher values = closer devices)
                    val sortedList = unsortedScanResults.sortedByDescending { it.rssi }
                    
                    _scanResults.value = sortedList
                    lastSortTime = currentTime
                    sortCount++
                    
                    Log.d("BleManager", "Sorted scan results (sort #$sortCount): ${sortedList.size} devices")
                }
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
                    connectionStartTime = System.currentTimeMillis()
                    
                    // שמירת המכשיר כנבחר
                    val deviceAddress = gatt.device?.address
                    val deviceName = gatt.device?.name
                    if (deviceAddress != null) {
                        android.util.Log.d(TAG, "Saving connected device address: $deviceAddress")
                        saveSelectedDevice(deviceAddress)
                        _connectedDeviceName.value = deviceName
                    }
                    
                    // השמעת צליל
                    mediaPlayer.start()
                    
                    // Request larger MTU size before discovering services
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        android.util.Log.d(TAG, "Requesting larger MTU size")
                        val mtuSize = 517 // Request exactly 517 bytes MTU size to match the BLE device's configuration
                        val mtuResult = gatt.requestMtu(mtuSize)
                        android.util.Log.d(TAG, "MTU request result: $mtuResult")
                        // Service discovery will be started in onMtuChanged callback
                        
                        // If MTU request fails, proceed with service discovery
                        if (!mtuResult) {
                            android.util.Log.d(TAG, "MTU request failed, proceeding with service discovery")
                            startServiceDiscovery(gatt)
                        }
                    } else {
                        // For older Android versions, just discover services
                        startServiceDiscovery(gatt)
                    }
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    android.util.Log.d(TAG, "Disconnected from GATT server")
                    
                    // עדכון מצב החיבור
                    _connectionState.value = ConnectionState.Disconnected
                    _connectedDeviceName.value = null
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
        
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            android.util.Log.d(TAG, "onMtuChanged: mtu=$mtu, status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                android.util.Log.d(TAG, "MTU size changed successfully to $mtu")
            } else {
                android.util.Log.e(TAG, "Failed to change MTU size, status: $status")
            }
            
            // Now discover services
            startServiceDiscovery(gatt)
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered: status=$status")
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered successfully")
                
                // Debug all services and characteristics
                debugBleServices(gatt)
                
                // Look for our specific service
                val service = gatt.getService(BleConstants.SERVICE_UUID)
                Log.d(TAG, "Looking for service UUID: ${BleConstants.SERVICE_UUID}")
                
                if (service != null) {
                    Log.d(TAG, "Found required service: ${BleConstants.SERVICE_UUID}")
                    
                    // Try to find our characteristics
                    notifyCharacteristic = service.getCharacteristic(BleConstants.NOTIFICATION_CHARACTERISTIC_UUID)
                    commandCharacteristic = service.getCharacteristic(BleConstants.COMMAND_CHARACTERISTIC_UUID)
                    
                    Log.d(TAG, "Notify characteristic found: ${notifyCharacteristic != null}")
                    Log.d(TAG, "Command characteristic found: ${commandCharacteristic != null}")
                    
                    // Check if we found both characteristics
                    if (notifyCharacteristic != null && commandCharacteristic != null) {
                        Log.d(TAG, "Found all required characteristics")
                        
                        // Log the properties of the notify characteristic
                        val notifyProps = notifyCharacteristic!!.properties
                        Log.d(TAG, "Notify characteristic properties: $notifyProps")
                        Log.d(TAG, "Has NOTIFY: ${(notifyProps and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0}")
                        Log.d(TAG, "Has INDICATE: ${(notifyProps and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0}")
                        Log.d(TAG, "Has READ: ${(notifyProps and BluetoothGattCharacteristic.PROPERTY_READ) != 0}")
                        
                        // Log the properties of the command characteristic
                        val commandProps = commandCharacteristic!!.properties
                        Log.d(TAG, "Command characteristic properties: $commandProps")
                        Log.d(TAG, "Has WRITE: ${(commandProps and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0}")
                        Log.d(TAG, "Has WRITE_NO_RESPONSE: ${(commandProps and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0}")
                        
                        // Update connection state first
                        _connectionState.value = ConnectionState.Connected
                        Log.d(TAG, "Connection state set to CONNECTED")
                        
                        // Reset settings request attempts
                        settingsRequestAttempts = 0
                        
                        // Then enable notifications
                        try {
                            enableNotifications(gatt, notifyCharacteristic!!)
                            Log.d(TAG, "Notifications enabled successfully")
                            
                            // Schedule a delayed settings request in case we don't receive settings automatically
                            scheduleSettingsRequest()
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
                    // If we didn't find our specific service, try to find any service with notify characteristics
                    Log.d(TAG, "Required service not found, looking for any service with notify characteristics")
                    
                    var foundNotifyCharacteristic = false
                    
                    // Loop through all services
                    for (service in gatt.services) {
                        Log.d(TAG, "Checking service: ${service.uuid}")
                        
                        // Loop through all characteristics in this service
                        for (characteristic in service.characteristics) {
                            val props = characteristic.properties
                            
                            // Check if this characteristic has notify property
                            if ((props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                                Log.d(TAG, "Found characteristic with NOTIFY property: ${characteristic.uuid}")
                                
                                // Try to use this characteristic for notifications
                                notifyCharacteristic = characteristic
                                foundNotifyCharacteristic = true
                                
                                // Look for a characteristic with write property in the same service
                                for (cmdChar in service.characteristics) {
                                    val cmdProps = cmdChar.properties
                                    if ((cmdProps and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                                        (cmdProps and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                                        Log.d(TAG, "Found characteristic with WRITE property: ${cmdChar.uuid}")
                                        commandCharacteristic = cmdChar
                                        break
                                    }
                                }
                                
                                break
                            }
                        }
                        
                        if (foundNotifyCharacteristic) {
                            break
                        }
                    }
                    
                    if (foundNotifyCharacteristic) {
                        Log.d(TAG, "Found alternative notify characteristic, trying to use it")
                        
                        // Update connection state
                        _connectionState.value = ConnectionState.Connected
                        Log.d(TAG, "Connection state set to CONNECTED")
                        
                        // Enable notifications
                        try {
                            enableNotifications(gatt, notifyCharacteristic!!)
                            Log.d(TAG, "Notifications enabled successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to enable notifications", e)
                            disconnect()
                        }
                    } else {
                        Log.e(TAG, "No notify characteristics found in any service")
                        disconnect()
                    }
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
            android.util.Log.d(TAG, "onCharacteristicChanged (deprecated) called for characteristic: ${characteristic.uuid}")
            
            when (characteristic.uuid) {
                BleConstants.NOTIFICATION_CHARACTERISTIC_UUID -> {
                    val value = characteristic.value
                    val jsonString = String(value)
                    Log.d(TAG, "Received JSON (deprecated API): $jsonString")
                    
                    // Process the complete message directly
                    processMessage(jsonString)
                }
                else -> {
                    android.util.Log.d(TAG, "Received notification from unknown characteristic: ${characteristic.uuid}")
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            android.util.Log.d(TAG, "onCharacteristicChanged (new API) called for characteristic: ${characteristic.uuid}")
            
            when (characteristic.uuid) {
                BleConstants.NOTIFICATION_CHARACTERISTIC_UUID -> {
                    val jsonString = String(value)
                    Log.d(TAG, "Received JSON (new API): $jsonString")
                    
                    // Process the complete message directly
                    processMessage(jsonString)
                }
                else -> {
                    android.util.Log.d(TAG, "Received notification from unknown characteristic: ${characteristic.uuid}")
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
        // First check if Bluetooth is enabled
        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth is not enabled")
            _connectionState.value = ConnectionState.BluetoothDisabled
            return
        }

        // Then check permissions
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing required permissions")
            _connectionState.value = ConnectionState.PermissionDenied
            return
        }
        
        try {
            _connectionState.value = ConnectionState.Scanning
            bleScanner?.startScan(
                emptyList(),
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build(),
                scanCallback
            ) ?: run {
                Log.e(TAG, "BLE Scanner is null")
                _connectionState.value = ConnectionState.Error
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while starting scan: ${e.message}")
            _connectionState.value = ConnectionState.PermissionDenied
        } catch (e: Exception) {
            Log.e(TAG, "Error starting scan: ${e.message}")
            _connectionState.value = ConnectionState.Error
        }
    }
    
    fun stopScan() {
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Cannot stop scan: missing required permissions")
            return
        }
        
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_SCAN permission not granted")
            return
        }
        
        bleScanner?.stopScan(scanCallback)
        if (_connectionState.value == ConnectionState.Scanning) {
            _connectionState.value = ConnectionState.Disconnected
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
            // No need to clear buffer anymore
            
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
                    Thread.sleep(500) // Increased delay for better stability
                } catch (e: InterruptedException) {
                    android.util.Log.e(TAG, "Sleep interrupted", e)
                }
                
                // Set connection state to connecting
                _connectionState.value = ConnectionState.Connecting
                android.util.Log.d(TAG, "Connection state set to CONNECTING")
                
                // Connect with autoConnect = true for automatic reconnection
                bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.util.Log.d(TAG, "Using Android M+ connection API with LE transport")
                    device.connectGatt(
                        context,
                        true, // Use true for automatic reconnection
                        gattCallback,
                        BluetoothDevice.TRANSPORT_LE
                    )
                } else {
                    android.util.Log.d(TAG, "Using legacy connection API")
                    device.connectGatt(context, true, gattCallback)
                }
                
                if (bluetoothGatt == null) {
                    android.util.Log.e(TAG, "Failed to create GATT connection")
                    _connectionState.value = ConnectionState.Disconnected
                    scheduleReconnect(deviceAddress)
                    return
                }
                
                android.util.Log.d(TAG, "Connection request sent to device")
                stopScan() // Stop scanning after connection attempt
                
                // Schedule a connection timeout
                scheduleConnectionTimeout(deviceAddress)
            } else {
                android.util.Log.e(TAG, "Failed to get remote device")
                _connectionState.value = ConnectionState.Disconnected
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error connecting to device", e)
            _connectionState.value = ConnectionState.Disconnected
            scheduleReconnect(deviceAddress)
        }
    }
    
    // Schedule a connection timeout to prevent hanging in connecting state
    private fun scheduleConnectionTimeout(deviceAddress: String) {
        android.util.Log.d(TAG, "Scheduling connection timeout")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (_connectionState.value == ConnectionState.Connecting) {
                android.util.Log.e(TAG, "Connection timeout occurred")
                disconnect()
                
                // Try to reconnect
                scheduleReconnect(deviceAddress)
            }
        }, 10000) // 10 second timeout
    }
    
    // Schedule a reconnection attempt
    private fun scheduleReconnect(deviceAddress: String) {
        android.util.Log.d(TAG, "Scheduling reconnection attempt")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (_connectionState.value != ConnectionState.Connected) {
                android.util.Log.d(TAG, "Attempting to reconnect to device: $deviceAddress")
                connect(deviceAddress)
            }
        }, 3000) // Wait 3 seconds before reconnecting
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
            
            // בדיקה אם זו פקודת ניפוח או הוצאת אוויר כדי להתעלם מהודעת הסטטוס הבאה
            if (command is BleCommand.StartTargetPressure) {
                Log.d(TAG, "Setting flag to ignore next status update after INFLATE/DEFLATE command")
                ignoreNextStatusUpdate = true
                
                // הגדרת טיימר שיאפס את הדגל אוטומטית אחרי 2 שניות למקרה שלא קיבלנו הודעת סטטוס
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (ignoreNextStatusUpdate) {
                        Log.d(TAG, "Resetting ignore flag automatically after timeout")
                        ignoreNextStatusUpdate = false
                    }
                }, 2000) // 2 שניות
            } else if (command is BleCommand.Stop) {
                // אם זו פקודת עצירה, נאפס את הדגל כדי לא להתעלם מהודעות סטטוס
                Log.d(TAG, "Resetting ignore flag for STOP command")
                ignoreNextStatusUpdate = false
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
                        put("SimulationMode", command.simulationMode)
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
                is BleCommand.RequestSettings -> {
                    JSONObject().apply {
                        put("command", command.command)
                        put("type", command.type)
                    }.toString()
                }
                is BleCommand.RequestStatus -> {
                    JSONObject().apply {
                        put("command", command.command)
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
        
        // Check if the characteristic has the NOTIFY property
        val properties = characteristic.properties
        if ((properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
            android.util.Log.e(TAG, "Characteristic does not have NOTIFY property")
            throw Exception("Characteristic does not have NOTIFY property")
        }
        android.util.Log.d(TAG, "Characteristic has NOTIFY property")
        
        // First enable the local notifications
        val success = gatt.setCharacteristicNotification(characteristic, true)
        if (success == false) {
            android.util.Log.e(TAG, "Failed to enable local notifications")
            throw Exception("Failed to enable local notifications")
        }
        android.util.Log.d(TAG, "Local notifications enabled successfully")
        
        // Then write to the descriptor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // For Android 13+, we need to use the new API
            try {
                android.util.Log.d(TAG, "Using Android 13+ API to enable notifications")
                val descriptorUuid = BleConstants.CLIENT_CONFIG_DESCRIPTOR_UUID
                val status = gatt.writeDescriptor(
                    BluetoothGattDescriptor(
                        descriptorUuid,
                        BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                    ),
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                )
                android.util.Log.d(TAG, "Descriptor write status: $status")
                if (status != BluetoothStatusCodes.SUCCESS) {
                    android.util.Log.e(TAG, "Failed to write to descriptor: $status")
                    // Don't throw an exception here, some devices don't require descriptor write
                    android.util.Log.d(TAG, "Continuing anyway, as some devices don't require descriptor write")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error writing descriptor (new API): ${e.message}")
                android.util.Log.d(TAG, "Continuing anyway, as some devices don't require descriptor write")
            }
        } else {
            // For older Android versions, use the deprecated API
            try {
                android.util.Log.d(TAG, "Using older Android API to enable notifications")
                val descriptor = characteristic.getDescriptor(BleConstants.CLIENT_CONFIG_DESCRIPTOR_UUID)
                if (descriptor == null) {
                    android.util.Log.e(TAG, "Notification descriptor not found")
                    // Don't throw an exception here, some devices don't require descriptor write
                    android.util.Log.d(TAG, "Continuing anyway, as some devices don't require descriptor write")
                    return
                }
                
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                val writeSuccess = gatt.writeDescriptor(descriptor)
                if (writeSuccess == false) {
                    android.util.Log.e(TAG, "Failed to write to descriptor")
                    // Don't throw an exception here, some devices don't require descriptor write
                    android.util.Log.d(TAG, "Continuing anyway, as some devices don't require descriptor write")
                }
                android.util.Log.d(TAG, "Descriptor write initiated")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error writing descriptor (old API): ${e.message}")
                android.util.Log.d(TAG, "Continuing anyway, as some devices don't require descriptor write")
            }
        }
        
        // Request a settings update to trigger a notification
        try {
            android.util.Log.d(TAG, "Requesting settings update to trigger notification")
            sendCommand(BleCommand.RequestSettings())
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error requesting settings: ${e.message}")
        }
    }
    
    private fun hasRequiredPermissions(): Boolean {
        // Always check location permissions regardless of Android version
        val hasLocationPermissions = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
                                   hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        
        // Check Bluetooth permissions based on Android version
        val hasBluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(Manifest.permission.BLUETOOTH) &&
            hasPermission(Manifest.permission.BLUETOOTH_ADMIN)
        }
        
        return hasLocationPermissions && hasBluetoothPermissions
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
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
        
        // בדיקה אילו הרשאות חסרות
        val missingPermissions = permissions.filter { permission ->
            ActivityCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        // בקשת ההרשאות החסרות
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                missingPermissions,
                REQUEST_BLUETOOTH_PERMISSIONS
            )
        }
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

    private fun saveFullDeviceSettings(settings: DeviceSettingsResponse) {
        val sharedPreferences = context.getSharedPreferences("DeviceSettings", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        
        // Save all settings
        editor.putString("deviceName", settings.deviceName)
        editor.putString("wifiSsid", settings.wifiSsid)
        editor.putString("wifiPassword", settings.wifiPassword)
        editor.putInt("relayCompressorPin", settings.relayCompressorPin.toIntOrNull() ?: 0)
        editor.putInt("relayFreePin", settings.relayFreePin.toIntOrNull() ?: 0)
        editor.putInt("sensorPin", settings.sensorPin.toIntOrNull() ?: 0)
        editor.putBoolean("autoStart", settings.autoStart == "1" || settings.autoStart.lowercase() == "true")
        editor.putInt("maxSecForStatus", settings.maxSecForStatus.toIntOrNull() ?: 30)
        editor.putInt("sensorValueAt0PSI", settings.sensorValueAt0PSI.toIntOrNull() ?: 0)
        editor.putInt("sensorValueAt50PSI", settings.sensorValueAt50PSI.toIntOrNull() ?: 1023)
        editor.putFloat("measureWait", settings.measureWait.toFloatOrNull() ?: 2f)
        editor.putFloat("precision", settings.precision.toFloatOrNull() ?: 1f)
        editor.putString("btPinCode", settings.btPinCode)
        editor.putFloat("pressure", settings.pressure.toFloatOrNull() ?: 0f)
        editor.putString("softwareVersion", settings.softwareVersion)
        editor.putString("ipAddress", settings.ipAddress)
        editor.putBoolean("simulationMode", settings.simulationMode == "1" || settings.simulationMode.lowercase() == "true")
        
        editor.apply()
    }

    // Call this function when device status is received
    private fun onDeviceStatusReceived(deviceStatus: DeviceStatus) {
        android.util.Log.d(TAG, "Received device status: $deviceStatus")
        
        // בדיקה אם צריך להתעלם מהודעת הסטטוס הזו
        if (ignoreNextStatusUpdate) {
            android.util.Log.d(TAG, "Ignoring this status update as it came immediately after INFLATE/DEFLATE command")
            ignoreNextStatusUpdate = false
            return
        }
        
        saveDeviceSettings(deviceStatus)
        
        // Update the StateFlow on the main thread to ensure proper UI updates
        CoroutineScope(Dispatchers.Main).launch {
            android.util.Log.d(TAG, "Updating _deviceStatus StateFlow with: $deviceStatus")
            _deviceStatus.value = deviceStatus
            
            // CRITICAL: Also update the _currentPressure flow used by Android Auto
            val newPressure = deviceStatus.pressure
            if (_currentPressure.value != newPressure) {
                android.util.Log.d(TAG, "Updating _currentPressure to: $newPressure")
                _currentPressure.value = newPressure
            }
            
            // Update operation mode (inflation status)
            val isCurrentlyInflating = deviceStatus.operationMode == DeviceStatus.MODE_INFLATE
            if (_isInflating.value != isCurrentlyInflating) {
                android.util.Log.d(TAG, "Updating _isInflating to: $isCurrentlyInflating")
                _isInflating.value = isCurrentlyInflating
            }
            
            android.util.Log.d(TAG, "StateFlow updated, new values: status=${_deviceStatus.value}, pressure=${_currentPressure.value}, inflating=${_isInflating.value}")
        }
    }
    
    // Call this function when full device settings are received
    private fun onDeviceFullSettingsReceived(settingsResponse: DeviceSettingsResponse) {
        android.util.Log.d(TAG, "Received full device settings: $settingsResponse")
        saveFullDeviceSettings(settingsResponse)
        
        // Parse the pressure from the response
        val pressureValue = settingsResponse.pressure.toFloatOrNull() ?: 0f
        
        // Update both device status and device settings
        _deviceStatus.value = DeviceStatus(
            pressure = pressureValue,
            operationMode = settingsResponse.operationMode
        )
        
        // Also update the _currentPressure StateFlow
        if (_currentPressure.value != pressureValue) {
            android.util.Log.d(TAG, "Updating _currentPressure from settings to: $pressureValue")
            _currentPressure.value = pressureValue
        }
        
        _deviceSettings.value = settingsResponse.toDeviceSettings()
    }
    
    fun saveSelectedDevice(deviceAddress: String) {
        val sharedPreferences = context.getSharedPreferences("DeviceSettings", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        
        // Get the current saved device
        val currentSavedDevice = sharedPreferences.getString("selectedDeviceAddress", null)
        
        if (currentSavedDevice != null && currentSavedDevice != deviceAddress) {
            // If we have a different device saved, move it to second position
            editor.putString("secondDeviceAddress", currentSavedDevice)
        }
        
        // Save the new device as the primary device
        editor.putString("selectedDeviceAddress", deviceAddress)
        editor.apply()
    }

    fun connectToSavedDevice() {
        val sharedPreferences = context.getSharedPreferences("DeviceSettings", Context.MODE_PRIVATE)
        val primaryDeviceAddress = sharedPreferences.getString("selectedDeviceAddress", null)
        val secondaryDeviceAddress = sharedPreferences.getString("secondDeviceAddress", null)
        
        if (primaryDeviceAddress != null) {
            Log.d(TAG, "Attempting to connect to primary saved device: $primaryDeviceAddress")
            
            // Check if we're already connected to this device
            if (_connectionState.value == ConnectionState.Connected && 
                bluetoothGatt?.device?.address == primaryDeviceAddress) {
                Log.d(TAG, "Already connected to primary device, no need to reconnect")
                return
            }
            
            // Small delay before connecting
            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                Log.e(TAG, "Sleep interrupted: ${e.message}")
            }
            
            // Connect to the primary device
            connect(primaryDeviceAddress)
        } else if (secondaryDeviceAddress != null) {
            Log.d(TAG, "No primary device found, attempting to connect to secondary device: $secondaryDeviceAddress")
            
            // Check if we're already connected to this device
            if (_connectionState.value == ConnectionState.Connected && 
                bluetoothGatt?.device?.address == secondaryDeviceAddress) {
                Log.d(TAG, "Already connected to secondary device, no need to reconnect")
                return
            }
            
            // Small delay before connecting
            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                Log.e(TAG, "Sleep interrupted: ${e.message}")
            }
            
            // Connect to the secondary device
            connect(secondaryDeviceAddress)
        } else {
            Log.d(TAG, "No saved devices to connect to")
        }
    }
    
    // Add a method to check if we're connected to a specific device
    fun isConnectedToDevice(deviceAddress: String): Boolean {
        return _connectionState.value == ConnectionState.Connected && 
               bluetoothGatt?.device?.address == deviceAddress
    }
    
    // Simplified message processing now that we receive complete messages
    private fun processMessage(jsonString: String) {
        try {
            Log.d(TAG, "Processing JSON message: $jsonString")
            
            // Try direct JSONObject parsing first which is more forgiving with malformed JSON
            try {
                val jsonObject = JSONObject(jsonString)
                
                // Check if it's a target reached notification
                if (jsonObject.has("type") && jsonObject.getString("type") == "target_reached") {
                    Log.d(TAG, "Detected target reached notification")
                    
                    val targetPressure = jsonObject.optString("targetPressure", "0").toFloatOrNull() ?: 0f
                    val currentPressure = jsonObject.optString("currentPressure", "0").toFloatOrNull() ?: 0f
                    
                    // Handle target reached notification
                    onTargetPressureReached(targetPressure, currentPressure)
                    return
                }
                
                // Check if it's a settings response (has DeviceName)
                if (jsonObject.has("DeviceName")) {
                    Log.d(TAG, "Detected settings response with DeviceName")
                    
                    // Convert JSONObject to Map for our adapter
                    val jsonMap = mutableMapOf<String, Any?>()
                    val keys = jsonObject.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        jsonMap[key] = jsonObject.get(key)
                    }
                    
                    val settingsAdapter = DeviceSettingsResponseAdapter()
                    val settingsResponse = settingsAdapter.fromJson(jsonMap)
                    onDeviceFullSettingsReceived(settingsResponse)
                    return
                }
                
                // Check for status update with different possible field names
                val hasPressureField = jsonObject.has("CurrentPressure") || jsonObject.has("Pressure")
                val hasOperationMode = jsonObject.has("operationMode")
                
                if (hasPressureField && hasOperationMode) {
                    Log.d(TAG, "Detected status update with pressure and operationMode")
                    
                    // Try to get pressure from either field name
                    val pressure = when {
                        jsonObject.has("CurrentPressure") -> {
                            val value = jsonObject.optString("CurrentPressure", "0").toFloatOrNull() ?: 0f
                            Log.d(TAG, "Using CurrentPressure field: $value")
                            value
                        }
                        jsonObject.has("Pressure") -> {
                            val value = jsonObject.optString("Pressure", "0").toFloatOrNull() ?: 0f
                            Log.d(TAG, "Using Pressure field: $value")
                            value
                        }
                        else -> 0f
                    }
                    
                    val operationMode = jsonObject.optInt("operationMode", 4)
                    
                    val deviceStatus = DeviceStatus(
                        pressure = pressure,
                        operationMode = operationMode
                    )
                    Log.d(TAG, "Created DeviceStatus: $deviceStatus")
                    onDeviceStatusReceived(deviceStatus)
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing with JSONObject: ${e.message}")
            }
            
            // Fallback to Moshi parsing
            try {
                val settingsResponse = moshi.adapter(DeviceSettingsResponse::class.java).fromJson(jsonString)
                if (settingsResponse != null) {
                    onDeviceFullSettingsReceived(settingsResponse)
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing settings with Moshi: ${e.message}")
            }
            
            // If that fails, try to parse as DeviceStatus
            try {
                val deviceStatus = moshi.adapter(DeviceStatus::class.java).fromJson(jsonString)
                if (deviceStatus != null) {
                    onDeviceStatusReceived(deviceStatus)
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing device status with Moshi: ${e.message}")
            }
            
            Log.e(TAG, "Failed to parse device data: $jsonString")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error parsing device data: ${e.message}, JSON: $jsonString")
            // Log the JSON for debugging
            android.util.Log.e(TAG, "Raw JSON: $jsonString")
        }
    }
    
    // Schedule a delayed settings request in case we don't receive settings automatically
    private fun scheduleSettingsRequest() {
        Log.d(TAG, "Scheduling delayed settings request")
        
        // Use a handler to delay the request
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // Check if we've already received settings
            if (_deviceSettings.value == null && settingsRequestAttempts < MAX_SETTINGS_REQUEST_ATTEMPTS) {
                Log.d(TAG, "No settings received yet, sending request (attempt ${settingsRequestAttempts + 1})")
                requestDeviceSettings()
                settingsRequestAttempts++
                
                // Schedule another request if needed
                if (settingsRequestAttempts < MAX_SETTINGS_REQUEST_ATTEMPTS) {
                    scheduleSettingsRequest()
                }
            } else if (_deviceSettings.value != null) {
                Log.d(TAG, "Settings already received, no need to request")
            } else {
                Log.d(TAG, "Max settings request attempts reached")
            }
        }, 3000) // Wait 3 seconds before requesting settings
    }
    
    // Helper method to start service discovery
    private fun startServiceDiscovery(gatt: BluetoothGatt) {
        android.util.Log.d(TAG, "Starting service discovery")
        val discoverStarted = gatt.discoverServices()
        android.util.Log.d(TAG, "Service discovery started: $discoverStarted")
        
        if (discoverStarted == false) {
            android.util.Log.e(TAG, "Failed to start service discovery")
            disconnect()
        }
    }
    
    // Method to manually request settings from the device
    fun requestDeviceSettings() {
        Log.d(TAG, "Manually requesting device settings")
        if (_connectionState.value == ConnectionState.Connected && commandCharacteristic != null) {
            try {
                sendCommand(BleCommand.RequestSettings())
                Log.d(TAG, "Settings request sent")
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting settings: ${e.message}")
            }
        } else {
            Log.e(TAG, "Cannot request settings: not connected or command characteristic is null")
        }
    }
    
    // Call this function when target pressure is reached
    private fun onTargetPressureReached(targetPressure: Float, currentPressure: Float) {
        android.util.Log.d(TAG, "Target pressure reached: target=$targetPressure, current=$currentPressure")
        
        // Update device status with the current pressure
        val deviceStatus = DeviceStatus(
            pressure = currentPressure,
            operationMode = DeviceStatus.MODE_STOP
        )
        onDeviceStatusReceived(deviceStatus)
        
        // Play a notification sound
        playTargetReachedSound()
        
        // Show a notification
        showTargetReachedNotification(targetPressure, currentPressure)
        
        // Broadcast an event that can be observed by the UI
        try {
            android.util.Log.d(TAG, "Attempting to emit target reached event: ($targetPressure, $currentPressure)")
            val emitResult = _targetReachedEvent.tryEmit(Pair(targetPressure, currentPressure))
            android.util.Log.d(TAG, "Event emission result: $emitResult")
            
            // Use coroutine scope to ensure the event is emitted
            CoroutineScope(Dispatchers.Main).launch {
                android.util.Log.d(TAG, "Emitting target reached event via coroutine")
                _targetReachedEvent.emit(Pair(targetPressure, currentPressure))
                android.util.Log.d(TAG, "Event emitted via coroutine")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error emitting target reached event: ${e.message}", e)
        }
    }
    
    // Play a sound when target pressure is reached
    private fun playTargetReachedSound() {
        try {
            // Try to use the dedicated target reached sound first
            var targetReachedSound = MediaPlayer.create(context, R.raw.target_reached_sound)
            
            // If the target_reached_sound.wav doesn't exist yet, fall back to connection_sound.wav
            if (targetReachedSound == null) {
                Log.d(TAG, "Target reached sound file not found, using connection sound instead")
                targetReachedSound = MediaPlayer.create(context, R.raw.connection_sound)
            }
            
            // IMPORTANT: To use a custom sound:
            // 1. Download a more appropriate sound (car horn, air pressure complete, etc.)
            // 2. Rename it to target_reached_sound.wav
            // 3. Place it in the app/src/main/res/raw/ directory
            
            targetReachedSound?.setOnCompletionListener { mp -> mp.release() }
            targetReachedSound?.start()
            
            Log.d(TAG, "Playing target reached sound")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing target reached sound: ${e.message}")
            // Fallback to connection sound if there was an error
            try {
                val fallbackSound = MediaPlayer.create(context, R.raw.connection_sound)
                fallbackSound?.setOnCompletionListener { mp -> mp.release() }
                fallbackSound?.start()
            } catch (e: Exception) {
                Log.e(TAG, "Error playing fallback sound: ${e.message}")
            }
        }
    }
    
    // Show a notification when target pressure is reached
    private fun showTargetReachedNotification(targetPressure: Float, currentPressure: Float) {
        try {
            Log.d(TAG, "Showing target reached notification: target=$targetPressure, current=$currentPressure")
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            
            // Create a notification channel (required for Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "Creating notification channel for Android O+")
                val channelId = "target_pressure_channel"
                val channelName = "התראות לחץ יעד"
                val importance = android.app.NotificationManager.IMPORTANCE_HIGH
                val channel = android.app.NotificationChannel(channelId, channelName, importance)
                channel.description = "התראות כאשר לחץ היעד הושג"
                channel.enableVibration(true)
                channel.enableLights(true)
                
                // Register the channel with the system
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "Notification channel created: $channelId")
            }
            
            // Create an intent for when the notification is tapped
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pendingIntent = PendingIntent.getActivity(
                context, 
                0, 
                intent, 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            // Create the notification using NotificationCompat.Builder for better compatibility
            val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) "target_pressure_channel" else ""
            val notificationBuilder = NotificationCompat.Builder(context, channelId)
                .setContentTitle("לחץ היעד הושג")
                .setContentText("יעד: $targetPressure PSI, נוכחי: $currentPressure PSI")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setVibrate(longArrayOf(0, 500, 200, 500))
            
            // Show the notification
            Log.d(TAG, "Showing notification with ID: 1")
            notificationManager.notify(1, notificationBuilder.build())
            Log.d(TAG, "Notification shown successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification: ${e.message}", e)
            e.printStackTrace()
        }
    }
    
    fun getConnectionDuration(): Long {
        return if (_connectionState.value == ConnectionState.Connected) {
            System.currentTimeMillis() - connectionStartTime
        } else {
            0
        }
    }
    
    fun connectToDevice(device: BleDevice) {
        Log.d(TAG, "Connecting to device: ${device.name}")
        val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
        if (bluetoothDevice != null) {
            _connectionState.value = ConnectionState.Connecting
            bluetoothGatt = bluetoothDevice.connectGatt(context, true, gattCallback)
            Log.d(TAG, "ConnectGatt called with autoConnect=true for automatic reconnection")
        } else {
            Log.e(TAG, "Device not found: ${device.address}")
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    fun setTargetPressure(pressure: Float) {
        Log.d(TAG, "Setting target pressure to: $pressure")
        _targetPressure.value = pressure
        // כאן יש להוסיף את הלוגיקה לשליחת הפקודה למכשיר
    }

    fun startInflation() {
        Log.d(TAG, "Starting inflation")
        _isInflating.value = true
        // כאן יש להוסיף את הלוגיקה לשליחת פקודת התחלת ניפוח למכשיר
    }

    fun stopInflation() {
        Log.d(TAG, "Stopping inflation")
        _isInflating.value = false
        // כאן יש להוסיף את הלוגיקה לשליחת פקודת עצירת ניפוח למכשיר
    }
    
    /**
     * מתודה לביצוע קריאה חד פעמית של לחץ הצמיג.
     * שימושית לרענון מדידות באופן מידי.
     */
    fun refreshPressureReading() {
        if (_connectionState.value != ConnectionState.Connected) return
        
        // Get the latest pressure reading by requesting current status
        try {
            Log.d(TAG, "Requesting fresh pressure reading")
            sendCommand(BleCommand.RequestStatus())
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing pressure reading: ${e.message}")
        }
    }
    
    companion object {
        const val REQUEST_BLUETOOTH_PERMISSIONS = 1001
    }

    private fun onDeviceConnected(device: BluetoothDevice) {
        Log.d(TAG, "Device connected: ${device.name} (${device.address})")
        _connectionState.value = ConnectionState.Connected
        connectedDevice = device
        
        // Save the device when successfully connected
        bleRepository?.onDeviceConnected(device)
        
        // Start GATT operations
        startGattOperations()
    }

    private fun startGattOperations() {
        if (isGattOperationsStarted) return
        
        gatt?.let { gatt ->
            isGattOperationsStarted = true
            
            // Discover services
            if (!gatt.discoverServices()) {
                Log.e(TAG, "Failed to start service discovery")
                disconnect()
                return
            }
            
            Log.d(TAG, "Started service discovery")
        } ?: run {
            Log.e(TAG, "Cannot start GATT operations - no GATT connection")
            disconnect()
        }
    }
}

// Custom adapter for DeviceSettingsResponse to handle the specific JSON format
class DeviceSettingsResponseAdapter {
    @FromJson
    fun fromJson(jsonObject: Map<String, Any?>): DeviceSettingsResponse {
        // Get pressure from either "CurrentPressure" or "Pressure" field
        val pressure = when {
            jsonObject.containsKey("Pressure") -> (jsonObject["Pressure"] as? String) ?: "0"
            jsonObject.containsKey("CurrentPressure") -> (jsonObject["CurrentPressure"] as? String) ?: "0"
            else -> "0"
        }
        
        return DeviceSettingsResponse(
            operationMode = (jsonObject["operationMode"] as? Number)?.toInt() ?: 4,
            deviceName = (jsonObject["DeviceName"] as? String) ?: "",
            wifiSsid = (jsonObject["wifissid"] as? String) ?: "",
            wifiPassword = (jsonObject["wifipassword"] as? String) ?: "",
            relayCompressorPin = (jsonObject["RelayCompressorPin"] as? String) ?: "0",
            relayFreePin = (jsonObject["RelayFreePin"] as? String) ?: "0",
            sensorPin = (jsonObject["SensorPin"] as? String) ?: "0",
            autoStart = (jsonObject["AutoStart"] as? String) ?: "0",
            maxSecForStatus = (jsonObject["MaxSecForStatus"] as? String) ?: "30",
            sensorValueAt0PSI = (jsonObject["SensorValueAt0PSI"] as? String) ?: "0",
            sensorValueAt50PSI = (jsonObject["SensorValueAt50PSI"] as? String) ?: "1023",
            measureWait = (jsonObject["MessureWait"] as? String) ?: "2",
            precision = (jsonObject["Precision"] as? String) ?: "1",
            btPinCode = (jsonObject["BTPinCode"] as? String) ?: "1234",
            pressure = pressure,
            softwareVersion = (jsonObject["Software_VER"] as? String) ?: "",
            ipAddress = (jsonObject["IP"] as? String) ?: "",
            simulationMode = (jsonObject["SimulationMode"] as? String) ?: "0"
        )
    }
} 
