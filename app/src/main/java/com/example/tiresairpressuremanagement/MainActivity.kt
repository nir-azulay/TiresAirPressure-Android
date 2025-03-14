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
import java.util.Locale
import android.content.res.Configuration

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private var showPermissionDialog by mutableStateOf(false)
    private lateinit var bleManager: BleManager
    private var isConnectingToSavedDevice = false
    private var currentLocale: Locale? = null

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

    override fun attachBaseContext(newBase: Context) {
        val sharedPreferences = newBase.getSharedPreferences(
            newBase.getString(R.string.app_settings),
            Context.MODE_PRIVATE
        )
        val languageCode = sharedPreferences.getString(
            newBase.getString(R.string.language_preference),
            "he"
        )
        val locale = Locale(languageCode ?: "he")
        
        // עדכון ברירת המחדל של השפה
        Locale.setDefault(locale)
        
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        
        // יצירת קונטקסט חדש עם השפה החדשה
        val context = newBase.createConfigurationContext(config)
        
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_TiresAirPressureManagement)
        super.onCreate(savedInstanceState)
        android.util.Log.d(TAG, getString(R.string.on_create_called))
        
        // Load saved language preference
        val sharedPreferences = getSharedPreferences(getString(R.string.app_settings), Context.MODE_PRIVATE)
        val savedLanguage = sharedPreferences.getString(getString(R.string.language_preference), "he")
        currentLocale = Locale(savedLanguage ?: "he")
        
        // וידוא שהשפה מוגדרת כראוי
        Locale.setDefault(currentLocale!!)
        val config = resources.configuration
        config.setLocale(currentLocale)
        resources.updateConfiguration(config, resources.displayMetrics)
        
        bleManager = BleManager(this)
        checkAndRequestPermissions()
        
        val isFirstLaunch = sharedPreferences.getBoolean(getString(R.string.is_first_launch), true)
        
        if (isFirstLaunch) {
            sharedPreferences.edit().putBoolean(getString(R.string.is_first_launch), false).apply()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermission()
            }
        }
        
        setContent {
            android.util.Log.d(TAG, getString(R.string.setting_content))
            TiresAirPressureManagementTheme {
                AppContent()
            }
        }
    }

    private fun updateLocale(locale: Locale) {
        Log.d(TAG, "Updating locale to: ${locale.language}")
        
        // בדיקה אם השפה הנוכחית זהה לשפה החדשה
        if (currentLocale?.language == locale.language) {
            Log.d(TAG, "Locale is already set to ${locale.language}, skipping update")
            return
        }
        
        currentLocale = locale
        Locale.setDefault(locale)
        
        // עדכון הקונפיגורציה
        val config = resources.configuration
        config.setLocale(locale)
        
        // עדכון המשאבים באופן מיידי ויצירת קונטקסט חדש
        val context = createConfigurationContext(config)
        resources.updateConfiguration(config, resources.displayMetrics)
        
        // עדכון הקונטקסט הגלובלי
        baseContext.resources.updateConfiguration(config, baseContext.resources.displayMetrics)
        applicationContext.resources.updateConfiguration(config, applicationContext.resources.displayMetrics)
        
        // שמירת השפה בהעדפות
        val sharedPreferences = getSharedPreferences(getString(R.string.app_settings), Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putString(getString(R.string.language_preference), locale.language)
            .apply()
        
        // יצירה מחדש של האקטיביטי כדי להחיל את השינויים
        recreate()
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

    sealed class Screen {
        object Main : Screen()
        object Settings : Screen()
        object About : Screen()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AppContent() {
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }
        
        // גורם לקומפוזבל להתרנדר מחדש כאשר השפה משתנה
        val currentLanguage = remember { mutableStateOf(currentLocale?.language ?: "he") }
        
        // עדכון השפה בכל פעם שהקומפוזבל מתרנדר
        LaunchedEffect(currentLanguage.value) {
            Log.d(TAG, "Current language in AppContent: ${currentLanguage.value}")
        }
        
        when (currentScreen) {
            is Screen.Main -> {
                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModel.provideFactory(application, bleManager)
                )
                MainScreen(
                    bleManager = bleManager,
                    onDisconnect = {
                        android.util.Log.d(TAG, getString(R.string.user_initiated_disconnect))
                        bleManager.disconnect()
                    },
                    onSettingsClick = {
                        android.util.Log.d(TAG, getString(R.string.user_clicked_settings))
                        currentScreen = Screen.Settings
                    },
                    onAboutClick = {
                        android.util.Log.d(TAG, getString(R.string.user_clicked_about))
                        currentScreen = Screen.About
                    },
                    viewModel = viewModel
                )
            }
            is Screen.Settings -> {
                val viewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.provideFactory(application, bleManager)
                )
                SettingsScreen(
                    viewModel = viewModel,
                    onBackClick = {
                        android.util.Log.d(TAG, getString(R.string.user_clicked_back_from_settings))
                        currentScreen = Screen.Main
                    },
                    onLanguageChanged = { newLocale ->
                        updateLocale(newLocale)
                        currentLanguage.value = newLocale.language
                    }
                )
            }
            is Screen.About -> {
                AboutScreen(
                    onBackClick = {
                        android.util.Log.d(TAG, getString(R.string.user_clicked_back_from_about))
                        currentScreen = Screen.Main
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