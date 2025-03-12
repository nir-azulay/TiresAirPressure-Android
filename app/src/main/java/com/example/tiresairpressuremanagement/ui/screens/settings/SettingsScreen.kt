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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tiresairpressuremanagement.domain.models.PressureUnit
import com.example.tiresairpressuremanagement.domain.models.TemperatureUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val availableNetworks by viewModel.availableNetworks.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    var showDiscardDialog by remember { mutableStateOf(false) }
    var expandedWifiDropdown by remember { mutableStateOf(false) }
    
    // Log connection state
    LaunchedEffect(isConnected) {
        Log.d("SettingsScreen", "isConnected: $isConnected")
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // כפתור חזרה וכותרת
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showDiscardDialog = true }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "חזור")
            }
            Text(
                text = "הגדרות",
                style = MaterialTheme.typography.headlineMedium
            )
            // ריווח שווה משני הצדדים
            Spacer(modifier = Modifier.width(48.dp))
        }
        
        // הוספת הודעה אם לא מחובר, אבל עדיין להציג את כל השדות
        if (!isConnected) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = "לא מחובר למכשיר. השינויים לא יישמרו.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        // שם המכשיר
        OutlinedTextField(
            value = settings.deviceName,
            onValueChange = { viewModel.updateSettings(deviceName = it) },
            label = { Text("שם המכשיר") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
        
        // הגדרות WiFi
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "הגדרות WiFi",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // SSID
                OutlinedTextField(
                    value = settings.wifiSsid,
                    onValueChange = { viewModel.updateSettings(wifiSsid = it) },
                    label = { Text("SSID") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
                
                // סריקת רשתות
                Button(
                    onClick = { viewModel.scanWifiNetworks() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("סרוק רשתות זמינות")
                }
                
                // רשימת רשתות זמינות
                if (availableNetworks.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    ) {
                        items(availableNetworks) { network ->
                            Text(
                                text = network,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.updateSettings(wifiSsid = network) }
                                    .padding(8.dp)
                            )
                        }
                    }
                }
                
                // סיסמה
                OutlinedTextField(
                    value = settings.wifiPassword,
                    onValueChange = { viewModel.updateSettings(wifiPassword = it) },
                    label = { Text("סיסמה") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        }
        
        // הגדרות חומרה
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "הגדרות חומרה",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Relay Compressor Pin
                OutlinedTextField(
                    value = settings.relayCompressorPin.toString(),
                    onValueChange = { viewModel.updateSettings(relayCompressorPin = it.toIntOrNull() ?: 0) },
                    label = { Text("Relay Compressor Pin") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
                
                // Relay Free Pin
                OutlinedTextField(
                    value = settings.relayFreePin.toString(),
                    onValueChange = { viewModel.updateSettings(relayFreePin = it.toIntOrNull() ?: 0) },
                    label = { Text("Relay Free Pin") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
                
                // Sensor Pin
                OutlinedTextField(
                    value = settings.sensorPin.toString(),
                    onValueChange = { viewModel.updateSettings(sensorPin = it.toIntOrNull() ?: 0) },
                    label = { Text("Sensor Pin") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        }
        
        // כיול חיישן
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "כיול חיישן",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // ערך ב-0 PSI
                OutlinedTextField(
                    value = settings.sensorValueAt0PSI.toString(),
                    onValueChange = { 
                        viewModel.updateSettings(
                            sensorValueAt0PSI = it.toIntOrNull() ?: 0,
                            sensorValueAt50PSI = settings.sensorValueAt50PSI
                        )
                    },
                    label = { Text("ערך ב-0 PSI") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
                
                // ערך ב-50 PSI
                OutlinedTextField(
                    value = settings.sensorValueAt50PSI.toString(),
                    onValueChange = { 
                        viewModel.updateSettings(
                            sensorValueAt0PSI = settings.sensorValueAt0PSI,
                            sensorValueAt50PSI = it.toIntOrNull() ?: 1023
                        )
                    },
                    label = { Text("ערך ב-50 PSI") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        }
        
        // הגדרות נוספות
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "הגדרות נוספות",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Auto Start
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("התחלה אוטומטית")
                    Switch(
                        checked = settings.autoStart,
                        onCheckedChange = { viewModel.updateSettings(autoStart = it) }
                    )
                }
                
                // Max Sec For Status
                OutlinedTextField(
                    value = settings.maxSecForStatus.toString(),
                    onValueChange = { viewModel.updateSettings(maxSecForStatus = it.toIntOrNull() ?: 30) },
                    label = { Text("זמן מקסימלי לסטטוס (שניות)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
                
                // Measure Wait
                OutlinedTextField(
                    value = settings.measureWait.toString(),
                    onValueChange = { viewModel.updateSettings(measureWait = it.toIntOrNull() ?: 2) },
                    label = { Text("זמן המתנה למדידה (שניות)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
                
                // Precision
                OutlinedTextField(
                    value = settings.precision.toString(),
                    onValueChange = { viewModel.updateSettings(precision = it.toIntOrNull() ?: 1) },
                    label = { Text("דיוק (PSI)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
                
                // BT Pin Code
                OutlinedTextField(
                    value = settings.btPinCode,
                    onValueChange = { viewModel.updateSettings(btPinCode = it) },
                    label = { Text("קוד PIN לבלוטות'") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        }
        
        // מצב סימולציה
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "מצב סימולציה",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Switch(
                        checked = settings.simulationMode,
                        onCheckedChange = { viewModel.setSimulationMode(it) }
                    )
                }
            }
        }
        
        // כפתור שמירה
        Button(
            onClick = { viewModel.saveSettings { onBackClick() } },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Text("שמור הגדרות")
        }
    }
    
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("לבטל שינויים?") },
            text = { Text("האם אתה בטוח שברצונך לבטל את השינויים?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBackClick()
                    }
                ) {
                    Text("כן, בטל")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("לא, המשך בעריכה")
                }
            }
        )
    }
} 