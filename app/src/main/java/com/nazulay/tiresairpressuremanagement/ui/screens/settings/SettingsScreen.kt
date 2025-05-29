package com.nazulay.tiresairpressuremanagement.ui.screens.settings

import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.SignalWifiStatusbarConnectedNoInternet4
import androidx.compose.material.icons.filled.SignalWifiStatusbar4Bar
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nazulay.tiresairpressuremanagement.R
import com.nazulay.tiresairpressuremanagement.data.ble.ConnectionState
import com.nazulay.tiresairpressuremanagement.domain.models.DeviceSettings
import com.nazulay.tiresairpressuremanagement.domain.models.AppSettings
import com.nazulay.tiresairpressuremanagement.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.material3.LocalContentColor
import com.nazulay.tiresairpressuremanagement.utils.LocaleHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    forceReconnect: () -> Unit = {}
) {
    var isSaving by remember { mutableStateOf(false) }
    val deviceSettings by viewModel.deviceSettings.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()
    
    // Get connection state cleanly from the ViewModel
    val connectionState by viewModel.connectionState.collectAsState()
    
    // Launched effect to handle navigation back after saving
    LaunchedEffect(isSaving) {
        if (isSaving) {
            // Wait 5 seconds after saving settings
            delay(5000)
            Log.d("SettingsScreen", "Settings saved, returning to main screen for reconnection")
            // Return to main screen to let regular app flow handle reconnection
            onBackClick() 
        }
    }
    
    // Add a LaunchedEffect to request device settings when connected but no settings loaded
    LaunchedEffect(connectionState, deviceSettings) {
        if (connectionState == ConnectionState.Connected && deviceSettings == null) {
            Log.d("SettingsScreen", "Connected but no settings loaded, requesting settings from BLE device")
            viewModel.forceRefreshConnectionState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = stringResource(R.string.settings),
                        fontSize = 16.sp, // Smaller font size for the app title
                        fontWeight = FontWeight.Medium // Medium weight for better readability at smaller size
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            // Always show save and cancel buttons at the bottom
            BottomAppBar(
                containerColor = Color(0xFF1E2530),
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Show message if there are unsaved changes
                    if (hasUnsavedChanges) {
                        Text(
                            text = stringResource(R.string.unsaved_changes_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Yellow,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                isSaving = true
                                viewModel.saveDeviceSettingsAndReconnect(forceReconnect)
                            },
                            modifier = Modifier
                                .weight(1f),
                            enabled = connectionState == ConnectionState.Connected
                        ) {
                            Text(
                                text = stringResource(R.string.save_settings),
                                color = Color.White // Make button text white for better visibility
                            )
                        }
                        
                        OutlinedButton(
                            onClick = {
                                if (hasUnsavedChanges) {
                                    viewModel.discardChanges()
                                }
                                onBackClick() // Return to main screen, always
                            },
                            modifier = Modifier
                                .weight(1f),
                            enabled = true // Always enabled
                        ) {
                            Text(
                                text = if (hasUnsavedChanges) {
                                    stringResource(R.string.discard)
                                } else {
                                    stringResource(R.string.close)
                                },
                                color = Color.White // Make button text white for better visibility
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212)) // Match MainScreen dark background
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp), // Further reduced padding for even tighter fit
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // App Settings Section
                item {
                    Text(
                        text = stringResource(R.string.app_settings),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White, // Ensure consistent text color
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }

                // Predefined Pressures Card
                item {
                    SettingsCard {
                        PredefinedPressuresSettings(
                            pressures = appSettings.predefinedPressures,
                            onPressuresUpdated = { viewModel.updatePredefinedPressures(it) }
                        )
                    }
                }

                // Language Settings Card
                item {
                    SettingsCard {
                        LanguageSettings(
                            currentLanguage = viewModel.currentLanguage.value,
                            onLanguageSelected = { viewModel.updateLanguage(it) }
                        )
                    }
                }

                // Device Settings Section Header
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.device_settings),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White, // Ensure consistent text color
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }

                // Only show device settings if connected (safe comparison)
                if (connectionState == ConnectionState.Connected) {
                    val currentSettings = deviceSettings // Capture the value locally
                    if (currentSettings != null) {
                        // Add Simulation Mode Card to Device Settings
                        item {
                            SettingsCard {
                                SimulationModeSettings(
                                    isEnabled = currentSettings.simulationMode, // Use device setting
                                    onToggle = { viewModel.updateSimulationMode(it) }
                                )
                            }
                        }
                        // Add Debug Mode Toggle
                        item {
                            SettingsCard {
                                val debugMode by viewModel.debugMode.collectAsState()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.debug_mode),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = debugMode,
                                        onCheckedChange = { viewModel.setDebugMode(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                                            uncheckedThumbColor = Color.Gray
                                        )
                                    )
                                }
                            }
                        }
                        
                        // Device Name Card
                        item {
                            SettingsCard {
                                // Define a common text field configuration
                                val textFieldColors = OutlinedTextFieldDefaults.colors(
                                    unfocusedTextColor = Color.White,
                                    focusedTextColor = Color.White,
                                    unfocusedBorderColor = Color.Gray,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    cursorColor = Color.White
                                )
                                
                                OutlinedTextField(
                                    value = currentSettings.deviceName, // Use local variable
                                    onValueChange = { viewModel.updateDeviceSettings(deviceName = it) },
                                    label = { Text(stringResource(R.string.device_name), color = Color.White.copy(alpha = 0.8f)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = textFieldColors
                                )
                            }
                        }

                        // WiFi Settings Card
                        item {
                            val networks by viewModel.availableNetworks.collectAsState()
                            SettingsCard {
                                WifiSettings(
                                    wifiSsid = currentSettings.wifiSsid, 
                                    wifiPassword = currentSettings.wifiPassword,
                                    availableNetworks = networks, // Pass the collected networks
                                    onSsidChanged = { ssid -> viewModel.updateDeviceSettings(wifiSsid = ssid) },
                                    onPasswordChanged = { password -> viewModel.updateDeviceSettings(wifiPassword = password) },
                                    onScanClicked = { viewModel.scanWifiNetworks() }, // Call scan function
                                    onNetworkSelected = { selectedSsid -> 
                                        // Update the SSID field when a network is selected from the list
                                        viewModel.updateDeviceSettings(wifiSsid = selectedSsid)
                                    } 
                                )
                            }
                        }

                        // Hardware Settings Card
                        item {
                            SettingsCard {
                                HardwareSettings(
                                    settings = currentSettings, // Use local variable
                                    onSettingsChanged = { updatedSettings -> 
                                        viewModel.updateDeviceSettings(
                                            relayCompressorPin = updatedSettings.relayCompressorPin,
                                            relayFreePin = updatedSettings.relayFreePin,
                                            sensorPin = updatedSettings.sensorPin,
                                            maxSecForStatus = updatedSettings.maxSecForStatus,
                                            sensorValueAt0PSI = updatedSettings.sensorValueAt0PSI,
                                            sensorValueAt50PSI = updatedSettings.sensorValueAt50PSI,
                                            measureWait = updatedSettings.measureWait,
                                            precision = updatedSettings.precision,
                                            btPinCode = updatedSettings.btPinCode,
                                            pressureCalcMethod = updatedSettings.pressureCalcMethod
                                        )
                                    }
                                )
                            }
                        }
                        
                        // Remove save/cancel buttons from here as they're now in the bottom bar
                    } else {
                        item {
                            SettingsCard {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                    Text(
                                        text = stringResource(R.string.loading_preset_pressures),
                                        modifier = Modifier.padding(top = 60.dp)
                                    )
                                    
                                    // Add a button to manually request settings
                                    Button(
                                        onClick = { viewModel.requestDeviceSettings() },
                                        modifier = Modifier.padding(top = 100.dp)
                                    ) {
                                        Text(stringResource(R.string.request_settings))
                                    }
                                    
                                    // Auto-request settings every 3 seconds while in loading state
                                    LaunchedEffect(Unit) {
                                        while (true) {
                                            viewModel.requestDeviceSettings()
                                            delay(3000) // Wait 3 seconds between requests
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Not connected message
                    item {
                        SettingsCard {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.not_connected),
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                Text(
                                    text = stringResource(R.string.current_connection_state, connectionState),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, // Use theme color
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                
                                Button(
                                    onClick = { 
                                        // Use the ViewModel's method to attempt connection
                                        viewModel.connectToSavedDeviceIfNeeded() 
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.try_reconnect),
                                        color = Color.White // Make button text white for better visibility
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Saving Dialog
    if (isSaving) {
        // Track the saving/reconnection state
        var reconnectingState by remember { mutableStateOf(0) } // 0: saving, 1: reconnecting
        
        // Update state based on time
        LaunchedEffect(isSaving) {
            // Start with saving state
            reconnectingState = 0
            
            // After 2.5 seconds, transition to reconnecting state
            delay(2500)
            if (isSaving) {
                reconnectingState = 1
                Log.d("SettingsScreen", "Transitioning to reconnection state in dialog")
            }
        }
        
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.saving_settings)) },
            text = { 
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (reconnectingState == 0) {
                            stringResource(R.string.device_will_reset)
                        } else {
                            stringResource(R.string.reconnecting_automatically)
                        },
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator()
                    
                    // Show countdown for reconnection state
                    if (reconnectingState == 1) {
                        val remainingTime = remember { mutableStateOf(3) }
                        
                        LaunchedEffect(reconnectingState) {
                            for (i in 3 downTo 1) {
                                remainingTime.value = i
                                delay(1000)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.returning_to_main_in_seconds, remainingTime.value),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            },
            confirmButton = { }
        )
    }
}

@Composable
private fun SettingsCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E2530) // Match MainScreen card color
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun CustomRadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = text)
        RadioButton(
            selected = selected,
            onClick = onClick
        )
    }
}

@Composable
private fun SimulationModeSettings(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f) // Add weight to limit width and prevent pushing Switch off screen
        ) {
            Text(
                text = stringResource(R.string.simulation_mode),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), // Smaller title
                color = Color.White // Match MainScreen text color
            )
            Text(
                text = stringResource(R.string.simulation_mode_description),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f), // Match MainScreen text color with reduced opacity
                modifier = Modifier.padding(end = 8.dp) // Add padding to ensure space between text and switch
            )
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.DarkGray
            )
        )
    }
}

