package com.nazulay.tiresairpressuremanagement.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

import androidx.compose.foundation.BorderStroke
import com.nazulay.tiresairpressuremanagement.data.ota.FirmwareUpgradeManager

private const val TAG = "MainScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    bleManager: BleManager,
    onDisconnect: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onExitClick: () -> Unit,
    onFirmwareUpgradeClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val upgradeManager = remember {
        FirmwareUpgradeManager.getInstance(context).also { it.attachBleManager(bleManager) }
    }
    val isUpdateAvailable by upgradeManager.isUpdateAvailable.collectAsState()
    val updateSuggestionDismissed by upgradeManager.updateSuggestionDismissed.collectAsState()
    val controllerVersion by upgradeManager.deviceVersion.collectAsState()

    val connectionState by bleManager.connectionState.collectAsState()
    val deviceName by bleManager.connectedDeviceName.collectAsState()
    val accessCodeRequired by bleManager.accessCodeRequired.collectAsState()
    var accessCodeInput by remember { mutableStateOf("") }
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
    
    if (accessCodeRequired) {
        AlertDialog(
            onDismissRequest = { bleManager.dismissAccessPrompt() },
            title = { Text(stringResource(R.string.access_code_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.access_code_message))
                    OutlinedTextField(
                        value = accessCodeInput,
                        onValueChange = { accessCodeInput = it.take(32) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    bleManager.submitAccessCode(accessCodeInput)
                    accessCodeInput = ""
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { bleManager.dismissAccessPrompt() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Animated Target Reached Celebration Dialog
    if (showTargetReachedDialog && targetReachedInfo != null) {
        val targetVal = targetReachedInfo?.first ?: 0f
        val currentVal = targetReachedInfo?.second ?: 0f
        val dialogScale = remember { Animatable(0.6f) }
        val ringProgress = remember { Animatable(0f) }

        LaunchedEffect(showTargetReachedDialog) {
            dialogScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            )
        }
        LaunchedEffect(Unit) {
            ringProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing)
            )
        }

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showTargetReachedDialog = false }
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Shockwave ring behind card
                Canvas(modifier = Modifier.size(260.dp)) {
                    val r = (size.minDimension / 2f) * (0.5f + ringProgress.value * 0.8f)
                    drawCircle(
                        color = Color(0xFF10B981).copy(alpha = (1f - ringProgress.value).coerceIn(0f, 1f)),
                        radius = r,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF171F2C)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size((76.dp * scale).coerceIn(68.dp, 88.dp))
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.target_reached_title).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = stringResource(R.string.target_reached_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = String.format("%.1f PSI", currentVal),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF10B981)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(vertical = 10.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.target_short), style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                                Text(String.format("%.1f PSI", targetVal), fontWeight = FontWeight.Bold, color = Color(0xFFFFA726))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.current_short), style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                                Text(String.format("%.1f PSI", currentVal), fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { showTargetReachedDialog = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Text(
                                text = stringResource(R.string.ok).uppercase(),
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
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
                    containerColor = Color(0xFF131A24),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D1117))
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
                            if (connectionState == ConnectionState.Connected && isUpdateAvailable && !updateSuggestionDismissed) {
                                Spacer(modifier = Modifier.height(8.dp))
                                FirmwareUpdateSuggestionBanner(
                                    deviceVersion = controllerVersion,
                                    bundledVersion = upgradeManager.bundledVersion,
                                    onUpgrade = onFirmwareUpgradeClick,
                                    onDismiss = { upgradeManager.dismissUpdateSuggestion() }
                                )
                            }
                            
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
                                PresetButtons(
                                    predefinedPressures = viewModel.predefinedPressures.collectAsState().value,
                                    selectedPressure = targetPressure,
                                    onPresetSelected = { targetPressure = it }
                                )

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
                        if (connectionState == ConnectionState.Connected && isUpdateAvailable && !updateSuggestionDismissed) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FirmwareUpdateSuggestionBanner(
                                deviceVersion = controllerVersion,
                                bundledVersion = upgradeManager.bundledVersion,
                                onUpgrade = onFirmwareUpgradeClick,
                                onDismiss = { upgradeManager.dismissUpdateSuggestion() }
                            )
                        }
                        
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
                            PresetButtons(
                                predefinedPressures = viewModel.predefinedPressures.collectAsState().value,
                                selectedPressure = targetPressure,
                                onPresetSelected = { targetPressure = it }
                            )

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
private fun FirmwareUpdateSuggestionBanner(
    deviceVersion: String,
    bundledVersion: String,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF6D00).copy(alpha = 0.14f)),
        border = BorderStroke(1.dp, Color(0xFFFF6D00).copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdateAlt,
                    contentDescription = null,
                    tint = Color(0xFFFF9E40),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.firmware_update_available_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = stringResource(
                            R.string.firmware_update_available_message_format,
                            deviceVersion,
                            bundledVersion
                        ),
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onUpgrade,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6D00)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.upgrade_now_button),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.later_button),
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
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
    val isDeflating = currentPressureAnimated.value > targetPressure + 0.5f
    val activeAccent = when {
        Math.abs(currentPressureAnimated.value - targetPressure) <= 0.5f && currentPressureAnimated.value > 0f -> Color(0xFF10B981)
        isDeflating -> Color(0xFFFF453A)
        else -> Color(0xFF00B4FF)
    }

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
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF171F2C)
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
                    text = stringResource(R.string.current_pressure).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
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
                    color = activeAccent
                )
                val delta = targetPressure - currentPressureAnimated.value
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (Math.abs(delta) <= 0.5f) stringResource(R.string.at_target) else String.format(stringResource(R.string.psi_to_target_format), delta),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = activeAccent
                )
                // Show debug info if debugMode is enabled and values are present
                Log.d(TAG, "Debug display check: debugMode=$debugMode, deviceStatus=$deviceStatus, rawADC=${deviceStatus?.rawADC}, voltage=${deviceStatus?.voltage}")
                if (debugMode && deviceStatus?.rawADC != null && deviceStatus.voltage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "rawADC: ${deviceStatus.rawADC}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFD60A)
                    )
                    Text(
                        text = "voltage: ${String.format("%.3f", deviceStatus.voltage)} V",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFD60A)
                    )
                }
            }
            
            // 270° Speedometer Dial with Tick Marks
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                val progress = (currentPressureAnimated.value / maxPressure).coerceIn(0f, 1f)
                val targetProgress = (targetPressure / maxPressure).coerceIn(0f, 1f)

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 14.dp.toPx()
                    val innerStrokePx = 6.dp.toPx()
                    val outerRadius = (size.minDimension / 2f) - 4.dp.toPx()
                    val arcDiameter = (outerRadius - 12.dp.toPx()) * 2f
                    val arcOffset = Offset(
                        (size.width - arcDiameter) / 2f,
                        (size.height - arcDiameter) / 2f
                    )
                    val innerDiameter = arcDiameter - 22.dp.toPx()
                    val innerOffset = Offset(
                        (size.width - innerDiameter) / 2f,
                        (size.height - innerDiameter) / 2f
                    )

                    // Outer tick marks (270 degrees from 135° to 405°)
                    for (tick in 0..20) {
                        val isMajor = tick % 5 == 0
                        val angleRad = Math.toRadians(135.0 + tick * 13.5)
                        val tickLen = if (isMajor) 8.dp.toPx() else 5.dp.toPx()
                        val startR = outerRadius - tickLen
                        val sx = center.x + (startR * Math.cos(angleRad)).toFloat()
                        val sy = center.y + (startR * Math.sin(angleRad)).toFloat()
                        val ex = center.x + (outerRadius * Math.cos(angleRad)).toFloat()
                        val ey = center.y + (outerRadius * Math.sin(angleRad)).toFloat()
                        val tickColor = if (tick / 20f <= progress) activeAccent else Color.White.copy(alpha = if (isMajor) 0.35f else 0.15f)
                        drawLine(
                            color = tickColor,
                            start = Offset(sx, sy),
                            end = Offset(ex, ey),
                            strokeWidth = if (isMajor) 2.5.dp.toPx() else 1.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Background 270° track
                    drawArc(
                        color = Color(0xFF293548),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = arcOffset,
                        size = Size(arcDiameter, arcDiameter),
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )

                    // Target pressure inner 270° arc
                    drawArc(
                        color = Color(0xFFFFA726).copy(alpha = 0.55f),
                        startAngle = 135f,
                        sweepAngle = 270f * targetProgress,
                        useCenter = false,
                        topLeft = innerOffset,
                        size = Size(innerDiameter, innerDiameter),
                        style = Stroke(width = innerStrokePx, cap = StrokeCap.Round)
                    )

                    // Current pressure active 270° arc
                    drawArc(
                        color = activeAccent,
                        startAngle = 135f,
                        sweepAngle = 270f * progress,
                        useCenter = false,
                        topLeft = arcOffset,
                        size = Size(arcDiameter, arcDiameter),
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }

                // Target pressure text in the center
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.target_short),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = String.format("%.1f", targetPressure),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        color = Color(0xFFFFA726)
                    )
                    Text(
                        text = "PSI",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                }
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
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF171F2C)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.target_pressure).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8)
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
                    thumbColor = Color(0xFFFF7A00),
                    activeTrackColor = Color(0xFFFF7A00),
                    inactiveTrackColor = Color(0xFF293548)
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
                    containerColor = Color(0xFF1E293B)
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = stringResource(R.string.decrease),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Current target pressure
                Text(
                    text = stringResource(R.string.pressure_format, targetPressure),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFA726)
                )
                
                // Increase button
                FloatingActionButton(
                    onClick = { if (targetPressure < maxPressure) onTargetPressureChange(targetPressure + 0.5f) },
                    modifier = Modifier.size(48.dp),
                    containerColor = Color(0xFFFF7A00)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.increase),
                        tint = Color.White,
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
            .height(58.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isInflating) Color(0xFFE53935) else Color(0xFFFF7A00)
        ),
        shape = RoundedCornerShape(16.dp)
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
                text = (if (isInflating) stringResource(R.string.stop) else stringResource(R.string.start)).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
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
    val isDeflating = currentPressureAnimated != null && targetPressure < currentPressureAnimated.value
    val accentColor = if (isDeflating) Color(0xFFFF453A) else Color(0xFF00B4FF)
    
    AnimatedVisibility(
        visible = isInflating,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(accentColor.copy(alpha = 0.14f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size((12.dp * scale).coerceAtLeast(10.dp))
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            val remaining = currentPressureAnimated?.let { Math.abs(targetPressure - it.value) } ?: 0f
            Text(
                text = (if (isDeflating) stringResource(R.string.mode_deflating) else stringResource(R.string.mode_inflating)) +
                    stringResource(R.string.psi_remaining_short_format, remaining),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PresetButtons(
    predefinedPressures: List<Float>,
    selectedPressure: Float = 30f,
    onPresetSelected: (Float) -> Unit
) {
    if (predefinedPressures.isEmpty()) {
        return
    }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val chunkedPressures = predefinedPressures.chunked(4)
        
        chunkedPressures.forEach { rowPressures ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                repeat(4) { index ->
                    if (index < rowPressures.size) {
                        val pressure = rowPressures[index]
                        PresetPressureButton(
                            pressure = pressure,
                            isSelected = Math.abs(pressure - selectedPressure) < 0.05f,
                            onSelect = onPresetSelected,
                            modifier = Modifier.weight(1f).padding(horizontal = 2.dp).defaultMinSize(minHeight = 52.dp)
                        )
                    } else {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                                .defaultMinSize(minHeight = 52.dp)
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
    isSelected: Boolean = false,
    onSelect: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFFFF7A00) else Color(0xFF171F2C))
            .clickable { onSelect(pressure) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.preset_pressure_format, pressure.toInt()),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            color = if (isSelected) Color.White else Color(0xFFE2E8F0),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
} 
