package com.nazulay.tiresairpressuremanagement.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import com.nazulay.tiresairpressuremanagement.R
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.ui.graphics.vector.ImageVector
import com.nazulay.tiresairpressuremanagement.domain.models.BleDevice
import com.nazulay.tiresairpressuremanagement.data.ble.BleManager
import com.nazulay.tiresairpressuremanagement.data.ble.ConnectionState

private const val TAG = "ConnectionScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionManagementScreen(
    bleManager: BleManager,
    onDeviceConnected: () -> Unit
) {
    val devices by bleManager.scanResults.collectAsState()
    val connectionState by bleManager.connectionState.collectAsState()
    
    // Log initial state
    DisposableEffect(Unit) {
        android.util.Log.d(TAG, "Initial connection state: $connectionState")
        onDispose {
            android.util.Log.d(TAG, "ConnectionManagementScreen disposed")
        }
    }
    
    // Log connection state changes and call onDeviceConnected when connected
    LaunchedEffect(connectionState) {
        android.util.Log.d(TAG, "Connection state changed to: $connectionState")
        if (connectionState == ConnectionState.Connected) {
            android.util.Log.d(TAG, "Device connected, calling onDeviceConnected")
            onDeviceConnected()
        }
    }
    
    // Force call onDeviceConnected if already connected
    LaunchedEffect(Unit) {
        if (connectionState == ConnectionState.Connected) {
            android.util.Log.d(TAG, "Already connected on initial composition, calling onDeviceConnected")
            onDeviceConnected()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = stringResource(R.string.search_devices),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Connection status card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (connectionState) {
                        ConnectionState.Connected -> Color(0xFF4CAF50).copy(alpha = 0.2f) // Green
                        ConnectionState.Connecting -> Color(0xFFFFC107).copy(alpha = 0.2f) // Amber
                        ConnectionState.Disconnected -> Color(0xFFF44336).copy(alpha = 0.2f) // Red
                        ConnectionState.PermissionDenied -> Color(0xFFF44336).copy(alpha = 0.2f) // Red
                        ConnectionState.Scanning -> Color(0xFF2196F3).copy(alpha = 0.2f) // Blue
                        ConnectionState.BluetoothDisabled -> Color(0xFFF44336).copy(alpha = 0.2f) // Red
                        ConnectionState.Error -> Color(0xFFF44336).copy(alpha = 0.2f) // Red
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = when (connectionState) {
                            ConnectionState.Connected -> Icons.Default.Bluetooth
                            ConnectionState.Connecting -> Icons.Default.Search
                            ConnectionState.Disconnected -> Icons.Default.Bluetooth
                            ConnectionState.PermissionDenied -> Icons.Default.Bluetooth
                            ConnectionState.Scanning -> Icons.Default.Search
                            ConnectionState.BluetoothDisabled -> Icons.Default.BluetoothDisabled
                            ConnectionState.Error -> Icons.Default.Bluetooth
                        },
                        contentDescription = null,
                        tint = when (connectionState) {
                            ConnectionState.Connected -> Color(0xFF4CAF50) // Green
                            ConnectionState.Connecting -> Color(0xFFFFC107) // Amber
                            ConnectionState.Disconnected -> Color(0xFFF44336) // Red
                            ConnectionState.PermissionDenied -> Color(0xFFF44336) // Red
                            ConnectionState.Scanning -> Color(0xFF2196F3) // Blue
                            ConnectionState.BluetoothDisabled -> Color(0xFFF44336) // Red
                            ConnectionState.Error -> Color(0xFFF44336) // Red
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (connectionState) {
                            ConnectionState.Connected -> stringResource(R.string.connected)
                            ConnectionState.Connecting -> stringResource(R.string.connecting)
                            ConnectionState.Disconnected -> stringResource(R.string.disconnected)
                            ConnectionState.PermissionDenied -> stringResource(R.string.permission_denied)
                            ConnectionState.Scanning -> stringResource(R.string.scanning)
                            ConnectionState.BluetoothDisabled -> stringResource(R.string.bluetooth_disabled)
                            ConnectionState.Error -> stringResource(R.string.connection_error)
                        },
                        color = when (connectionState) {
                            ConnectionState.Connected -> Color(0xFF4CAF50) // Green
                            ConnectionState.Connecting -> Color(0xFFFFC107) // Amber
                            ConnectionState.Disconnected -> Color(0xFFF44336) // Red
                            ConnectionState.PermissionDenied -> Color(0xFFF44336) // Red
                            ConnectionState.Scanning -> Color(0xFF2196F3) // Blue
                            ConnectionState.BluetoothDisabled -> Color(0xFFF44336) // Red
                            ConnectionState.Error -> Color(0xFFF44336) // Red
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Scan button
            Button(
                onClick = {
                    android.util.Log.d(TAG, "Starting scan")
                    bleManager.startScan()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3F51B5)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.scan_devices),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.scan_devices),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Devices section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E2530) // Dark blue-gray color from MainScreen
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = if (devices.isEmpty()) stringResource(R.string.no_devices_found_message) 
                               else stringResource(R.string.available_devices_count, devices.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    if (devices.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.scan_devices_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(devices) { device ->
                                DeviceItem(
                                    device = device,
                                    onClick = {
                                        android.util.Log.d(TAG, "Attempting to connect to device: ${device.address}")
                                        bleManager.connect(device.address)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceItem(
    device: BleDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bluetooth icon with signal strength indication
            Icon(
                imageVector = getBluetoothIconForSignal(device.rssi),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Display device name or a friendly alternative if not available
                if (device.name.isNotBlank() && device.name != "Unknown Device") {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    // For Chinese head units that may not show device names
                    Text(
                        text = stringResource(R.string.unknown_device_with_id, device.address.takeLast(6)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Always show the device address
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Signal strength text
                Text(
                    text = getSignalStrengthText(device.rssi),
                    style = MaterialTheme.typography.bodySmall,
                    color = getSignalStrengthColor(device.rssi, LocalContext.current)
                )
            }
            
            // Connect button/icon
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = stringResource(id = R.string.tap_to_connect),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// Add helper functions for signal strength display

// Get appropriate Bluetooth icon based on signal strength
@Composable
fun getBluetoothIconForSignal(rssi: Int): ImageVector {
    return when {
        rssi > -60 -> Icons.Default.Bluetooth
        rssi > -80 -> Icons.Default.Bluetooth
        else -> Icons.Default.BluetoothDisabled
    }
}

// Get readable signal strength text
fun getSignalStrengthText(rssi: Int): String {
    return when {
        rssi > -60 -> "Excellent signal ($rssi dBm)"
        rssi > -70 -> "Good signal ($rssi dBm)"
        rssi > -80 -> "Fair signal ($rssi dBm)"
        rssi > -90 -> "Weak signal ($rssi dBm)"
        else -> "Very weak signal ($rssi dBm)"
    }
}

// Get color based on signal strength
fun getSignalStrengthColor(rssi: Int, context: Context): Color {
    return when {
        rssi > -60 -> Color(0xFF4CAF50) // Green
        rssi > -70 -> Color(0xFF8BC34A) // Light Green
        rssi > -80 -> Color(0xFFFFC107) // Amber
        rssi > -90 -> Color(0xFFFF9800) // Orange
        else -> Color(0xFFF44336) // Red
    }
} 