@Composable
private fun PredefinedPressuresSettings(
    pressures: List<Float>,
    onPressuresUpdated: (List<Float>) -> Unit
) {
    val viewModel: SettingsViewModel = viewModel()
    val appSettings by viewModel.appSettings.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Header with controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.predefined_pressures),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            
            // Add pressure button
            if (pressures.size < appSettings.maxPressureCount) {
                IconButton(
                    onClick = { viewModel.addPressure() }
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_pressure),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        Text(
            text = stringResource(R.string.predefined_pressures_description),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(vertical = 4.dp)
        )
        
        // Min and Max pressure count controls
        PressureCountControls(
            minCount = 4, // Fixed minimum count
            maxCount = appSettings.maxPressureCount,
            onCountUpdated = { min, max -> viewModel.updatePressureCount(min, max) }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Display pressures in a column for better layout
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pressures.forEachIndexed { index, pressure ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        PressureSpinner(
                            label = stringResource(R.string.predefined_pressure_format, (index + 1).toString()),
                            value = pressure,
                            onValueChange = { newValue ->
                                val newPressures = pressures.toMutableList()
                                newPressures[index] = newValue
                                onPressuresUpdated(newPressures)
                            }
                        )
                    }
                    
                    // Only show remove button if above minimum count
                    if (pressures.size > appSettings.minPressureCount) {
                        IconButton(
                            onClick = { viewModel.removePressure(index) }
                        ) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = stringResource(R.string.remove_pressure),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PressureCountControls(
    minCount: Int,
    maxCount: Int,
    onCountUpdated: (min: Int, max: Int) -> Unit
) {
    var maxCountState by remember { mutableStateOf(maxCount) }
    
    // Fixed minimum count
    val fixedMinCount = 4
    
    // Maximum allowed number of presets
    val maxAllowed = 16
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.pressure_count_settings),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Max Count Spinner
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.max_pressure_count, maxCountState),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { 
                        if (maxCountState > fixedMinCount + 2) {
                            maxCountState -= 2  // Decrease by 2 to keep it even
                            onCountUpdated(fixedMinCount, maxCountState)
                        } else if (maxCountState > fixedMinCount) {
                            // If we can't decrease by 2, just go to min
                            maxCountState = fixedMinCount
                            onCountUpdated(fixedMinCount, maxCountState)
                        }
                    },
                    enabled = maxCountState > fixedMinCount
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = null,
                        tint = if (maxCountState > fixedMinCount) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
                
                Text(
                    text = maxCountState.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                
                IconButton(
                    onClick = { 
                        if (maxCountState < maxAllowed - 2) {
                            maxCountState += 2  // Increase by 2 to keep it even
                            onCountUpdated(fixedMinCount, maxCountState)
                        } else if (maxCountState < maxAllowed) {
                            // If we can't increase by 2, just go to max
                            maxCountState = maxAllowed
                            onCountUpdated(fixedMinCount, maxCountState)
                        }
                    },
                    enabled = maxCountState < maxAllowed
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = if (maxCountState < maxAllowed) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }
        }
    }
}

