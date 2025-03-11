package com.example.tiresairpressuremanagement.data.ble

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import com.example.tiresairpressuremanagement.domain.models.BleCommand
import com.example.tiresairpressuremanagement.domain.models.BleDevice
import com.example.tiresairpressuremanagement.domain.models.DeviceSettings
import com.example.tiresairpressuremanagement.domain.models.DeviceStatus
import com.example.tiresairpressuremanagement.utils.BleConstants
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

class BleManager(private val context: Context) {
    private var bluetoothGatt: BluetoothGatt? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState
    
    private val _deviceStatus = MutableStateFlow<DeviceStatus?>(null)
    val deviceStatus: StateFlow<DeviceStatus?> = _deviceStatus
    
    private val _scanResults = MutableStateFlow<List<BleDevice>>(emptyList())
    val scanResults: StateFlow<List<BleDevice>> = _scanResults
    
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    
    private val bleScanner by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val currentList = _scanResults.value.toMutableList()
            val bleDevice = BleDevice(
                name = device.name,
                address = device.address,
                rssi = result.rssi
            )
            if (!currentList.any { it.address == bleDevice.address }) {
                currentList.add(bleDevice)
                _scanResults.value = currentList
            }
        }
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = true
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = false
                    bluetoothGatt = null
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(BleConstants.SERVICE_UUID)
                notifyCharacteristic = service?.getCharacteristic(BleConstants.NOTIFICATION_CHARACTERISTIC_UUID)
                commandCharacteristic = service?.getCharacteristic(BleConstants.COMMAND_CHARACTERISTIC_UUID)
                
                // Enable notifications
                enableNotifications()
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
                    parseNotification(jsonString)
                }
            }
        }
    }
    
    fun startScan() {
        if (!hasRequiredPermissions()) return
        
        val scanFilter = ScanFilter.Builder()
            .build()
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        bleScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
    }
    
    fun stopScan() {
        if (!hasRequiredPermissions()) return
        bleScanner?.stopScan(scanCallback)
    }
    
    fun connect(address: String) {
        if (!hasRequiredPermissions()) return
        
        bluetoothAdapter?.getRemoteDevice(address)?.let { device ->
            device.connectGatt(context, false, gattCallback)
        }
    }
    
    fun disconnect() {
        bluetoothGatt?.disconnect()
    }
    
    fun sendCommand(command: BleCommand) {
        val jsonString = when (command) {
            is BleCommand.Stop -> moshi.adapter(BleCommand.Stop::class.java).toJson(command)
            is BleCommand.Start -> moshi.adapter(BleCommand.Start::class.java).toJson(command)
            is BleCommand.UpdateSettings -> moshi.adapter(BleCommand.UpdateSettings::class.java).toJson(command)
        }
        
        val bytes = jsonString.toByteArray()
        
        commandCharacteristic?.let { characteristic ->
            characteristic.value = bytes
            bluetoothGatt?.writeCharacteristic(characteristic)
        }
    }
    
    private fun enableNotifications() {
        notifyCharacteristic?.let { characteristic ->
            bluetoothGatt?.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            bluetoothGatt?.writeDescriptor(descriptor)
        }
    }
    
    private fun parseNotification(jsonString: String) {
        try {
            if (jsonString.contains("\"operationMode\":4")) {
                // Settings update
                val adapter = moshi.adapter(DeviceSettings::class.java)
                val settings = adapter.fromJson(jsonString)
                // Handle settings update if needed
            } else {
                // Status update
                val adapter = moshi.adapter(DeviceStatus::class.java)
                val status = adapter.fromJson(jsonString)
                status?.let {
                    _deviceStatus.value = it
                }
            }
        } catch (e: Exception) {
            // Handle parsing error
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
} 