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
            delay(5500) // Wait 5.5 seconds (slightly longer than the 5s in ViewModel)
            Log.d("SettingsScreen", "Settings saved, returning to main screen for reconnection")
            onBackClick() // Return to main screen to let regular app flow handle reconnection
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
                    .padding(horizontal = 16.dp),
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

                // Display Mode Card
                item {
                    SettingsCard {
                        DisplayModeSettings(
                            currentMode = appSettings.themeMode,
                            onModeSelected = { viewModel.updateThemeMode(it) }
                        )
                    }
                }

                // Simulation Mode Card
                item {
                    SettingsCard {
                        SimulationModeSettings(
                            isEnabled = appSettings.simulationMode,
                            onToggle = { viewModel.updateSimulationMode(it) }
                        )
                    }
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
                    if (currentSettings != null) { // Use the local variable for the check
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
                                            autoStart = updatedSettings.autoStart,
                                            maxSecForStatus = updatedSettings.maxSecForStatus,
                                            sensorValueAt0PSI = updatedSettings.sensorValueAt0PSI,
                                            sensorValueAt50PSI = updatedSettings.sensorValueAt50PSI,
                                            measureWait = updatedSettings.measureWait,
                                            precision = updatedSettings.precision,
                                            btPinCode = updatedSettings.btPinCode
                                        )
                                    }
                                )
                            }
                        }

                        // Save Device Settings Button
                        item {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            isSaving = true
                                            viewModel.saveDeviceSettingsAndReconnect(forceReconnect)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                    ) {
                                        Text(stringResource(R.string.save_settings))
                                    }
                                    
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.discardChanges()
                                        },
                                        modifier = Modifier
                                            .weight(1f),
                                        enabled = hasUnsavedChanges
                                    ) {
                                        Text(stringResource(R.string.discard))
                                    }
                                }
                                
                                if (hasUnsavedChanges) {
                                    Text(
                                        text = stringResource(R.string.unsaved_changes_message),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Yellow,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            SettingsCard {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                    Text("Loading device settings...", modifier = Modifier.padding(top = 60.dp))
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
                                    text = "Current connection state: ${connectionState}", // Use the state directly
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
                                    Text(stringResource(R.string.try_reconnect))
                                }
                            }
                        }
                    }
                }
                
                // Bottom spacing
                item {
                    Spacer(modifier = Modifier.height(24.dp))
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
private fun DisplayModeSettings(
    currentMode: Int,
    onModeSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.display_mode),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), // Smaller title
            color = Color.White, // Match MainScreen text color
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min), // Ensure row height adapts to content
            horizontalArrangement = Arrangement.spacedBy(8.dp) // Add spacing
        ) {
            ThemeButton(
                modifier = Modifier.weight(1f), // Make buttons share space equally
                icon = Icons.Default.SettingsBrightness,
                text = stringResource(R.string.system_default),
                isSelected = currentMode == SettingsViewModel.THEME_MODE_SYSTEM,
                onClick = { onModeSelected(SettingsViewModel.THEME_MODE_SYSTEM) }
            )
            ThemeButton(
                modifier = Modifier.weight(1f), // Make buttons share space equally
                icon = Icons.Default.LightMode,
                text = stringResource(R.string.light_mode),
                isSelected = currentMode == SettingsViewModel.THEME_MODE_LIGHT,
                onClick = { onModeSelected(SettingsViewModel.THEME_MODE_LIGHT) }
            )
            ThemeButton(
                modifier = Modifier.weight(1f), // Make buttons share space equally
                icon = Icons.Default.DarkMode,
                text = stringResource(R.string.dark_mode),
                isSelected = currentMode == SettingsViewModel.THEME_MODE_DARK,
                onClick = { onModeSelected(SettingsViewModel.THEME_MODE_DARK) }
            )
        }
        
        Text(
            text = stringResource(R.string.display_mode_change_notice),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f), // Match MainScreen text color with reduced opacity
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun ThemeButton(
    modifier: Modifier = Modifier, // Add modifier parameter
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = ButtonDefaults.buttonColors(
        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    )
    Button(
        onClick = onClick,
        colors = colors,
        modifier = modifier.height(IntrinsicSize.Min) // Apply modifier here
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = text)
            Spacer(Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.labelSmall)
        }
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
        Column {
            Text(
                text = stringResource(R.string.simulation_mode),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), // Smaller title
                color = Color.White // Match MainScreen text color
            )
            Text(
                text = stringResource(R.string.simulation_mode_description),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f) // Match MainScreen text color with reduced opacity
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.predefined_pressures),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), // Smaller title
            color = Color.White // Match MainScreen text color
        )
        
        Text(
            text = stringResource(R.string.predefined_pressures_description),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f), // Match MainScreen text color with reduced opacity
            modifier = Modifier.padding(vertical = 4.dp)
        )
        
        // Display pressures in two columns for better layout
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pressures.chunked(2).forEach { rowPressures ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowPressures.forEachIndexed { pressureIndexInRow, pressure ->
                        // Calculate the actual index in the original list
                        val originalIndex = pressures.indexOf(pressure) // Find index (assumes unique values for now)
                        if (originalIndex != -1) {
                             Box(modifier = Modifier.weight(1f)) { // Make columns equal width
                                 PressureSpinner( // Use the new spinner composable
                                     label = stringResource(R.string.predefined_pressure_format, originalIndex + 1),
                                     value = pressure,
                                     onValueChange = { newValue ->
                                         val newPressures = pressures.toMutableList()
                                         newPressures[originalIndex] = newValue
                                         onPressuresUpdated(newPressures)
                                     }
                                 )
                             }
                        } else {
                            // Handle cases where pressure might not be unique (though unlikely here)
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    // Add Spacer if only one item in the last row
                    if (rowPressures.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.hardware_settings),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), // Smaller title
            color = Color.White, // Match MainScreen text color
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Define a common text field configuration
        val textFieldColors = OutlinedTextFieldDefaults.colors(
            unfocusedTextColor = Color.White,
            focusedTextColor = Color.White,
            unfocusedBorderColor = Color.Gray,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            cursorColor = Color.White
        )
        
        OutlinedTextField(
            value = settings.relayCompressorPin.toString(),
            onValueChange = { value -> 
                onSettingsChanged(settings.copy(relayCompressorPin = value.toIntOrNull() ?: 0))
            },
            label = { Text(stringResource(R.string.relay_compressor_pin), color = Color.White.copy(alpha = 0.8f)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = textFieldColors
        )
        
        OutlinedTextField(
            value = settings.relayFreePin.toString(),
            onValueChange = { value -> 
                onSettingsChanged(settings.copy(relayFreePin = value.toIntOrNull() ?: 0))
            },
            label = { Text(stringResource(R.string.relay_free_pin), color = Color.White.copy(alpha = 0.8f)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = textFieldColors
        )
        
        OutlinedTextField(
            value = settings.sensorPin.toString(),
            onValueChange = { value -> 
                onSettingsChanged(settings.copy(sensorPin = value.toIntOrNull() ?: 0))
            },
            label = { Text(stringResource(R.string.sensor_pin), color = Color.White.copy(alpha = 0.8f)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = textFieldColors
        )
        
        OutlinedTextField(
            value = settings.maxSecForStatus.toString(),
            onValueChange = { value -> 
                onSettingsChanged(settings.copy(maxSecForStatus = value.toIntOrNull() ?: 30))
            },
            label = { Text(stringResource(R.string.max_sec_for_status), color = Color.White.copy(alpha = 0.8f)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = textFieldColors
        )
        
        OutlinedTextField(
            value = settings.measureWait.toString(),
            onValueChange = { value -> 
                onSettingsChanged(settings.copy(measureWait = value.toFloatOrNull() ?: 2f))
            },
            label = { Text(stringResource(R.string.measure_wait), color = Color.White.copy(alpha = 0.8f)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = textFieldColors
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.auto_start),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            
            Switch(
                checked = settings.autoStart,
                onCheckedChange = { value -> 
                    onSettingsChanged(settings.copy(autoStart = value))
                }
            )
        }
        
        OutlinedTextField(
            value = settings.sensorValueAt0PSI.toString(),
            onValueChange = { value -> 
                onSettingsChanged(settings.copy(sensorValueAt0PSI = value.toIntOrNull() ?: 0))
            },
            label = { Text(stringResource(R.string.sensor_value_at_0_psi), color = Color.White.copy(alpha = 0.8f)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = textFieldColors
        )
        
        OutlinedTextField(
            value = settings.sensorValueAt50PSI.toString(),
            onValueChange = { value -> 
                onSettingsChanged(settings.copy(sensorValueAt50PSI = value.toIntOrNull() ?: 1023))
            },
            label = { Text(stringResource(R.string.sensor_value_at_50_psi), color = Color.White.copy(alpha = 0.8f)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = textFieldColors
        )
        
        OutlinedTextField(
            value = settings.precision.toString(),
            onValueChange = { value -> 
                onSettingsChanged(settings.copy(precision = value.toFloatOrNull() ?: 1f))
            },
            label = { Text(stringResource(R.string.precision_psi), color = Color.White.copy(alpha = 0.8f)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = textFieldColors
        )
        
        OutlinedTextField(
            value = settings.btPinCode,
            onValueChange = { value -> 
                onSettingsChanged(settings.copy(btPinCode = value))
            },
            label = { Text(stringResource(R.string.bt_pin_code), color = Color.White.copy(alpha = 0.8f)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = textFieldColors
        )
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
        
        OutlinedTextField(
            value = wifiSsid,
            onValueChange = onSsidChanged,
            label = { Text(stringResource(R.string.ssid), color = Color.White.copy(alpha = 0.8f)) },
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors
        )
        
        OutlinedTextField(
            value = wifiPassword,
            onValueChange = onPasswordChanged,
            label = { Text(stringResource(R.string.password), color = Color.White.copy(alpha = 0.8f)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onScanClicked) {
                Text(stringResource(R.string.scan_networks))
            }
            if (availableNetworks.isNotEmpty()) {
                 TextButton(onClick = { showNetworkList = !showNetworkList }) {
                     Text(
                         text = if (showNetworkList) 
                             stringResource(R.string.hide_list) 
                         else 
                             stringResource(R.string.show_list_count, availableNetworks.size),
                         color = MaterialTheme.colorScheme.primary
                     )
                 }
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
