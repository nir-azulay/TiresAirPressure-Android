package com.example.tiresairpressuremanagement.ui.screens.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tiresairpressuremanagement.R
import com.example.tiresairpressuremanagement.domain.models.BleDevice
import com.example.tiresairpressuremanagement.utils.BleConstants
import android.app.Application
import androidx.compose.ui.platform.LocalContext
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(
        factory = MainViewModel.provideFactory(LocalContext.current.applicationContext as Application)
    ),
    onNavigateToSettings: () -> Unit
) {
    val currentPressure by viewModel.currentPressure.collectAsState()
    val targetPressure by viewModel.targetPressure.collectAsState()
    val isInflating by viewModel.isInflating.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val scanResults by viewModel.scanResults.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    var showPressureReachedDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Add Settings button at the top
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "הגדרות",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        if (!isConnected) {
            Text(
                text = "לא מחובר למכשיר",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
            Button(onClick = {
                Log.d("MainScreen", "Starting device scan")
                viewModel.startScan()
            }) {
                Text("סרוק מכשירים")
            }
            if (scanResults.isNotEmpty()) {
                Log.d("MainScreen", "Scan results: ${scanResults.size} devices found")
                LazyColumn {
                    items(scanResults) { device ->
                        OutlinedButton(
                            onClick = {
                                Log.d("MainScreen", "Connecting to device: ${device.name ?: device.address}")
                                viewModel.connectToDevice(device)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp)
                        ) {
                            Text(device.name ?: device.address)
                        }
                    }
                }
            } else {
                Log.d("MainScreen", "No devices found during scan")
            }
        } else {
            // Pressure Display
            Text(
                text = String.format("%.1f PSI", currentPressure),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Target Pressure Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.decreaseTargetPressure() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Text("-", fontSize = 24.sp)
                }
                
                Text(
                    text = String.format("%.1f PSI", targetPressure),
                    fontSize = 24.sp
                )
                
                IconButton(
                    onClick = { viewModel.increaseTargetPressure() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Text("+", fontSize = 24.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Predefined Pressure Buttons
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(listOf(8, 10, 12, 16, 20, 30, 38)) { pressure ->
                    OutlinedButton(
                        onClick = { viewModel.setTargetPressure(pressure.toFloat()) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text("$pressure PSI")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Start/Stop Button
            Button(
                onClick = { viewModel.toggleInflation() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isInflating) Color(0xFFE53935) else Color(0xFF4CAF50)
                )
            ) {
                Text(
                    text = if (isInflating) "עצור" else "התחל",
                    fontSize = 20.sp
                )
            }
        }
    }
    
    // Pressure Reached Dialog
    if (showPressureReachedDialog) {
        AlertDialog(
            onDismissRequest = { showPressureReachedDialog = false },
            title = { Text("הודעה") },
            text = { Text("הגעת ללחץ היעד!") },
            confirmButton = {
                TextButton(onClick = { showPressureReachedDialog = false }) {
                    Text("אישור")
                }
            }
        )
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
    if (scanResults.isEmpty()) {
        Text(
            text = "מחפש מכשירים...",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp)
        )
    } else {
        Text(
            text = "מכשירים זמינים:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(scanResults) { device ->
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    onClick = { onDeviceClick(device) }
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = device.address,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
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