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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tiresairpressuremanagement.R
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
    var isSaving by remember { mutableStateOf(false) }
    
    // Log connection state
    LaunchedEffect(connectionState) {
        Log.d("SettingsScreen", "connectionState: $connectionState")
    }
    
    // Discard changes dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.exit_without_save)) },
            text = { Text(stringResource(R.string.changes_not_saved)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        onBackClick()
                    }
                ) {
                    Text(stringResource(R.string.exit_without_saving))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.continue_editing))
                }
            }
        )
    }
    
    // Saving settings dialog with progress indicator
    if (isSaving) {
        AlertDialog(
            onDismissRequest = { /* Do nothing, prevent dismissal */ },
            title = { Text(stringResource(R.string.saving_settings)) },
            text = { 
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(stringResource(R.string.device_will_reset))
                }
            },
            confirmButton = { }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = { showDiscardDialog = true }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
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
                        text = stringResource(R.string.connection_status, connectionStatus),
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
                                contentDescription = stringResource(R.string.try_reconnect),
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
                        text = stringResource(R.string.device_settings_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    OutlinedTextField(
                        value = settings.deviceName,
                        onValueChange = { viewModel.updateSettings(deviceName = it) },
                        label = { Text(stringResource(R.string.device_name), color = Color.White.copy(alpha = 0.7f)) },
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
                        text = stringResource(R.string.wifi_settings),
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
                        Text(stringResource(R.string.scan_networks))
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
                                    // Check if this is an error message
                                    val isErrorMessage = network.startsWith("Error") || 
                                                        network.startsWith("Failed") || 
                                                        network.startsWith("Location permission") ||
                                                        network.startsWith("WiFi is disabled") ||
                                                        network == "No networks found"
                                    
                                    Text(
                                        text = network,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .run {
                                                if (!isErrorMessage) {
                                                    this.clickable { 
                                                        viewModel.updateSettings(wifiSsid = network)
                                                        // סגירת הרשימה על ידי איפוס רשימת הרשתות הזמינות
                                                        viewModel.clearAvailableNetworks()
                                                    }
                                                } else {
                                                    this
                                                }
                                            }
                                            .padding(12.dp),
                                        color = if (isErrorMessage) Color(0xFFFFA500) else Color.White // Orange for error messages
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
                        label = { Text(stringResource(R.string.wifi_password), color = Color.White.copy(alpha = 0.7f)) },
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
                        text = stringResource(R.string.hardware_settings),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // Relay Compressor Pin
                    OutlinedTextField(
                        value = settings.relayCompressorPin.toString(),
                        onValueChange = { viewModel.updateSettings(relayCompressorPin = it.toIntOrNull() ?: 0) },
                        label = { Text(stringResource(R.string.relay_compressor_pin), color = Color.White.copy(alpha = 0.7f)) },
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
                        label = { Text(stringResource(R.string.relay_free_pin), color = Color.White.copy(alpha = 0.7f)) },
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
                        label = { Text(stringResource(R.string.sensor_pin), color = Color.White.copy(alpha = 0.7f)) },
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
                    
                    // Max Sec For Status
                    OutlinedTextField(
                        value = settings.maxSecForStatus.toString(),
                        onValueChange = { viewModel.updateSettings(maxSecForStatus = it.toIntOrNull() ?: 30) },
                        label = { Text(stringResource(R.string.max_sec_for_status), color = Color.White.copy(alpha = 0.7f)) },
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
                    
                    // Measure Wait
                    OutlinedTextField(
                        value = settings.measureWait.toString(),
                        onValueChange = { viewModel.updateSettings(measureWait = it.toFloatOrNull() ?: 2f) },
                        label = { Text(stringResource(R.string.measure_wait), color = Color.White.copy(alpha = 0.7f)) },
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
                    
                    // Auto Start
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.auto_start),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        
                        Switch(
                            checked = settings.autoStart,
                            onCheckedChange = { viewModel.updateSettings(autoStart = it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF3F51B5),
                                checkedTrackColor = Color(0xFF3F51B5).copy(alpha = 0.5f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.Gray.copy(alpha = 0.5f)
                            )
                        )
                    }
                    
                    // Bluetooth PIN Code
                    OutlinedTextField(
                        value = settings.btPinCode,
                        onValueChange = { viewModel.updateSettings(btPinCode = it) },
                        label = { Text(stringResource(R.string.bt_pin_code), color = Color.White.copy(alpha = 0.7f)) },
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
                        text = stringResource(R.string.calibration_settings),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // Sensor Value At 0 PSI
                    OutlinedTextField(
                        value = settings.sensorValueAt0PSI.toString(),
                        onValueChange = { viewModel.updateSettings(sensorValueAt0PSI = it.toIntOrNull() ?: 0) },
                        label = { Text(stringResource(R.string.sensor_value_at_0_psi), color = Color.White.copy(alpha = 0.7f)) },
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
                        label = { Text(stringResource(R.string.sensor_value_at_50_psi), color = Color.White.copy(alpha = 0.7f)) },
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
                        label = { Text(stringResource(R.string.precision_psi), color = Color.White.copy(alpha = 0.7f)) },
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
            
            // Simulation Mode Card
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
                        text = stringResource(R.string.simulation_mode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.enable_simulation_mode),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        
                        Switch(
                            checked = settings.simulationMode,
                            onCheckedChange = { viewModel.setSimulationMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF3F51B5),
                                checkedTrackColor = Color(0xFF3F51B5).copy(alpha = 0.5f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.Gray.copy(alpha = 0.5f)
                            )
                        )
                    }
                    
                    Text(
                        text = stringResource(R.string.simulation_mode_description),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            
            // Theme Mode Card
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
                        text = "מצב תצוגה",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    val themeMode by viewModel.themeMode.collectAsState()
                    
                    // System Default Option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setThemeMode(0) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ברירת מחדל של המערכת",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        
                        RadioButton(
                            selected = themeMode == 0,
                            onClick = { viewModel.setThemeMode(0) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF3F51B5),
                                unselectedColor = Color.Gray
                            )
                        )
                    }
                    
                    // Light Mode Option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setThemeMode(1) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "מצב יום",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        
                        RadioButton(
                            selected = themeMode == 1,
                            onClick = { viewModel.setThemeMode(1) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF3F51B5),
                                unselectedColor = Color.Gray
                            )
                        )
                    }
                    
                    // Dark Mode Option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setThemeMode(2) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "מצב לילה",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        
                        RadioButton(
                            selected = themeMode == 2,
                            onClick = { viewModel.setThemeMode(2) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF3F51B5),
                                unselectedColor = Color.Gray
                            )
                        )
                    }
                    
                    Text(
                        text = "שינוי מצב התצוגה יחייב הפעלה מחדש של האפליקציה",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            
            // Save button
            Button(
                onClick = {
                    isSaving = true
                    viewModel.saveSettings { 
                        isSaving = false
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
                    stringResource(R.string.save_settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
} 