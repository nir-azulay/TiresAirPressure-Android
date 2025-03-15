package com.example.tiresairpressuremanagement

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
import com.example.tiresairpressuremanagement.ui.navigation.NavGraph
import com.example.tiresairpressuremanagement.ui.theme.TiresAirPressureManagementTheme
import com.example.tiresairpressuremanagement.data.ble.BleManager
import com.example.tiresairpressuremanagement.data.ble.ConnectionState
import com.example.tiresairpressuremanagement.utils.PermissionManager
import androidx.compose.foundation.clickable
import com.example.tiresairpressuremanagement.domain.models.BleDevice
import com.example.tiresairpressuremanagement.domain.models.BleCommand
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import com.example.tiresairpressuremanagement.ui.screens.ConnectionManagementScreen
import com.example.tiresairpressuremanagement.ui.screens.MainScreen
import com.example.tiresairpressuremanagement.ui.screens.settings.SettingsScreen
import com.example.tiresairpressuremanagement.ui.screens.settings.SettingsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import android.os.Build
import android.Manifest
import android.util.Log
import com.example.tiresairpressuremanagement.ui.screens.main.MainViewModel
import com.example.tiresairpressuremanagement.ui.screens.AboutScreen
import com.example.tiresairpressuremanagement.ui.screens.SplashScreen
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.isSystemInDarkTheme

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private var showPermissionDialog by mutableStateOf(false)
    private lateinit var bleManager: BleManager
    private var isConnectingToSavedDevice = false
    
    // קבועים עבור מצב תצוגה
    companion object {
        private const val THEME_MODE_PREF = "theme_mode_preference"
        private const val THEME_MODE_KEY = "theme_mode"
        private const val THEME_MODE_SYSTEM = 0  // ברירת מחדל - לפי הגדרות המערכת
        private const val THEME_MODE_LIGHT = 1   // מצב יום
        private const val THEME_MODE_DARK = 2    // מצב לילה
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            showPermissionDialog = true
        } else {
            tryConnectToSavedDevice()
        }
    }

    private val requestNotificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, getString(R.string.notification_permission_granted))
        } else {
            Log.d(TAG, getString(R.string.notification_permission_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_TiresAirPressureManagement)
        super.onCreate(savedInstanceState)
        android.util.Log.d(TAG, getString(R.string.on_create_called))
        
        bleManager = BleManager(this)
        checkAndRequestPermissions()
        
        val sharedPreferences = getSharedPreferences(getString(R.string.app_settings), Context.MODE_PRIVATE)
        val isFirstLaunch = sharedPreferences.getBoolean(getString(R.string.is_first_launch), true)
        
        if (isFirstLaunch) {
            sharedPreferences.edit().putBoolean(getString(R.string.is_first_launch), false).apply()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermission()
            }
        }
        
        setContent {
            android.util.Log.d(TAG, getString(R.string.setting_content))
            
            // קריאת הגדרת מצב התצוגה
            val themePreferences = getSharedPreferences(THEME_MODE_PREF, Context.MODE_PRIVATE)
            val themeMode = themePreferences.getInt(THEME_MODE_KEY, THEME_MODE_SYSTEM)
            
            // קביעת מצב התצוגה בהתאם להגדרות
            val darkTheme = when (themeMode) {
                THEME_MODE_LIGHT -> false
                THEME_MODE_DARK -> true
                else -> isSystemInDarkTheme()
            }
            
            TiresAirPressureManagementTheme(darkTheme = darkTheme) {
                AppContent()
            }
        }
    }

    // Add method to handle configuration changes
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        android.util.Log.d(TAG, "Configuration changed: ${newConfig.orientation}")
        // No need to recreate the activity or reconnect BLE
        // The activity will not be destroyed and recreated
    }

    private fun tryConnectToSavedDevice() {
        val sharedPreferences = getSharedPreferences(getString(R.string.device_settings), Context.MODE_PRIVATE)
        val savedDeviceAddress = sharedPreferences.getString(getString(R.string.selected_device_address), null)
        if (savedDeviceAddress != null) {
            android.util.Log.d(TAG, getString(R.string.found_saved_device, savedDeviceAddress))
            isConnectingToSavedDevice = true
            bleManager.connectToSavedDevice()
        } else {
            android.util.Log.d(TAG, getString(R.string.no_saved_device))
            isConnectingToSavedDevice = false
        }
    }

    @Composable
    private fun AppContent() {
        // מצב המסך הנוכחי - start with SPLASH screen
        var currentScreen by remember { mutableStateOf(Screen.SPLASH) }
        
        // Track connection state
        val connectionState by bleManager.connectionState.collectAsState()
        
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
                    // Create the ViewModel with the existing BleManager instance
                    val viewModel: MainViewModel = viewModel(
                        factory = MainViewModel.provideFactory(application, bleManager)
                    )
                    MainScreen(
                        bleManager = bleManager,
                        onDisconnect = {
                            android.util.Log.d(TAG, "User initiated disconnect")
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
                        viewModel = viewModel
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
            
            if (showPermissionDialog) {
                PermissionDialog(
                    onDismiss = { showPermissionDialog = false },
                    onConfirm = {
                        showPermissionDialog = false
                        checkAndRequestPermissions()
                    }
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = PermissionManager.getMissingPermissions(this)
        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            // אם כל ההרשאות כבר קיימות, ננסה להתחבר למכשיר השמור
            tryConnectToSavedDevice()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.d("MainActivity", "Requesting notification permission for Android 13+")
            requestNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d(TAG, "onResume called")
        // ננסה להתחבר מחדש אם אנחנו מנותקים
        if (bleManager.connectionState.value == ConnectionState.Disconnected) {
            tryConnectToSavedDevice()
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
        text = { Text(stringResource(R.string.permissions_explanation)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.grant_permissions))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}