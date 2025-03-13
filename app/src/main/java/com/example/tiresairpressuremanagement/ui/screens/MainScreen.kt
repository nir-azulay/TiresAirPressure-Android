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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tiresairpressuremanagement.data.ble.BleManager
import com.example.tiresairpressuremanagement.data.ble.ConnectionState
import com.example.tiresairpressuremanagement.domain.models.BleCommand
import com.example.tiresairpressuremanagement.ui.screens.main.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.tiresairpressuremanagement.R
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset

private const val TAG = "MainScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    bleManager: BleManager,
    onDisconnect: () -> Unit,
    onSettingsClick: () -> Unit,
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
        
        // If device status shows stopped mode, update inflation state
        deviceStatus?.let { status ->
            if (status.operationMode == 1) { // MODE_STOP
                isInflating = false
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
            title = { Text("לחץ היעד הושג") },
            text = { 
                Column {
                    Text("לחץ היעד הושג בהצלחה!")
                    Text("יעד: ${targetReachedInfo?.first} PSI")
                    Text("נוכחי: ${targetReachedInfo?.second} PSI")
                }
            },
            confirmButton = {
                Button(onClick = { 
                    android.util.Log.d(TAG, "Target reached dialog confirmed")
                    showTargetReachedDialog = false 
                }) {
                    Text("אישור")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ניהול לחץ אוויר בצמיגים") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "הגדרות")
                    }
                    IconButton(onClick = { 
                        isInflating = false
                        bleManager.disconnect()
                        onDisconnect()
                    }) {
                        Icon(Icons.Default.BluetoothDisabled, contentDescription = "נתק מכשיר")
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
            // Ford Raptor background image
            FordRaptorBackground()
            
            // Main content
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                val screenHeight = maxHeight
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Connection status indicator
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
                                    ConnectionState.Connected -> "מחובר"
                                    ConnectionState.Connecting -> "מתחבר..."
                                    ConnectionState.Disconnected -> "מנותק"
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
                    
                    // Pressure gauge - make it larger to match screenshot
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
                                    text = "לחץ נוכחי",
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
                                    text = "PSI",
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
                    
                    // Target pressure selector
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
                                text = "לחץ יעד",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Slider for target pressure
                            Slider(
                                value = targetPressure,
                                onValueChange = { targetPressure = it },
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
                                    onClick = { if (targetPressure > minPressure) targetPressure -= 0.5f },
                                    modifier = Modifier.size(48.dp),
                                    containerColor = Color(0xFFBBDEFB)
                                ) {
                                    Icon(
                                        Icons.Default.Remove,
                                        contentDescription = "הפחת",
                                        tint = Color.Black,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                
                                // Current target pressure
                                Text(
                                    text = String.format("%.1f PSI", targetPressure),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                
                                // Increase button
                                FloatingActionButton(
                                    onClick = { if (targetPressure < maxPressure) targetPressure += 0.5f },
                                    modifier = Modifier.size(48.dp),
                                    containerColor = Color(0xFFBBDEFB)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "הוסף",
                                        tint = Color.Black,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    // Start/Stop button
                    Button(
                        onClick = { 
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
                        },
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
                                    contentDescription = "עצור",
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "התחל",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isInflating) "עצור" else "התחל",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // Inflation status indicator
                    AnimatedVisibility(
                        visible = isInflating,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp * scale)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "מתנפח...",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // Preset pressure buttons in a card for better visibility
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
                                text = "לחצים מוגדרים מראש",
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
                                PresetPressureButton(pressure = 8f, onSelect = { targetPressure = it })
                                PresetPressureButton(pressure = 10f, onSelect = { targetPressure = it })
                                PresetPressureButton(pressure = 12f, onSelect = { targetPressure = it })
                                PresetPressureButton(pressure = 16f, onSelect = { targetPressure = it })
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Second row of preset buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                PresetPressureButton(pressure = 20f, onSelect = { targetPressure = it })
                                PresetPressureButton(pressure = 30f, onSelect = { targetPressure = it })
                                PresetPressureButton(pressure = 38f, onSelect = { targetPressure = it })
                                PresetPressureButton(pressure = 40f, onSelect = { targetPressure = it })
                            }
                        }
                    }
                }
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

@Composable
fun FordRaptorBackground() {
    // Create a simple truck shape using Canvas instead of loading an image
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .alpha(0.15f) // Increased opacity for better visibility
    ) {
        val width = size.width
        val height = size.height
        
        // Draw a simple truck outline
        drawTruckOutline(width, height)
    }
}

private fun DrawScope.drawTruckOutline(width: Float, height: Float) {
    // Scale factors
    val scaleX = width / 800f
    val scaleY = height / 400f
    
    // Truck body
    val bodyPath = Path().apply {
        moveTo(150f * scaleX, 200f * scaleY)
        lineTo(200f * scaleX, 150f * scaleY)
        lineTo(500f * scaleX, 150f * scaleY)
        lineTo(550f * scaleX, 200f * scaleY)
        lineTo(600f * scaleX, 200f * scaleY)
        lineTo(650f * scaleX, 250f * scaleY)
        lineTo(700f * scaleX, 250f * scaleY)
        lineTo(700f * scaleX, 300f * scaleY)
        lineTo(100f * scaleX, 300f * scaleY)
        lineTo(100f * scaleX, 250f * scaleY)
        close()
    }
    
    // Cabin
    val cabinPath = Path().apply {
        moveTo(300f * scaleX, 150f * scaleY)
        lineTo(350f * scaleX, 100f * scaleY)
        lineTo(450f * scaleX, 100f * scaleY)
        lineTo(500f * scaleX, 150f * scaleY)
    }
    
    // Windows
    val windowsPath = Path().apply {
        moveTo(320f * scaleX, 145f * scaleY)
        lineTo(360f * scaleX, 110f * scaleY)
        lineTo(440f * scaleX, 110f * scaleY)
        lineTo(480f * scaleX, 145f * scaleY)
    }
    
    // Wheels
    val frontWheelPath = Path().apply {
        addOval(Rect(
            left = 170f * scaleX,
            top = 270f * scaleY,
            right = 200f * scaleX,
            bottom = 300f * scaleY
        ))
    }
    
    val rearWheelPath = Path().apply {
        addOval(Rect(
            left = 570f * scaleX,
            top = 270f * scaleY,
            right = 600f * scaleX,
            bottom = 300f * scaleY
        ))
    }
    
    // Raptor-specific details
    
    // Lifted suspension
    val suspensionPath = Path().apply {
        // Front suspension
        moveTo(170f * scaleX, 280f * scaleY)
        lineTo(150f * scaleX, 250f * scaleY)
        
        moveTo(200f * scaleX, 280f * scaleY)
        lineTo(220f * scaleX, 250f * scaleY)
        
        // Rear suspension
        moveTo(570f * scaleX, 280f * scaleY)
        lineTo(550f * scaleX, 250f * scaleY)
        
        moveTo(600f * scaleX, 280f * scaleY)
        lineTo(620f * scaleX, 250f * scaleY)
    }
    
    // Raptor grille
    val grillePath = Path().apply {
        // Grille outline
        addRect(Rect(
            left = 120f * scaleX,
            top = 220f * scaleY,
            right = 180f * scaleX,
            bottom = 250f * scaleY
        ))
        
        // Horizontal grille lines
        for (i in 0..3) {
            val y = 225f * scaleY + i * 6f * scaleY
            moveTo(125f * scaleX, y)
            lineTo(175f * scaleX, y)
        }
    }
    
    // Draw all paths
    drawPath(
        path = bodyPath,
        color = Color.Black,
        style = Stroke(width = 3f)
    )
    
    drawPath(
        path = cabinPath,
        color = Color.Black,
        style = Stroke(width = 3f)
    )
    
    drawPath(
        path = windowsPath,
        color = Color.Black,
        style = Stroke(width = 2f)
    )
    
    drawPath(
        path = frontWheelPath,
        color = Color.Black,
        style = Stroke(width = 3f)
    )
    
    drawPath(
        path = rearWheelPath,
        color = Color.Black,
        style = Stroke(width = 3f)
    )
    
    // Draw Raptor-specific details
    drawPath(
        path = suspensionPath,
        color = Color.Black,
        style = Stroke(width = 2f)
    )
    
    drawPath(
        path = grillePath,
        color = Color.Black,
        style = Stroke(width = 2f)
    )
    
    // Add some simple lines to represent "RAPTOR" text
    val textY = 180f * scaleY
    val textStartX = 380f * scaleX
    val textWidth = 100f * scaleX
    
    drawLine(
        start = Offset(textStartX, textY),
        end = Offset(textStartX + textWidth, textY),
        color = Color.Black,
        strokeWidth = 3f
    )
} 