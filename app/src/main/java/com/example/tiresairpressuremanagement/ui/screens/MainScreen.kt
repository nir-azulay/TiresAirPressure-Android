package com.example.tiresairpressuremanagement.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tiresairpressuremanagement.data.ble.BleManager
import com.example.tiresairpressuremanagement.data.ble.ConnectionState
import com.example.tiresairpressuremanagement.domain.models.BleCommand
import kotlinx.coroutines.flow.collectLatest

private const val TAG = "MainScreen"

@Composable
fun MainScreen(
    bleManager: BleManager,
    onDisconnect: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val deviceStatus by bleManager.deviceStatus.collectAsState()
    val connectionState by bleManager.connectionState.collectAsState()
    var targetPressure by remember { mutableStateOf(30f) }
    var isInflating by remember { mutableStateOf(false) }
    
    // Define min and max pressure values
    val minPressure = 5f
    val maxPressure = 40f
    
    // State for target reached dialog
    var showTargetReachedDialog by remember { mutableStateOf(false) }
    var targetReachedInfo by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    
    // Log when MainScreen is composed
    DisposableEffect(Unit) {
        android.util.Log.d(TAG, "MainScreen composed with connection state: $connectionState")
        onDispose {
            android.util.Log.d(TAG, "MainScreen disposed")
        }
    }
    
    // Log device status changes
    LaunchedEffect(deviceStatus) {
        android.util.Log.d(TAG, "Device status updated in MainScreen: $deviceStatus")
        android.util.Log.d(TAG, "Current pressure value: ${deviceStatus?.pressure ?: "null"}")
        android.util.Log.d(TAG, "Operation mode: ${deviceStatus?.operationMode ?: "null"}")
        
        // If device status shows stopped mode, update inflation state
        deviceStatus?.let { status ->
            if (status.operationMode == 1) { // MODE_STOP
                isInflating = false
            }
        }
    }
    
    // Log connection state changes
    LaunchedEffect(connectionState) {
        android.util.Log.d(TAG, "Connection state in MainScreen changed to: $connectionState")
        if (connectionState == ConnectionState.Disconnected) {
            android.util.Log.d(TAG, "Device disconnected, resetting inflation state")
            isInflating = false
        }
    }
    
    // Collect target reached events
    LaunchedEffect(Unit) {
        android.util.Log.d(TAG, "Setting up target reached event collector")
        try {
            bleManager.targetReachedEvent.collectLatest { (targetPressureValue, currentPressureValue) ->
                android.util.Log.d(TAG, "Target reached event received in MainScreen: target=$targetPressureValue, current=$currentPressureValue")
                
                // Update UI state
                isInflating = false
                android.util.Log.d(TAG, "Updated inflation state to false")
                
                // Show dialog with target reached information
                targetReachedInfo = Pair(targetPressureValue, currentPressureValue)
                showTargetReachedDialog = true
                android.util.Log.d(TAG, "Set showTargetReachedDialog to true with info: $targetReachedInfo")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error collecting target reached events: ${e.message}", e)
        }
    }
    
    // Target reached dialog
    if (showTargetReachedDialog && targetReachedInfo != null) {
        android.util.Log.d(TAG, "Showing target reached dialog with info: $targetReachedInfo")
        AlertDialog(
            onDismissRequest = { 
                android.util.Log.d(TAG, "Target reached dialog dismissed")
                showTargetReachedDialog = false 
            },
            title = { Text("לחץ היעד הושג") },
            text = { 
                Column {
                    Text("לחץ היעד הושג בהצלחה!")
                    Text("יעד: ${targetReachedInfo?.first} PSI")
                    Text("נוכחי: ${targetReachedInfo?.second} PSI")
                }
            },
            confirmButton = {
                Button(onClick = { 
                    android.util.Log.d(TAG, "Target reached dialog confirmed")
                    showTargetReachedDialog = false 
                }) {
                    Text("אישור")
                }
            }
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // שורת כפתורים עליונה
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // כפתור ניתוק
            Button(
                onClick = { 
                    android.util.Log.d(TAG, "Disconnect button clicked")
                    isInflating = false
                    bleManager.disconnect()
                    onDisconnect()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("נתק מכשיר")
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // כפתור הגדרות
            Button(
                onClick = {
                    android.util.Log.d(TAG, "Settings button clicked")
                    onSettingsClick()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("הגדרות")
            }
        }
        
        // תצוגת לחץ נוכחי
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "לחץ נוכחי",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${deviceStatus?.pressure ?: 0f} PSI",
                    style = MaterialTheme.typography.headlineLarge
                )
            }
        }
        
        // בחירת לחץ יעד
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "לחץ יעד",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(onClick = { if (targetPressure > minPressure) targetPressure -= 1f }) {
                        Text("-")
                    }
                    Text(
                        text = "$targetPressure PSI",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Button(onClick = { if (targetPressure < maxPressure) targetPressure += 1f }) {
                        Text("+")
                    }
                }
            }
        }
        
        // כפתור START/STOP
        Button(
            onClick = { 
                isInflating = !isInflating
                android.util.Log.d(TAG, "Inflation state changed to: $isInflating")
                val command = if (isInflating) {
                    android.util.Log.d(TAG, "Sending Start command with target pressure: $targetPressure")
                    BleCommand.StartTargetPressure(targetPressure = targetPressure)
                } else {
                    android.util.Log.d(TAG, "Sending Stop command")
                    BleCommand.Stop()
                }
                bleManager.sendCommand(command)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Text(if (isInflating) "עצור" else "התחל")
        }
        
        // כפתורי לחץ מוגדרים מראש
        Text(
            text = "לחצים מוגדרים מראש:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        // שורה ראשונה של כפתורים
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { targetPressure = 8f }) {
                Text("8 PSI")
            }
            Button(onClick = { targetPressure = 10f }) {
                Text("10 PSI")
            }
            Button(onClick = { targetPressure = 12f }) {
                Text("12 PSI")
            }
            Button(onClick = { targetPressure = 16f }) {
                Text("16 PSI")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // שורה שנייה של כפתורים
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { targetPressure = 20f }) {
                Text("20 PSI")
            }
            Button(onClick = { targetPressure = 30f }) {
                Text("30 PSI")
            }
            Button(onClick = { targetPressure = 38f }) {
                Text("38 PSI")
            }
        }
    }
} 