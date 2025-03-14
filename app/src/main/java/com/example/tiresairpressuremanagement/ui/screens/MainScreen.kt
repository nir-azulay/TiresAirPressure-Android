package com.example.tiresairpressuremanagement.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tiresairpressuremanagement.data.ble.BleManager
import com.example.tiresairpressuremanagement.data.ble.ConnectionState
import com.example.tiresairpressuremanagement.domain.models.BleCommand
import com.example.tiresairpressuremanagement.domain.models.DeviceStatus
import com.example.tiresairpressuremanagement.ui.screens.main.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.tiresairpressuremanagement.R
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalView
import android.view.WindowManager

private const val TAG = "MainScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    bleManager: BleManager,
    onDisconnect: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    viewModel: MainViewModel = viewModel()
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
    
    // Animation for the pressure gauge
    val currentPressureAnimated = remember { Animatable(0f) }
    
    // Keep screen on during inflation/deflation
    val view = LocalView.current
    DisposableEffect(isInflating) {
        if (isInflating) {
            view.keepScreenOn = true
            android.util.Log.d(TAG, "Screen will stay on during inflation/deflation")
        } else {
            view.keepScreenOn = false
            android.util.Log.d(TAG, "Screen can turn off normally")
        }
        
        onDispose {
            view.keepScreenOn = false
            android.util.Log.d(TAG, "Screen can turn off normally (cleanup)")
        }
    }
    
    LaunchedEffect(deviceStatus?.pressure) {
        deviceStatus?.pressure?.let {
            currentPressureAnimated.animateTo(
                targetValue = it,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
        }
    }
    
    // Pulsating animation for the inflation indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulsating")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
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
        
        // Update inflation state based on operation mode
        deviceStatus?.let { status ->
            when (status.operationMode) {
                DeviceStatus.MODE_STOP -> {
                    if (isInflating) {
                        android.util.Log.d(TAG, "Device is in STOP mode, updating UI to stopped state")
                        isInflating = false
                    }
                }
                DeviceStatus.MODE_INFLATE, DeviceStatus.MODE_DEFLATE -> {
                    if (!isInflating) {
                        android.util.Log.d(TAG, "Device is in INFLATE/DEFLATE mode, updating UI to inflating state")
                        isInflating = true
                    }
                }
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
            title = { Text(stringResource(R.string.target_reached_title)) },
            text = { 
                Column {
                    Text(stringResource(R.string.target_reached_message))
                    Text(stringResource(R.string.target_value, targetReachedInfo?.first ?: 0f))
                    Text(stringResource(R.string.current_value, targetReachedInfo?.second ?: 0f))
                }
            },
            confirmButton = {
                Button(onClick = { 
                    android.util.Log.d(TAG, "Target reached dialog confirmed")
                    showTargetReachedDialog = false 
                }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                    IconButton(onClick = { 
                        isInflating = false
                        bleManager.disconnect()
                        onDisconnect()
                    }) {
                        Icon(Icons.Default.BluetoothDisabled, contentDescription = stringResource(R.string.disconnect_device))
                    }
                    IconButton(onClick = onAboutClick) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.about))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Main content
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                val screenHeight = maxHeight
                val screenWidth = maxWidth
                val isLandscape = screenWidth > screenHeight
                
                if (isLandscape) {
                    // Landscape layout
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Left column - Pressure gauge and connection status
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp, top = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top
                        ) {
                            // Connection status indicator
                            ConnectionStatusCard(connectionState)
                            
                            // Pressure gauge
                            PressureGaugeCard(
                                screenHeight = screenHeight,
                                currentPressureAnimated = currentPressureAnimated,
                                targetPressure = targetPressure,
                                maxPressure = maxPressure
                            )
                        }
                        
                        // Right column - Controls
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp, top = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top
                        ) {
                            // Target pressure selector
                            TargetPressureCard(
                                targetPressure = targetPressure,
                                onTargetPressureChange = { targetPressure = it },
                                minPressure = minPressure,
                                maxPressure = maxPressure
                            )
                            
                            // Start/Stop button
                            InflationControlButton(
                                isInflating = isInflating,
                                onInflationToggle = {
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
                                }
                            )
                            
                            // Add space between button and indicator
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Inflation status indicator
                            InflationStatusIndicator(isInflating = isInflating, scale = scale, targetPressure = targetPressure, currentPressureAnimated = currentPressureAnimated)
                            
                            // Preset pressure buttons
                            PresetPressureCard(onPresetSelected = { targetPressure = it })
                        }
                    }
                } else {
                    // Portrait layout (original)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Connection status indicator
                        ConnectionStatusCard(connectionState)
                        
                        // Pressure gauge
                        PressureGaugeCard(
                            screenHeight = screenHeight,
                            currentPressureAnimated = currentPressureAnimated,
                            targetPressure = targetPressure,
                            maxPressure = maxPressure
                        )
                        
                        // Target pressure selector
                        TargetPressureCard(
                            targetPressure = targetPressure,
                            onTargetPressureChange = { targetPressure = it },
                            minPressure = minPressure,
                            maxPressure = maxPressure
                        )
                        
                        // Start/Stop button
                        InflationControlButton(
                            isInflating = isInflating,
                            onInflationToggle = {
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
                            }
                        )
                        
                        // Add space between button and indicator
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Inflation status indicator
                        InflationStatusIndicator(isInflating = isInflating, scale = scale, targetPressure = targetPressure, currentPressureAnimated = currentPressureAnimated)
                        
                        // Preset pressure buttons
                        PresetPressureCard(onPresetSelected = { targetPressure = it })
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(connectionState: ConnectionState) {
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
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = when (connectionState) {
                    ConnectionState.Connected -> Icons.Default.Bluetooth
                    ConnectionState.Connecting -> Icons.Default.BluetoothSearching
                    ConnectionState.Disconnected -> Icons.Default.BluetoothDisabled
                },
                contentDescription = null,
                tint = when (connectionState) {
                    ConnectionState.Connected -> Color(0xFF4CAF50) // Green
                    ConnectionState.Connecting -> Color(0xFFFFC107) // Amber
                    ConnectionState.Disconnected -> Color(0xFFF44336) // Red
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when (connectionState) {
                    ConnectionState.Connected -> stringResource(R.string.connected)
                    ConnectionState.Connecting -> stringResource(R.string.connecting)
                    ConnectionState.Disconnected -> stringResource(R.string.disconnected)
                },
                color = when (connectionState) {
                    ConnectionState.Connected -> Color(0xFF4CAF50) // Green
                    ConnectionState.Connecting -> Color(0xFFFFC107) // Amber
                    ConnectionState.Disconnected -> Color(0xFFF44336) // Red
                },
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PressureGaugeCard(
    screenHeight: androidx.compose.ui.unit.Dp,
    currentPressureAnimated: Animatable<Float, AnimationVector1D>,
    targetPressure: Float,
    maxPressure: Float
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height((screenHeight * 0.35f).coerceAtLeast(180.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E2530) // Dark blue-gray color from screenshot
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Current pressure display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.current_pressure),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = Color.White
                )
                Text(
                    text = String.format("%.1f", currentPressureAnimated.value),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.pressure_unit),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            // Progress indicator
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Calculate progress
                val progress = (currentPressureAnimated.value / maxPressure).coerceIn(0f, 1f)
                val targetProgress = (targetPressure / maxPressure).coerceIn(0f, 1f)
                
                // Draw circular progress
                CircularProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 10.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.DarkGray
                )
                
                // Target indicator (smaller circle)
                CircularProgressIndicator(
                    progress = targetProgress,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    strokeWidth = 4.dp,
                    color = Color(0xFFBBDEFB), // Light blue color from screenshot
                    trackColor = Color.Transparent
                )
                
                // Target pressure text in the center
                Text(
                    text = String.format("%.1f", targetPressure),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun TargetPressureCard(
    targetPressure: Float,
    onTargetPressureChange: (Float) -> Unit,
    minPressure: Float,
    maxPressure: Float
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E2530) // Dark blue-gray color from screenshot
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.target_pressure),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Slider for target pressure
            Slider(
                value = targetPressure,
                onValueChange = onTargetPressureChange,
                valueRange = minPressure..maxPressure,
                steps = ((maxPressure - minPressure) / 0.5f).toInt() - 1,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFBBDEFB),
                    activeTrackColor = Color(0xFFBBDEFB),
                    inactiveTrackColor = Color.DarkGray
                )
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Decrease button
                FloatingActionButton(
                    onClick = { if (targetPressure > minPressure) onTargetPressureChange(targetPressure - 0.5f) },
                    modifier = Modifier.size(48.dp),
                    containerColor = Color(0xFFBBDEFB)
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = stringResource(R.string.decrease),
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Current target pressure
                Text(
                    text = stringResource(R.string.pressure_format, targetPressure),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                // Increase button
                FloatingActionButton(
                    onClick = { if (targetPressure < maxPressure) onTargetPressureChange(targetPressure + 0.5f) },
                    modifier = Modifier.size(48.dp),
                    containerColor = Color(0xFFBBDEFB)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.increase),
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InflationControlButton(
    isInflating: Boolean,
    onInflationToggle: () -> Unit
) {
    Button(
        onClick = onInflationToggle,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isInflating) MaterialTheme.colorScheme.error else Color(0xFF3F51B5)
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isInflating) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = stringResource(R.string.stop),
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.start),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isInflating) stringResource(R.string.stop) else stringResource(R.string.start),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun InflationStatusIndicator(
    isInflating: Boolean,
    scale: Float,
    targetPressure: Float = 0f,
    currentPressureAnimated: Animatable<Float, AnimationVector1D>? = null
) {
    // Determine if we're inflating or deflating based on pressure comparison
    val isDeflating = currentPressureAnimated != null && targetPressure < currentPressureAnimated.value
    
    AnimatedVisibility(
        visible = isInflating,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size((12.dp * scale).coerceAtLeast(10.dp))
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isDeflating) stringResource(R.string.mode_deflating) else stringResource(R.string.mode_inflating),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PresetPressureCard(
    onPresetSelected: (Float) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C3440) // Slightly lighter than the main cards
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.preset_pressures),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // First row of preset buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PresetPressureButton(pressure = 8f, onSelect = onPresetSelected)
                PresetPressureButton(pressure = 10f, onSelect = onPresetSelected)
                PresetPressureButton(pressure = 12f, onSelect = onPresetSelected)
                PresetPressureButton(pressure = 16f, onSelect = onPresetSelected)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Second row of preset buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PresetPressureButton(pressure = 20f, onSelect = onPresetSelected)
                PresetPressureButton(pressure = 30f, onSelect = onPresetSelected)
                PresetPressureButton(pressure = 38f, onSelect = onPresetSelected)
                PresetPressureButton(pressure = 40f, onSelect = onPresetSelected)
            }
        }
    }
}

@Composable
fun PresetPressureButton(
    pressure: Float,
    onSelect: (Float) -> Unit
) {
    Button(
        onClick = { onSelect(pressure) },
        modifier = Modifier
            .width(70.dp)
            .height(40.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFBBDEFB) // Light blue color from screenshot
        )
    ) {
        Text(
            text = "$pressure",
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
} 