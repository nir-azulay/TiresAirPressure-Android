package com.example.tiresairpressuremanagement.ui.screens.settings

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tiresairpressuremanagement.domain.models.PressureUnit
import com.example.tiresairpressuremanagement.domain.models.TemperatureUnit
import com.example.tiresairpressuremanagement.data.ble.ConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val availableNetworks by viewModel.availableNetworks.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    var showDiscardDialog by remember { mutableStateOf(false) }
    var expandedWifiDropdown by remember { mutableStateOf(false) }
    
    // Log connection state
    LaunchedEffect(connectionState) {
        Log.d("SettingsScreen", "connectionState: $connectionState")
    }
    
    // Discard changes dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("לצאת ללא שמירה?") },
            text = { Text("השינויים שביצעת לא יישמרו.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        onBackClick()
                    }
                ) {
                    Text("צא ללא שמירה")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDiscardDialog = false }) {
                    Text("המשך עריכה")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("הגדרות") },
                navigationIcon = {
                    IconButton(onClick = { showDiscardDialog = true }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "חזור")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
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
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "סטטוס חיבור: $connectionStatus",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = when (connectionState) {
                            ConnectionState.Connected -> Color(0xFF4CAF50) // Green
                            ConnectionState.Connecting -> Color(0xFFFFC107) // Amber
                            ConnectionState.Disconnected -> Color(0xFFF44336) // Red
                        }
                    )
                    
                    // Refresh connection button
                    if (connectionState != ConnectionState.Connected) {
                        IconButton(onClick = { viewModel.connectToSavedDeviceIfNeeded() }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "נסה להתחבר מחדש",
                                tint = when (connectionState) {
                                    ConnectionState.Connected -> Color(0xFF4CAF50) // Green
                                    ConnectionState.Connecting -> Color(0xFFFFC107) // Amber
                                    ConnectionState.Disconnected -> Color(0xFFF44336) // Red
                                }
                            )
                        }
                    }
                }
            }
            
            // Device name
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E2530) // Dark blue-gray color from MainScreen
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "הגדרות מכשיר",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    OutlinedTextField(
                        value = settings.deviceName,
                        onValueChange = { viewModel.updateSettings(deviceName = it) },
                        label = { Text("שם המכשיר", color = Color.White.copy(alpha = 0.7f)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFBBDEFB),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            }
            
            // WiFi settings
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E2530) // Dark blue-gray color from MainScreen
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "הגדרות WiFi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // SSID
                    OutlinedTextField(
                        value = settings.wifiSsid,
                        onValueChange = { viewModel.updateSettings(wifiSsid = it) },
                        label = { Text("SSID", color = Color.White.copy(alpha = 0.7f)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFBBDEFB),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    
                    // Scan networks button
                    Button(
                        onClick = { viewModel.scanWifiNetworks() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3F51B5)
                        )
                    ) {
                        Text("סרוק רשתות זמינות")
                    }
                    
                    // Available networks list
                    if (availableNetworks.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF2C3440) // Slightly lighter than the main card
                            )
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            ) {
                                items(availableNetworks) { network ->
                                    Text(
                                        text = network,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.updateSettings(wifiSsid = network) }
                                            .padding(12.dp),
                                        color = Color.White
                                    )
                                    Divider(color = Color.White.copy(alpha = 0.1f))
                                }
                            }
                        }
                    }
                    
                    // Password
                    OutlinedTextField(
                        value = settings.wifiPassword,
                        onValueChange = { viewModel.updateSettings(wifiPassword = it) },
                        label = { Text("סיסמה", color = Color.White.copy(alpha = 0.7f)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFBBDEFB),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            }
            
            // Hardware settings
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E2530) // Dark blue-gray color from MainScreen
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "הגדרות חומרה",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // Relay Compressor Pin
                    OutlinedTextField(
                        value = settings.relayCompressorPin.toString(),
                        onValueChange = { viewModel.updateSettings(relayCompressorPin = it.toIntOrNull() ?: 0) },
                        label = { Text("Relay Compressor Pin", color = Color.White.copy(alpha = 0.7f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFBBDEFB),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    
                    // Relay Free Pin
                    OutlinedTextField(
                        value = settings.relayFreePin.toString(),
                        onValueChange = { viewModel.updateSettings(relayFreePin = it.toIntOrNull() ?: 0) },
                        label = { Text("Relay Free Pin", color = Color.White.copy(alpha = 0.7f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFBBDEFB),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    
                    // Sensor Pin
                    OutlinedTextField(
                        value = settings.sensorPin.toString(),
                        onValueChange = { viewModel.updateSettings(sensorPin = it.toIntOrNull() ?: 0) },
                        label = { Text("Sensor Pin", color = Color.White.copy(alpha = 0.7f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFBBDEFB),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            }
            
            // Calibration settings
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E2530) // Dark blue-gray color from MainScreen
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "הגדרות כיול",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // Sensor Value At 0 PSI
                    OutlinedTextField(
                        value = settings.sensorValueAt0PSI.toString(),
                        onValueChange = { viewModel.updateSettings(sensorValueAt0PSI = it.toIntOrNull() ?: 0) },
                        label = { Text("ערך חיישן ב-0 PSI", color = Color.White.copy(alpha = 0.7f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFBBDEFB),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    
                    // Sensor Value At 50 PSI
                    OutlinedTextField(
                        value = settings.sensorValueAt50PSI.toString(),
                        onValueChange = { viewModel.updateSettings(sensorValueAt50PSI = it.toIntOrNull() ?: 1023) },
                        label = { Text("ערך חיישן ב-50 PSI", color = Color.White.copy(alpha = 0.7f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFBBDEFB),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    
                    // Precision
                    OutlinedTextField(
                        value = settings.precision.toString(),
                        onValueChange = { viewModel.updateSettings(precision = it.toFloatOrNull() ?: 1f) },
                        label = { Text("דיוק (PSI)", color = Color.White.copy(alpha = 0.7f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFBBDEFB),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            }
            
            // Save button
            Button(
                onClick = {
                    viewModel.saveSettings { 
                        onBackClick()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3F51B5)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    "שמור הגדרות",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
} 