// New Composable for the Pressure Spinner (+/- buttons)
@Composable
private fun PressureSpinner(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    step: Float = 1.0f, // Increment/decrement step
    minValue: Float = 0f,
    maxValue: Float = 150f
) {
    Column {
        Text(
            text = label, 
            style = MaterialTheme.typography.labelMedium,
            color = Color.White, // Match MainScreen text color
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { onValueChange((value - step).coerceIn(minValue, maxValue)) },
                enabled = value > minValue,
                modifier = Modifier.size(40.dp), // Smaller buttons
                contentPadding = PaddingValues(0.dp) // Remove padding for icon only
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease")
            }
            
            Text(
                text = "%.1f".format(value), // Format to one decimal place
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White, // Match MainScreen text color
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Button(
                onClick = { onValueChange((value + step).coerceIn(minValue, maxValue)) },
                enabled = value < maxValue,
                modifier = Modifier.size(40.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase")
            }
        }
    }
}

@Composable
private fun HardwareSettings(
    settings: DeviceSettings,
    onSettingsChanged: (DeviceSettings) -> Unit
) {
    var relayCompressorPin by remember { mutableStateOf(settings.relayCompressorPin.toString()) }
    var relayFreePin by remember { mutableStateOf(settings.relayFreePin.toString()) }
    var sensorPin by remember { mutableStateOf(settings.sensorPin.toString()) }
    var maxSecForStatus by remember { mutableStateOf(settings.maxSecForStatus.toString()) }
    var sensorValueAt0PSI by remember { mutableStateOf(settings.sensorValueAt0PSI.toString()) }
    var sensorValueAt50PSI by remember { mutableStateOf(settings.sensorValueAt50PSI.toString()) }
    var measureWait by remember { mutableStateOf(settings.measureWait.toString()) }
    var precision by remember { mutableStateOf(settings.precision.toString()) }
    var btPinCode by remember { mutableStateOf(settings.btPinCode) }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        // Title section (no expand/collapse)
        Text(
            text = stringResource(R.string.hardware_settings),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        )
        // Divider for visual separation
        Divider(
            color = Color.Gray.copy(alpha = 0.3f),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        // Always show expanded content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            // Define a common text field configuration
            val textFieldColors = OutlinedTextFieldDefaults.colors(
                unfocusedTextColor = Color.White,
                focusedTextColor = Color.White,
                unfocusedBorderColor = Color.Gray,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                cursorColor = Color.White
            )
            
            OutlinedTextField(
                value = relayCompressorPin,
                onValueChange = { value -> 
                    onSettingsChanged(settings.copy(relayCompressorPin = value.toIntOrNull() ?: 0))
                    relayCompressorPin = value
                },
                label = { Text(stringResource(R.string.relay_compressor_pin), color = Color.White.copy(alpha = 0.8f)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = textFieldColors
            )
            
            OutlinedTextField(
                value = relayFreePin,
                onValueChange = { value -> 
                    onSettingsChanged(settings.copy(relayFreePin = value.toIntOrNull() ?: 0))
                    relayFreePin = value
                },
                label = { Text(stringResource(R.string.relay_free_pin), color = Color.White.copy(alpha = 0.8f)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = textFieldColors
            )
            
            OutlinedTextField(
                value = sensorPin,
                onValueChange = { value -> 
                    onSettingsChanged(settings.copy(sensorPin = value.toIntOrNull() ?: 0))
                    sensorPin = value
                },
                label = { Text(stringResource(R.string.sensor_pin), color = Color.White.copy(alpha = 0.8f)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = textFieldColors
            )
            
            OutlinedTextField(
                value = maxSecForStatus,
                onValueChange = { value -> 
                    onSettingsChanged(settings.copy(maxSecForStatus = value.toIntOrNull() ?: 30))
                    maxSecForStatus = value
                },
                label = { Text(stringResource(R.string.max_sec_for_status), color = Color.White.copy(alpha = 0.8f)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = textFieldColors
            )
            
            OutlinedTextField(
                value = measureWait,
                onValueChange = { value -> 
                    onSettingsChanged(settings.copy(measureWait = value.toFloatOrNull() ?: 2f))
                    measureWait = value
                },
                label = { Text(stringResource(R.string.measure_wait), color = Color.White.copy(alpha = 0.8f)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = textFieldColors
            )
            
            OutlinedTextField(
                value = sensorValueAt0PSI,
                onValueChange = { value -> 
                    onSettingsChanged(settings.copy(sensorValueAt0PSI = value.toFloatOrNull() ?: 0.228f))
                    sensorValueAt0PSI = value
                },
                label = { Text(stringResource(R.string.sensor_value_at_0_psi), color = Color.White.copy(alpha = 0.8f)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = textFieldColors
            )
            
            OutlinedTextField(
                value = sensorValueAt50PSI,
                onValueChange = { value -> 
                    onSettingsChanged(settings.copy(sensorValueAt50PSI = value.toFloatOrNull() ?: 2.853f))
                    sensorValueAt50PSI = value
                },
                label = { Text(stringResource(R.string.sensor_value_at_50_psi), color = Color.White.copy(alpha = 0.8f)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = textFieldColors
            )
            
            OutlinedTextField(
                value = precision,
                onValueChange = { value -> 
                    onSettingsChanged(settings.copy(precision = value.toFloatOrNull() ?: 1f))
                    precision = value
                },
                label = { Text(stringResource(R.string.precision_psi), color = Color.White.copy(alpha = 0.8f)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = textFieldColors
            )
            
            OutlinedTextField(
                value = btPinCode,
                onValueChange = { value -> 
                    onSettingsChanged(settings.copy(btPinCode = value))
                    btPinCode = value
                },
                label = { Text(stringResource(R.string.bt_pin_code), color = Color.White.copy(alpha = 0.8f)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = textFieldColors
            )

            // PressureCalcMethod selector (vertical layout)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.pressure_calc_method),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                val selectedMethod = settings.pressureCalcMethod
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedMethod == 1,
                        onClick = { onSettingsChanged(settings.copy(pressureCalcMethod = 1)) }
                    )
                    Text(text = stringResource(R.string.adc_calibration), color = Color.White)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedMethod == 2,
                        onClick = { onSettingsChanged(settings.copy(pressureCalcMethod = 2)) }
                    )
                    Text(text = stringResource(R.string.voltage_calibration), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun WifiSettings(
    wifiSsid: String,
    wifiPassword: String,
    availableNetworks: List<ScanResult>,
    onSsidChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onScanClicked: () -> Unit,
    onNetworkSelected: (String) -> Unit
) {
    var showNetworkList by remember { mutableStateOf(false) }
    var showNetworkDropdown by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) } // Track password visibility state

    // Define a common text field configuration
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedTextColor = Color.White,
        focusedTextColor = Color.White,
        unfocusedBorderColor = Color.Gray,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        cursorColor = Color.White
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.wifi_settings),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            color = Color.White
        )
        
        // WiFi SSID field with dropdown
        Box {
            OutlinedTextField(
                value = wifiSsid,
                onValueChange = onSsidChanged,
                label = { Text(stringResource(R.string.ssid), color = Color.White.copy(alpha = 0.8f)) },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors,
                trailingIcon = {
                    if (availableNetworks.isNotEmpty()) {
                        IconButton(onClick = { showNetworkDropdown = true }) {
                            Icon(
                                Icons.Default.SignalWifi4Bar,
                                contentDescription = "Select WiFi Network",
                                tint = Color.White
                            )
                        }
                    }
                }
            )
            
            // Dropdown menu for WiFi networks
            DropdownMenu(
                expanded = showNetworkDropdown,
                onDismissRequest = { showNetworkDropdown = false },
                modifier = Modifier
                    .background(Color(0xFF1E2530))
                    .heightIn(max = 300.dp) // Limit height
            ) {
                availableNetworks.forEach { network ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = network.SSID,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                SignalStrengthIndicator(level = network.level)
                            }
                        },
                        onClick = {
                            onNetworkSelected(network.SSID)
                            showNetworkDropdown = false
                        },
                        modifier = Modifier.background(Color(0xFF1E2530))
                    )
                }
            }
        }
        
        // Password field with visibility toggle
        OutlinedTextField(
            value = wifiPassword,
            onValueChange = onPasswordChanged,
            label = { Text(stringResource(R.string.password), color = Color.White.copy(alpha = 0.8f)) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors,
            trailingIcon = {
                // Toggle button for password visibility
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        // Show different icon based on password visibility
                        imageVector = if (passwordVisible) 
                            Icons.Filled.Visibility 
                        else 
                            Icons.Filled.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onScanClicked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(
                    text = stringResource(R.string.scan_networks),
                    color = Color.White
                )
            }
            
            if (availableNetworks.isNotEmpty()) {
                Text(
                    text = "${availableNetworks.size} " + stringResource(id = R.string.networks_found),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Keep the original list view option for those who prefer it
        if (availableNetworks.isNotEmpty()) {
            TextButton(
                onClick = { showNetworkList = !showNetworkList },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = if (showNetworkList) 
                        stringResource(R.string.hide_list) 
                    else 
                        stringResource(R.string.show_list),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        AnimatedVisibility(visible = showNetworkList && availableNetworks.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E2530) // Match MainScreen card color
                )
            ) {
                 Column(
                     modifier = Modifier
                         .fillMaxWidth()
                         .heightIn(max = 200.dp)
                         .verticalScroll(rememberScrollState())
                         .padding(8.dp)
                 ) {
                     availableNetworks.forEach { network ->
                         NetworkListItem(network = network) {
                             onNetworkSelected(network.SSID)
                             showNetworkList = false
                         }
                     }
                 }
            }
        }
    }
}

@Composable
private fun NetworkListItem(network: ScanResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(network.SSID, modifier = Modifier.weight(1f).padding(end = 8.dp))
        SignalStrengthIndicator(level = network.level)
    }
}

@Composable
private fun SignalStrengthIndicator(level: Int) {
    val icon = when (WifiManager.calculateSignalLevel(level, 4)) {
        0 -> Icons.Default.SignalWifiStatusbarConnectedNoInternet4
        1 -> Icons.Default.SignalWifiStatusbar4Bar
        2 -> Icons.Default.SignalWifi4Bar
        3 -> Icons.Filled.SignalWifi4Bar
        else -> Icons.Default.SignalWifiOff
    }
    val description = when (WifiManager.calculateSignalLevel(level, 4)) {
        0 -> stringResource(R.string.weak_signal)
        1 -> stringResource(R.string.fair_signal)
        2 -> stringResource(R.string.good_signal)
        3 -> stringResource(R.string.excellent_signal)
        else -> stringResource(R.string.no_signal)
    }
    Icon(
        icon,
        contentDescription = "$description ($level dBm)",
        tint = LocalContentColor.current // Ensure icon tint adapts
    )
}

@Composable
private fun LanguageSettings(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    val supportedLanguages = remember { 
        LocaleHelper.getSupportedLanguages()
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.language),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        supportedLanguages.forEach { (code, displayName) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLanguageSelected(code) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                
                RadioButton(
                    selected = currentLanguage == code,
                    onClick = { onLanguageSelected(code) }
                )
            }
        }
    }
} 
