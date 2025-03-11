package com.example.tiresairpressuremanagement.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tiresairpressuremanagement.domain.models.BleDevice
import com.example.tiresairpressuremanagement.utils.BleConstants

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val isConnected by viewModel.isConnected.collectAsState()
    val deviceStatus by viewModel.deviceStatus.collectAsState()
    val scanResults by viewModel.scanResults.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val targetPressure by viewModel.targetPressure.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Connection Status
        ConnectionStatus(
            isConnected = isConnected,
            onScanClick = { if (isScanning) viewModel.stopScan() else viewModel.startScan() },
            isScanning = isScanning
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Scan Results
        if (isScanning && !isConnected) {
            ScanResultsList(
                scanResults = scanResults,
                onDeviceClick = { device ->
                    viewModel.stopScan()
                    viewModel.connect(device)
                }
            )
        }
        
        if (isConnected) {
            // Current Pressure Display
            CurrentPressureDisplay(
                currentPressure = deviceStatus?.pressure ?: 0f,
                operationMode = deviceStatus?.operationMode ?: BleConstants.MODE_STOP
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Target Pressure Control
            TargetPressureControl(
                targetPressure = targetPressure,
                onPressureChange = viewModel::setTargetPressure,
                predefinedPressures = viewModel.predefinedPressures
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Start/Stop Button
            StartStopButton(
                isOperating = deviceStatus?.operationMode != BleConstants.MODE_STOP,
                onStartClick = viewModel::startOperation,
                onStopClick = viewModel::stopOperation
            )
        }
    }
}

@Composable
private fun ConnectionStatus(
    isConnected: Boolean,
    onScanClick: () -> Unit,
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isConnected) "מחובר" else "מנותק",
            style = MaterialTheme.typography.titleMedium,
            color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        
        Button(
            onClick = onScanClick,
            enabled = !isConnected
        ) {
            Text(if (isScanning) "עצור סריקה" else "סרוק מכשירים")
        }
    }
}

@Composable
private fun ScanResultsList(
    scanResults: List<BleDevice>,
    onDeviceClick: (BleDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "מכשירים זמינים:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        scanResults.forEach { device ->
            OutlinedButton(
                onClick = { onDeviceClick(device) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(device.name ?: "מכשיר לא מזוהה (${device.address})")
            }
        }
    }
}

@Composable
private fun CurrentPressureDisplay(
    currentPressure: Float,
    operationMode: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "%.1f PSI".format(currentPressure),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = when (operationMode) {
                BleConstants.MODE_INFLATE -> "מנפח..."
                BleConstants.MODE_DEFLATE -> "מרוקן..."
                else -> ""
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun TargetPressureControl(
    targetPressure: Float,
    onPressureChange: (Float) -> Unit,
    predefinedPressures: List<Int>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "לחץ יעד:",
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Pressure adjustment buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(onClick = { onPressureChange(targetPressure - 0.5f) }) {
                Icon(Icons.Default.Remove, contentDescription = "הפחת לחץ")
            }
            
            Text(
                text = "%.1f PSI".format(targetPressure),
                style = MaterialTheme.typography.headlineMedium
            )
            
            IconButton(onClick = { onPressureChange(targetPressure + 0.5f) }) {
                Icon(Icons.Default.Add, contentDescription = "הגדל לחץ")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Predefined pressure values
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(predefinedPressures) { pressure ->
                OutlinedButton(
                    onClick = { onPressureChange(pressure.toFloat()) }
                ) {
                    Text("$pressure PSI")
                }
            }
        }
    }
}

@Composable
private fun StartStopButton(
    isOperating: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = if (isOperating) onStopClick else onStartClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isOperating) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    ) {
        Text(
            text = if (isOperating) "עצור" else "התחל",
            style = MaterialTheme.typography.titleLarge
        )
    }
} 