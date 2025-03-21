package com.nazulay.tiresairpressuremanagement.data.ble

/**
 * מצבי חיבור אפשריים למכשיר BLE
 */
enum class ConnectionState {
    Connected,
    Connecting,
    Disconnected,
    Scanning,
    PermissionDenied
} 
