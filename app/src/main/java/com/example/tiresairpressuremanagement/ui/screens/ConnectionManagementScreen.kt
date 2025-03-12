package com.example.tiresairpressuremanagement.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tiresairpressuremanagement.data.ble.BleManager
import com.example.tiresairpressuremanagement.data.ble.ConnectionState
import com.example.tiresairpressuremanagement.domain.models.BleDevice

private const val TAG = "ConnectionScreen"

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "מצב חיבור: ${if (connectionState == ConnectionState.Connected) "מחובר" else "מנותק"}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Button(
            onClick = {
                android.util.Log.d(TAG, "Starting scan")
                bleManager.startScan()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("סרוק מכשירים")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (devices.isEmpty()) {
            Text(
                text = "לא נמצאו מכשירים. לחץ על 'סרוק מכשירים' כדי לחפש.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text(
                text = "מכשירים זמינים: ${devices.size}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun DeviceItem(
    device: BleDevice,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = device.name ?: "מכשיר לא ידוע",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
} 