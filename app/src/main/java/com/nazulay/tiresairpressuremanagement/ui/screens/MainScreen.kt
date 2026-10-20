package com.nazulay.tiresairpressuremanagement.ui.screens

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
import com.nazulay.tiresairpressuremanagement.data.ble.BleManager
import com.nazulay.tiresairpressuremanagement.data.ble.ConnectionState
import com.nazulay.tiresairpressuremanagement.domain.models.BleCommand
import com.nazulay.tiresairpressuremanagement.domain.models.DeviceStatus
import com.nazulay.tiresairpressuremanagement.ui.screens.main.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.nazulay.tiresairpressuremanagement.R
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import android.util.Log
import android.app.Application
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CancellationException

private const val TAG = "MainScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    bleManager: BleManager,
    onDisconnect: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onExitClick: () -> Unit
) {
    val connectionState by bleManager.connectionState.collectAsState()
    val deviceName by bleManager.connectedDeviceName.collectAsState()
    val deviceStatus by bleManager.deviceStatus.collectAsState()
    var targetPressure by remember { mutableStateOf(30f) }
    var isInflating by remember { mutableStateOf(false) }
    
    // Define min and max pressure values
    val minPressure = 5f
    val maxPressure = 40f
    
    // Create MainViewModel instance
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModel.provideFactory(LocalContext.current.applicationContext as Application, bleManager)
    )
    val debugMode by viewModel.debugMode.collectAsState()
    
    // Debug logging for debug mode changes
    LaunchedEffect(debugMode) {
        Log.d(TAG, "Debug mode changed to: $debugMode")
    }
    
    // Debug logging for device status changes
    LaunchedEffect(deviceStatus) {
        Log.d(TAG, "Device status changed: $deviceStatus")
    }
    
    // State for target reached dialog
    var showTargetReachedDialog by remember { mutableStateOf(false) }
    var targetReachedInfo by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    
    // Animation for the pressure gauge
    val currentPressureAnimated = remember { Animatable(0f) }
    
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
        } catch (e: CancellationException) {
            // This is expected when the composition is left
            android.util.Log.d(TAG, "Target reached event collection cancelled due to composition leaving")
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
                title = { 
                    Text(
                        text = stringResource(R.string.app_name),
                        fontSize = 16.sp, // Smaller font size for the app title
                        fontWeight = FontWeight.Medium // Medium weight for better readability at smaller size
                    ) 
                },
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
                    IconButton(onClick = onExitClick) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.exit_app))
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212)) // רקע כהה
        ) {
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
                            ConnectionStatusCard(connectionState, deviceName, bleManager)
                            
                            // Pressure gauge
                            PressureGaugeCard(
                                screenHeight = screenHeight,
                                currentPressureAnimated = currentPressureAnimated,
                                targetPressure = targetPressure,
                                maxPressure = maxPressure,
                                isLandscape = true,
                                debugMode = debugMode,
                                deviceStatus = deviceStatus
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
                            // Container for other controls
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TargetPressureCard(
                                    targetPressure = targetPressure,
                                    minPressure = minPressure,
                                    maxPressure = maxPressure,
                                    onTargetPressureChange = { targetPressure = it }
                                )
                                
                                // Preset pressure buttons
                                PresetButtons(viewModel.predefinedPressures.collectAsState().value, { targetPressure = it })

                                InflationControlButton(
                                    isInflating = isInflating,
                                    onInflationToggle = {
                                        if (isInflating) {
                                            // Stop inflation
                                            bleManager.sendCommand(BleCommand.Stop())
                                            android.util.Log.d(TAG, "Sent STOP command")
                                            isInflating = false // Update UI immediately
                                        } else {
                                            // Start inflation
                                            bleManager.sendCommand(BleCommand.StartTargetPressure(targetPressure = targetPressure))
                                            android.util.Log.d(TAG, "Sent START command with target: $targetPressure")
                                            isInflating = true // Update UI immediately
                                        }
                                    }
                                )
                                
                                InflationStatusIndicator(
                                    isInflating = isInflating,
                                    scale = scale,
                                    targetPressure = targetPressure,
                                    currentPressureAnimated = currentPressureAnimated
                                )
                            }
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
                        ConnectionStatusCard(connectionState, deviceName, bleManager)
                        
                        // Pressure gauge
                        PressureGaugeCard(
                            screenHeight = screenHeight,
                            currentPressureAnimated = currentPressureAnimated,
                            targetPressure = targetPressure,
                            maxPressure = maxPressure,
                            isLandscape = false,
                            debugMode = debugMode,
                            deviceStatus = deviceStatus
                        )
                        
                        // Container for other controls
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TargetPressureCard(
                                targetPressure = targetPressure,
                                minPressure = minPressure,
                                maxPressure = maxPressure,
                                onTargetPressureChange = { targetPressure = it }
                            )
                            
                            // Preset pressure buttons
                            PresetButtons(viewModel.predefinedPressures.collectAsState().value, { targetPressure = it })

                            InflationControlButton(
                                isInflating = isInflating,
                                onInflationToggle = {
                                    if (isInflating) {
                                        // Stop inflation
                                        bleManager.sendCommand(BleCommand.Stop())
                                        android.util.Log.d(TAG, "Sent STOP command")
                                        isInflating = false // Update UI immediately
                                    } else {
                                        // Start inflation
                                        bleManager.sendCommand(BleCommand.StartTargetPressure(targetPressure = targetPressure))
                                        android.util.Log.d(TAG, "Sent START command with target: $targetPressure")
                                        isInflating = true // Update UI immediately
                                    }
                                }
                            )
                            
                            InflationStatusIndicator(
                                isInflating = isInflating,
                                scale = scale,
                                targetPressure = targetPressure,
                                currentPressureAnimated = currentPressureAnimated
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(connectionState: ConnectionState, deviceName: String?, bleManager: BleManager) {
    var showTooltip by remember { mutableStateOf(false) }
    var currentDuration by remember { mutableStateOf(0L) }
    
    // Update duration when tooltip is shown
    LaunchedEffect(showTooltip) {
        if (showTooltip && connectionState == ConnectionState.Connected) {
            while (showTooltip) {
                currentDuration = bleManager.getConnectionDuration()
                delay(1000)
            }
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (connectionState) {
                ConnectionState.Connected -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                ConnectionState.Connecting -> Color(0xFFFFC107).copy(alpha = 0.2f)
                ConnectionState.Disconnected -> Color(0xFFF44336).copy(alpha = 0.2f)
                ConnectionState.PermissionDenied -> Color(0xFFF44336).copy(alpha = 0.2f)
                ConnectionState.Scanning -> Color(0xFF2196F3).copy(alpha = 0.2f)
                ConnectionState.BluetoothDisabled -> Color(0xFFF44336).copy(alpha = 0.2f)
                ConnectionState.Error -> Color(0xFFF44336).copy(alpha = 0.2f)
            }
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTooltip = !showTooltip },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = when (connectionState) {
                        ConnectionState.Connected -> Icons.Default.Bluetooth
                        ConnectionState.Connecting -> Icons.Default.BluetoothSearching
                        ConnectionState.Disconnected -> Icons.Default.BluetoothDisabled
                        ConnectionState.PermissionDenied -> Icons.Default.BluetoothDisabled
                        ConnectionState.Scanning -> Icons.Default.Search
                        ConnectionState.BluetoothDisabled -> Icons.Default.BluetoothDisabled
                        ConnectionState.Error -> Icons.Default.BluetoothDisabled
                    },
                    contentDescription = null,
                    tint = when (connectionState) {
                        ConnectionState.Connected -> Color(0xFF4CAF50)
                        ConnectionState.Connecting -> Color(0xFFFFC107)
                        ConnectionState.Disconnected -> Color(0xFFF44336)
                        ConnectionState.PermissionDenied -> Color(0xFFF44336)
                        ConnectionState.Scanning -> Color(0xFF2196F3)
                        ConnectionState.BluetoothDisabled -> Color(0xFFF44336)
                        ConnectionState.Error -> Color(0xFFF44336)
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = when (connectionState) {
                        ConnectionState.Connected -> stringResource(R.string.connected)
                        ConnectionState.Connecting -> stringResource(R.string.connecting)
                        ConnectionState.Disconnected -> stringResource(R.string.disconnected)
                        ConnectionState.PermissionDenied -> stringResource(R.string.permission_denied)
                        ConnectionState.Scanning -> stringResource(R.string.scanning)
                        ConnectionState.BluetoothDisabled -> stringResource(R.string.bluetooth_disabled)
                        ConnectionState.Error -> stringResource(R.string.connection_error)
                    },
                    color = when (connectionState) {
                        ConnectionState.Connected -> Color(0xFF4CAF50)
                        ConnectionState.Connecting -> Color(0xFFFFC107)
                        ConnectionState.Disconnected -> Color(0xFFF44336)
                        ConnectionState.PermissionDenied -> Color(0xFFF44336)
                        ConnectionState.Scanning -> Color(0xFF2196F3)
                        ConnectionState.BluetoothDisabled -> Color(0xFFF44336)
                        ConnectionState.Error -> Color(0xFFF44336)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                if (connectionState == ConnectionState.Connected && deviceName != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Normal
                    )
                    Text(
                        text = deviceName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Normal
                    )
                    Text(
                        text = ")",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Normal
                    )
                }
            }
            
            if (showTooltip && connectionState == ConnectionState.Connected) {
                val duration = currentDuration / 1000 // Convert to seconds
                val hours = duration / 3600
                val minutes = (duration % 3600) / 60
                val seconds = duration % 60
                
                val durationText = when {
                    hours > 0 -> stringResource(R.string.connection_duration_hours, hours, minutes)
                    minutes > 0 -> stringResource(R.string.connection_duration_minutes, minutes, seconds)
                    else -> stringResource(R.string.connection_duration_seconds, seconds)
                }
                
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 32.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 3.dp
                ) {
                    Text(
                        text = durationText,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PressureGaugeCard(
    screenHeight: androidx.compose.ui.unit.Dp,
    currentPressureAnimated: Animatable<Float, AnimationVector1D>,
    targetPressure: Float,
    maxPressure: Float,
    isLandscape: Boolean = false,
    debugMode: Boolean = false,
    deviceStatus: DeviceStatus? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(
                if (isLandscape) {
                    (screenHeight * 0.65f).coerceAtLeast(220.dp)
                } else {
                    (screenHeight * 0.35f).coerceAtLeast(180.dp)
                }
            ),
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
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.pressure_unit),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                // Show debug info if debugMode is enabled and values are present
                Log.d(TAG, "Debug display check: debugMode=$debugMode, deviceStatus=$deviceStatus, rawADC=${deviceStatus?.rawADC}, voltage=${deviceStatus?.voltage}")
                if (debugMode && deviceStatus?.rawADC != null && deviceStatus.voltage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "rawADC: ${deviceStatus.rawADC}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Yellow
                    )
                    Text(
                        text = "voltage: ${String.format("%.3f", deviceStatus.voltage)} V",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Yellow
                    )
                }
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
                    strokeWidth = 16.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.DarkGray
                )
                
                // Target indicator (smaller circle)
                CircularProgressIndicator(
                    progress = targetProgress,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    strokeWidth = 8.dp,
                    color = Color(0xFFBBDEFB), // Light blue color from screenshot
                    trackColor = Color.Transparent
                )
                
                // Target pressure text in the center
                Text(
                    text = String.format("%.1f", targetPressure),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
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
                    fontWeight = FontWeight.ExtraBold,
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
fun PresetButtons(
    predefinedPressures: List<Float>,
    onPresetSelected: (Float) -> Unit
) {
    if (predefinedPressures.isEmpty()) {
        // If no preset pressures, show nothing
        return
    }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Always split the predefined pressures into rows with exactly 4 buttons per row
        val chunkedPressures = predefinedPressures.chunked(4)
        
        // Create rows for each chunk
        chunkedPressures.forEach { rowPressures ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                // Always create a row with 4 buttons, even if some are empty
                repeat(4) { index ->
                    if (index < rowPressures.size) {
                        // Real button with pressure value
                        PresetPressureButton(
                            pressure = rowPressures[index], 
                            onSelect = onPresetSelected,
                            modifier = Modifier.weight(1f).padding(horizontal = 2.dp).defaultMinSize(minHeight = 48.dp)
                        )
                    } else {
                        // Empty space with the same size as a button for alignment
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                                .defaultMinSize(minHeight = 48.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PresetPressureButton(
    pressure: Float,
    onSelect: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { onSelect(pressure) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.preset_pressure_format, pressure.toInt()),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
} 
