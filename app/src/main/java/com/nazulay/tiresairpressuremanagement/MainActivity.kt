package com.nazulay.tiresairpressuremanagement

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.nazulay.tiresairpressuremanagement.ui.navigation.NavGraph
import com.nazulay.tiresairpressuremanagement.ui.theme.TiresAirPressureManagementTheme
import com.nazulay.tiresairpressuremanagement.data.ble.BleManager
import com.nazulay.tiresairpressuremanagement.data.ble.ConnectionState
import com.nazulay.tiresairpressuremanagement.utils.PermissionManager
import androidx.compose.foundation.clickable
import com.nazulay.tiresairpressuremanagement.domain.models.BleDevice
import com.nazulay.tiresairpressuremanagement.domain.models.BleCommand
import android.content.Context
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import com.nazulay.tiresairpressuremanagement.ui.screens.ConnectionManagementScreen
import com.nazulay.tiresairpressuremanagement.ui.screens.MainScreen
import com.nazulay.tiresairpressuremanagement.ui.screens.settings.SettingsScreen
import com.nazulay.tiresairpressuremanagement.ui.screens.settings.SettingsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import android.os.Build
import android.Manifest
import android.util.Log
import com.nazulay.tiresairpressuremanagement.ui.screens.main.MainViewModel
import com.nazulay.tiresairpressuremanagement.ui.screens.AboutScreen
import com.nazulay.tiresairpressuremanagement.ui.screens.SplashScreen
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.net.Uri
import android.content.res.Configuration
import kotlinx.coroutines.launch
import com.nazulay.tiresairpressuremanagement.data.repository.BleRepository
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import java.util.*
import com.nazulay.tiresairpressuremanagement.utils.LocaleHelper
import com.nazulay.tiresairpressuremanagement.utils.ActivityProvider
import android.provider.Settings
import android.app.Activity
import androidx.compose.ui.text.font.FontWeight

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private var showPermissionDialog by mutableStateOf(false)
    private var showPermissionSettingsDialog by mutableStateOf(false)
    private var showBluetoothRequiredDialog by mutableStateOf(false)
    private lateinit var bleManager: BleManager
    private var isConnectingToSavedDevice = false
    private lateinit var bleRepository: BleRepository
    
    // Constants for display mode
    companion object {
        private const val THEME_MODE_PREF = "theme_mode_preference"
        private const val THEME_MODE_KEY = "theme_mode"
        private const val THEME_MODE_SYSTEM = 0
        private const val THEME_MODE_LIGHT = 1
        private const val THEME_MODE_DARK = 2
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        Log.d(TAG, "Permission results: $permissions")
        
        if (!allGranted) {
            // Check which permissions were denied
            val deniedPermissions = permissions.filter { !it.value }.keys
            Log.d(TAG, "Denied permissions: $deniedPermissions")
            
            // Check if any permission is permanently denied
            val permanentlyDenied = deniedPermissions.any { permission ->
                !shouldShowRequestPermissionRationale(permission)
            }
            
            if (permanentlyDenied) {
                // Show settings dialog if any permission is permanently denied
                showPermissionSettingsDialog = true
            } else {
                // Show regular permission dialog for temporary denial
                showPermissionDialog = true
            }
        } else {
            Log.d(TAG, "All permissions granted")
            tryConnectToSavedDevice()
        }
    }

    private val requestNotificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Notification permission granted")
        } else {
            Log.d(TAG, "Notification permission denied")
            // Don't show settings dialog for notification permission
            // as it's not critical for app functionality
        }
    }

    override fun attachBaseContext(newBase: Context) {
        // Use LocaleHelper to apply the saved language
        val languageCode = LocaleHelper.getSelectedLanguage(newBase)
        val localeUpdatedContext = LocaleHelper.setLocale(newBase, languageCode)
        super.attachBaseContext(localeUpdatedContext)
    }

    // Method to update language from within the app
    fun updateLanguage(languageCode: String) {
        LocaleHelper.setLocale(this, languageCode)
        // Recreate the activity to apply the language change
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize BLE Repository first
        bleRepository = BleRepository(applicationContext)
        
        // Initialize BLE Manager with repository
        bleManager = BleManager(applicationContext, bleRepository)
        
        // Check Bluetooth enabled
        if (!PermissionManager.isBluetoothEnabled(this)) {
            Log.d(TAG, "Bluetooth is disabled, requesting enable")
            PermissionManager.requestEnableBluetooth(this)
        }
        
        // Try to connect to saved devices when app starts
        lifecycleScope.launch {
            Log.d(TAG, "Attempting to connect to saved devices")
            val savedDevices = bleRepository.getSavedDevices()
            Log.d(TAG, "Found ${savedDevices.size} saved devices")
            
            if (savedDevices.isNotEmpty()) {
                isConnectingToSavedDevice = true
                bleRepository.connectToSavedDevices()
            }
        }

        setContent {
            TiresAirPressureManagementTheme {
                AppContent()
            }
        }
    }

    // Add method to handle configuration changes
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "Configuration changed")
    }

    private fun tryConnectToSavedDevice() {
        // Use BleRepository to get saved devices
        val savedDevices = bleRepository.getSavedDevices()
        
        if (savedDevices.isNotEmpty()) {
            Log.d(TAG, "Found saved devices, attempting to connect")
            isConnectingToSavedDevice = true
            // Use BleManager to connect to saved device
            bleManager.connectToSavedDevice()
        } else {
            Log.d(TAG, getString(R.string.no_saved_device))
            isConnectingToSavedDevice = false
        }
    }

    @Composable
    private fun AppContent() {
        var currentScreen by remember { mutableStateOf(Screen.SPLASH) }
        
        // Check permissions when app starts
        LaunchedEffect(Unit) {
            checkAndRequestPermissions()
        }
        
        // Observe connection state
        val connectionState by bleManager.connectionState.collectAsState()
        
        // Show permission settings dialog if needed
        LaunchedEffect(connectionState) {
            if (connectionState == ConnectionState.PermissionDenied) {
                showPermissionSettingsDialog = true
            }
        }
        
        // Effect to handle connection state changes
        LaunchedEffect(connectionState) {
            if (isConnectingToSavedDevice) {
                when (connectionState) {
                    ConnectionState.Connected -> {
                        // If connected successfully, go to main screen
                        currentScreen = Screen.MAIN
                        isConnectingToSavedDevice = false
                    }
                    ConnectionState.Disconnected -> {
                        // If connection failed and we were trying to connect, go to connection screen
                        if (currentScreen == Screen.SPLASH) {
                            // Only change if we're still on splash screen
                            delay(3000) // Wait for splash screen animation
                            currentScreen = Screen.CONNECTION
                            isConnectingToSavedDevice = false
                        }
                    }
                    else -> {} // Do nothing for other states
                }
            }
        }
        
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            when (currentScreen) {
                Screen.SPLASH -> {
                    SplashScreen(
                        onTimeout = {
                            // If we're still connecting, stay on splash screen
                            if (connectionState != ConnectionState.Connecting) {
                                // If connected, go to main, otherwise go to connection screen
                                currentScreen = if (connectionState == ConnectionState.Connected) {
                                    Screen.MAIN
                                } else {
                                    Screen.CONNECTION
                                }
                            }
                        },
                        connectionInProgress = connectionState == ConnectionState.Connecting
                    )
                }
                
                Screen.MAIN -> {
                    android.util.Log.d(TAG, "Displaying MainScreen")
                    MainScreen(
                        bleManager = bleManager,
                        onDisconnect = {
                            android.util.Log.d(TAG, "User clicked disconnect")
                            bleManager.disconnect()
                            currentScreen = Screen.CONNECTION
                        },
                        onSettingsClick = {
                            android.util.Log.d(TAG, "User clicked settings")
                            currentScreen = Screen.SETTINGS
                        },
                        onAboutClick = {
                            android.util.Log.d(TAG, "User clicked about")
                            currentScreen = Screen.ABOUT
                        },
                        onExitClick = {
                            android.util.Log.d(TAG, "User clicked exit")
                            finish()
                        }
                    )
                }
                Screen.CONNECTION -> {
                    // אם יש חיבור פעיל, עבור למסך הראשי
                    LaunchedEffect(Unit) {
                        if (bleManager.connectionState.value == ConnectionState.Connected) {
                            currentScreen = Screen.MAIN
                        }
                    }
                    
                    ConnectionManagementScreen(
                        bleManager = bleManager,
                        onDeviceConnected = {
                            android.util.Log.d(TAG, "Device connected callback in MainActivity")
                            currentScreen = Screen.MAIN
                        }
                    )
                }
                Screen.SETTINGS -> {
                    android.util.Log.d(TAG, "Displaying SettingsScreen")
                    // Create the ViewModel with the existing BleManager instance
                    val viewModel: SettingsViewModel = viewModel(
                        factory = SettingsViewModel.provideFactory(application, bleManager)
                    )
                    // Use the correct SettingsScreen with ViewModel
                    SettingsScreen(
                        viewModel = viewModel,
                        onBackClick = {
                            android.util.Log.d(TAG, "User clicked back from settings")
                            currentScreen = Screen.MAIN
                            
                            // After returning to main screen, implement a delayed reconnection attempt
                            // if the device isn't connected yet
                            lifecycleScope.launch {
                                // First try to reconnect immediately
                                tryConnectToSavedDevice()
                                
                                // Then check connection after 10 seconds
                                delay(10000)
                                if (bleManager.connectionState.value != ConnectionState.Connected) {
                                    android.util.Log.d(TAG, "Device still not connected after 10 seconds, trying reconnection again")
                                    tryConnectToSavedDevice()
                                }
                            }
                        },
                        forceReconnect = {
                            android.util.Log.d(TAG, "Force reconnect callback triggered from SettingsScreen")
                            if (bleManager.connectionState.value == ConnectionState.Disconnected) {
                                android.util.Log.d(TAG, "Device is disconnected, calling tryConnectToSavedDevice")
                                tryConnectToSavedDevice()
                                
                                // Add a second reconnection attempt after 10 seconds if the first attempt fails
                                lifecycleScope.launch {
                                    delay(10000)
                                    if (bleManager.connectionState.value != ConnectionState.Connected) {
                                        android.util.Log.d(TAG, "Device still not connected after 10 seconds, trying reconnection again")
                                        tryConnectToSavedDevice()
                                    }
                                }
                            } else {
                                android.util.Log.d(TAG, "Device is not disconnected, no need to reconnect")
                            }
                        }
                    )
                }
                Screen.ABOUT -> {
                    android.util.Log.d(TAG, "Displaying AboutScreen")
                    AboutScreen(
                        onBackClick = {
                            android.util.Log.d(TAG, "User clicked back from about")
                            currentScreen = Screen.MAIN
                        }
                    )
                }
            }
            
            // Add the permission settings dialog
            if (showPermissionSettingsDialog) {
                PermissionSettingsDialog(
                    onDismiss = { showPermissionSettingsDialog = false },
                    onOpenSettings = {
                        showPermissionSettingsDialog = false
                        openAppSettings()
                    }
                )
            }
            
            if (showPermissionDialog) {
                PermissionDialog(
                    onDismiss = { showPermissionDialog = false },
                    onConfirm = {
                        showPermissionDialog = false
                        checkAndRequestPermissions()
                    }
                )
            }
            
            if (showBluetoothRequiredDialog) {
                BluetoothRequiredDialog(
                    onDismiss = { showBluetoothRequiredDialog = false },
                    onOpenSettings = {
                        showBluetoothRequiredDialog = false
                        openBluetoothSettings()
                    }
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        Log.d(TAG, "Checking and requesting necessary permissions")
        
        // Check if Bluetooth is enabled first
        if (!PermissionManager.isBluetoothEnabled(this)) {
            Log.d(TAG, "Bluetooth is not enabled, requesting to enable")
            PermissionManager.requestEnableBluetooth(this)
            return
        }
        
        // Get all missing permissions
        val missingPermissions = PermissionManager.getMissingPermissions(this).toTypedArray()
        
        if (missingPermissions.isNotEmpty()) {
            Log.d(TAG, "Missing permissions: ${missingPermissions.joinToString()}")
            
            // Pre-process Android 12+ special permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val containsScanPermission = missingPermissions.contains(Manifest.permission.BLUETOOTH_SCAN)
                val containsConnectPermission = missingPermissions.contains(Manifest.permission.BLUETOOTH_CONNECT)
                
                if (containsScanPermission || containsConnectPermission) {
                    Log.d(TAG, "Contains Android 12+ Bluetooth permissions")
                }
            }
            
            // Show rationale if needed before requesting permissions
            val shouldShowRationale = missingPermissions.any { permission ->
                shouldShowRequestPermissionRationale(permission)
            }
            
            if (shouldShowRationale) {
                Log.d(TAG, "Should show permission rationale")
                showPermissionDialog = true
            } else {
                Log.d(TAG, "Requesting permissions directly")
                // Use the activity result launcher to request permissions
                requestPermissionLauncher.launch(missingPermissions)
            }
        } else {
            Log.d(TAG, "All required permissions are granted")
            tryConnectToSavedDevice()
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // For Android 12 and below, notification permission is not required
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
        // Try to reconnect if we're disconnected and have saved devices
        lifecycleScope.launch {
            if (bleManager.connectionState.value == ConnectionState.Disconnected) {
                val savedDevices = bleRepository.getSavedDevices()
                if (savedDevices.isNotEmpty()) {
                    Log.d(TAG, "Found saved devices in onResume, attempting to connect")
                    bleRepository.connectToSavedDevices()
                }
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        android.util.Log.d(TAG, "onPause called")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d(TAG, "onDestroy called")
        bleManager.disconnect()
    }

    // Method to check if we have all WiFi scanning permissions
    fun hasWifiScanningPermissions(): Boolean {
        val wifiPermissions = listOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        
        return wifiPermissions.all { permission ->
            checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun openAppSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    // Handle Bluetooth enable result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == PermissionManager.REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "Bluetooth enabled successfully")
                // Proceed with connection
                tryConnectToSavedDevice()
            } else {
                Log.d(TAG, "Bluetooth enable request denied")
                // Show dialog explaining Bluetooth is required
                showBluetoothRequiredDialog()
            }
        }
    }
    
    // Add new dialog for Bluetooth required
    @Composable
    private fun BluetoothRequiredDialog(
        onDismiss: () -> Unit,
        onOpenSettings: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.bluetooth_required)) },
            text = { Text(stringResource(R.string.bluetooth_required_explanation)) },
            confirmButton = {
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
    
    // Show dialog explaining Bluetooth is required
    private fun showBluetoothRequiredDialog() {
        showBluetoothRequiredDialog = true
    }
    
    // Open Bluetooth settings
    private fun openBluetoothSettings() {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Bluetooth settings: ${e.message}")
        }
    }
}

enum class Screen {
    SPLASH,
    MAIN,
    CONNECTION,
    SETTINGS,
    ABOUT
}

@Composable
private fun PermissionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.permissions_required)) },
        text = { 
            Column {
                Text(stringResource(R.string.bluetooth_permissions_explanation))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.permissions_required_for_scanning),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.grant_permissions))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.not_now))
            }
        }
    )
}

@Composable
private fun PermissionSettingsDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.permissions_required)) },
        text = { Text(stringResource(R.string.open_settings_permission_explanation)) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

