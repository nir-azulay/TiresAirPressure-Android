package com.nazulay.tiresairpressuremanagement.data.ble

/**
 * מצבי חיבור אפשריים למכשיר BLE
 */
enum class ConnectionState {
    Disconnected,
    Scanning,
    Connecting,
    Connected,
    PermissionDenied,
    BluetoothDisabled,
    Error
} 
