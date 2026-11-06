package com.nazulay.tiresairpressuremanagement.ui.screens.settings

import android.content.Context
import android.graphics.Rect as AndroidRect
import android.net.wifi.WifiManager
import android.util.Log
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.SignalWifiStatusbar4Bar
import androidx.compose.material.icons.filled.SignalWifiStatusbarConnectedNoInternet4
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nazulay.tiresairpressuremanagement.R
import com.nazulay.tiresairpressuremanagement.data.ble.ConnectionState
import com.nazulay.tiresairpressuremanagement.data.ota.FirmwareUpgradeManager
import com.nazulay.tiresairpressuremanagement.domain.models.AppFontSize
import com.nazulay.tiresairpressuremanagement.domain.models.DeviceSettings
import com.nazulay.tiresairpressuremanagement.utils.LocaleHelper
import kotlinx.coroutines.delay
import java.util.Locale

// Palette mirroring iOS AppColors so both apps look the same.
private val SettingsBg = Color(0xFF0D1117)
private val SettingsCardBg = Color(0xFF171F2C)
private val SettingsCardBorder = Color(0xFF293548)
private val SettingsBarBg = Color(0xFF131A24)
private val BrandOrange = Color(0xFFFF7A00)
private val BadgeAmber = Color(0xFFFFB300)
private val SecondaryText = Color(0xFF94A3B8)
private val SwitchGreen = Color(0xFF34C759)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    forceReconnect: () -> Unit = {},
    onDeviceManagementClick: () -> Unit = {},
    onFirmwareUpgradeClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val upgradeManager = remember { FirmwareUpgradeManager.getInstance(context) }
    val isUpdateAvailable by upgradeManager.isUpdateAvailable.collectAsState()
    var isSaving by remember { mutableStateOf(false) }
    val deviceSettings by viewModel.deviceSettings.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val debugMode by viewModel.debugMode.collectAsState()
    val networks by viewModel.availableNetworks.collectAsState()

    var showResetDialog by remember { mutableStateOf(false) }
    var showDeviceConfig by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val density = LocalDensity.current
    val editorBounds = remember { FocusedEditorBounds() }
    val scaffoldOrigin = remember { mutableStateOf(Offset.Zero) }
    var keyboardHeightPx by remember { mutableIntStateOf(0) }
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val visible = AndroidRect()
            view.getWindowVisibleDisplayFrame(visible)
            val keypad = view.rootView.height - visible.bottom
            keyboardHeightPx = if (keypad > view.rootView.height * 0.12f) keypad else 0
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    val imeInsetPx = WindowInsets.ime.getBottom(density)
    val keyboardPad = with(density) { maxOf(keyboardHeightPx, imeInsetPx).toDp() }

    fun hideKeyboard() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    // Navigate back (to main, for reconnection) once a device-settings save completes.
    LaunchedEffect(isSaving) {
        if (isSaving) {
            delay(5000)
            Log.d("SettingsScreen", "Settings saved, returning to main screen for reconnection")
            onBackClick()
        }
    }

    // Request device settings when connected but none loaded yet.
    LaunchedEffect(connectionState, deviceSettings) {
        if (connectionState == ConnectionState.Connected && deviceSettings == null) {
            viewModel.forceRefreshConnectionState()
        }
    }

    val inDeviceConfig = showDeviceConfig && deviceSettings != null

    CompositionLocalProvider(LocalFocusedEditorBounds provides editorBounds) {
        Scaffold(
            modifier = Modifier
                .onGloballyPositioned { scaffoldOrigin.value = it.positionInRoot() }
                .padding(bottom = keyboardPad)
                .pointerInput(editorBounds) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val rootPoint = scaffoldOrigin.value + down.position
                        val field = editorBounds.rootRect
                        if (field != null && !field.contains(rootPoint)) {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(view.windowToken, 0)
                        }
                    }
                },
            containerColor = SettingsBg,
            topBar = {
                if (inDeviceConfig) {
                    TopAppBar(
                        title = { Text(text = stringResource(R.string.device_configuration), fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
                        navigationIcon = {
                            TextButton(onClick = {
                                hideKeyboard()
                                if (hasUnsavedChanges) showExitDialog = true else showDeviceConfig = false
                            }) {
                                Text(text = stringResource(R.string.cancel), color = Color.White)
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = {
                                    hideKeyboard()
                                    // Mirror iOS: hotspot password shorter than 8 chars falls back to the default.
                                    val s = deviceSettings
                                    if (s != null && s.accessPointPassword.length < 8) {
                                        viewModel.updateDeviceSettings(accessPointPassword = "12345678")
                                    }
                                    isSaving = true
                                    viewModel.saveDeviceSettingsAndReconnect(forceReconnect)
                                },
                                enabled = !isSaving
                            ) {
                                Text(
                                    text = stringResource(R.string.save),
                                    color = BrandOrange,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = SettingsBarBg,
                            titleContentColor = Color.White
                        )
                    )
                } else {
                    TopAppBar(
                        title = { Text(text = stringResource(R.string.settings), fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = SettingsBarBg,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White
                        )
                    )
                }
            },
            bottomBar = {
                if (!inDeviceConfig) {
                    // Bottom Save/Discard bar — same layout as iOS SettingsView.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SettingsBarBg)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (hasUnsavedChanges) {
                            Text(
                                text = stringResource(R.string.unsaved_changes_message),
                                style = MaterialTheme.typography.bodySmall,
                                color = BadgeAmber,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    hideKeyboard()
                                    // App-level settings apply immediately on Android;
                                    // Save also flushes any pending device edits, then closes.
                                    if (hasUnsavedChanges && connectionState == ConnectionState.Connected) {
                                        isSaving = true
                                        viewModel.saveDeviceSettingsAndReconnect(forceReconnect)
                                    } else {
                                        onBackClick()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandOrange)
                            ) {
                                Text(text = stringResource(R.string.save_settings), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    hideKeyboard()
                                    if (hasUnsavedChanges) viewModel.discardChanges()
                                    onBackClick()
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, SettingsCardBorder),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = SettingsCardBg)
                            ) {
                                Text(
                                    text = if (hasUnsavedChanges) stringResource(R.string.discard) else stringResource(R.string.close),
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            if (inDeviceConfig) {
                DeviceConfigurationPage(
                    viewModel = viewModel,
                    settings = deviceSettings!!,
                    networks = networks,
                    isUpdateAvailable = isUpdateAvailable,
                    hasUnsavedChanges = hasUnsavedChanges,
                    onFirmwareUpgradeClick = onFirmwareUpgradeClick,
                    modifier = Modifier.padding(paddingValues)
                )
            } else {
                SettingsRootPage(
                    viewModel = viewModel,
                    connectionState = connectionState,
                    deviceSettings = deviceSettings,
                    isUpdateAvailable = isUpdateAvailable,
                    currentLanguage = currentLanguage,
                    fontSizeScale = appSettings.fontSizeScale,
                    debugMode = debugMode,
                    simulationMode = deviceSettings?.simulationMode ?: appSettings.simulationMode,
                    presetPressures = appSettings.predefinedPressures,
                    minPressureCount = appSettings.minPressureCount,
                    maxPressureCount = appSettings.maxPressureCount,
                    onShowReset = { showResetDialog = true },
                    onDeviceManagementClick = onDeviceManagementClick,
                    onFirmwareUpgradeClick = onFirmwareUpgradeClick,
                    onConfigureDeviceClick = { showDeviceConfig = true },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }

    // Saving & reconnecting dialog (mirrors the iOS saving overlay).
    if (isSaving) {
        var reconnectingState by remember { mutableIntStateOf(0) }
        LaunchedEffect(isSaving) {
            reconnectingState = 0
            delay(2500)
            if (isSaving) reconnectingState = 1
        }
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.saving_settings)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
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
                    if (reconnectingState == 1) {
                        val remainingTime = remember { mutableIntStateOf(3) }
                        LaunchedEffect(reconnectingState) {
                            for (i in 3 downTo 1) {
                                remainingTime.intValue = i
                                delay(1000)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.returning_to_main_in_seconds, remainingTime.intValue),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            },
            confirmButton = { }
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.exit_without_save)) },
            text = { Text(stringResource(R.string.unsaved_changes_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.discardChanges()
                    showExitDialog = false
                    showDeviceConfig = false
                }) {
                    Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset_to_defaults)) },
            text = { Text(stringResource(R.string.reset_defaults_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetAppSettings()
                    showResetDialog = false
                }) {
                    Text(stringResource(R.string.reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// MARK: - Root Settings page (mirrors iOS SettingsView)

@Composable
private fun SettingsRootPage(
    viewModel: SettingsViewModel,
    connectionState: ConnectionState,
    deviceSettings: DeviceSettings?,
    isUpdateAvailable: Boolean,
    currentLanguage: String,
    fontSizeScale: AppFontSize,
    debugMode: Boolean,
    simulationMode: Boolean,
    presetPressures: List<Float>,
    minPressureCount: Int,
    maxPressureCount: Int,
    onShowReset: () -> Unit,
    onDeviceManagementClick: () -> Unit,
    onFirmwareUpgradeClick: () -> Unit,
    onConfigureDeviceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pickerDialog by remember { mutableStateOf<String?>(null) }
    var showAddPressure by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SettingsBg)
            .padding(horizontal = 16.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // App Settings
        item {
            SectionHeader(stringResource(R.string.app_settings))
            GroupedSection {
                PickerRow(
                    label = stringResource(R.string.language),
                    value = languageDisplayName(currentLanguage),
                    onClick = { pickerDialog = "language" }
                )
                SectionDivider()
                PickerRow(
                    label = stringResource(R.string.font_size),
                    value = stringResource(fontSizeLabel(fontSizeScale)),
                    onClick = { pickerDialog = "font" }
                )
                SectionDivider()
                SwitchRow(
                    label = stringResource(R.string.debug_mode),
                    checked = debugMode,
                    onCheckedChange = { viewModel.setDebugMode(it) }
                )
                SectionDivider()
                SwitchRow(
                    label = stringResource(R.string.simulation_mode),
                    checked = simulationMode,
                    onCheckedChange = { viewModel.updateSimulationMode(it) }
                )
            }
        }

        // Preset Pressures
        item {
            SectionHeader(stringResource(R.string.preset_pressures_title))
            GroupedSection {
                presetPressures.forEachIndexed { index, pressure ->
                    if (index > 0) SectionDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${pressure.toInt()} PSI",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (presetPressures.size > minPressureCount) {
                            IconButton(onClick = { viewModel.removePressure(index) }) {
                                Icon(
                                    Icons.Default.RemoveCircle,
                                    contentDescription = stringResource(R.string.remove_pressure),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                if (presetPressures.size < maxPressureCount) {
                    if (presetPressures.isNotEmpty()) SectionDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAddPressure = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AddCircle,
                            contentDescription = null,
                            tint = Color(0xFF30D158),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = stringResource(R.string.add_pressure),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                SectionDivider()
                Text(
                    text = stringResource(R.string.presets_count, presetPressures.size, maxPressureCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Device Management
        item {
            SectionHeader(stringResource(R.string.device_management))
            GroupedSection {
                NavRow(
                    icon = Icons.Default.Devices,
                    title = stringResource(R.string.manage_paired_devices),
                    subtitle = stringResource(R.string.manage_paired_devices_description),
                    onClick = onDeviceManagementClick
                )
            }
        }

        // Device Settings (links; the fields live on the Device Configuration page, like iOS)
        item {
            SectionHeader(stringResource(R.string.device_settings))
            if (connectionState == ConnectionState.Connected) {
                GroupedSection {
                    NavRow(
                        icon = Icons.Default.Settings,
                        title = stringResource(R.string.configure_device_settings),
                        enabled = deviceSettings != null,
                        onClick = onConfigureDeviceClick
                    )
                    SectionDivider()
                    NavRow(
                        icon = Icons.Default.SystemUpdateAlt,
                        title = stringResource(R.string.firmware_upgrade_title),
                        showBadge = isUpdateAvailable,
                        onClick = onFirmwareUpgradeClick
                    )
                    SectionDivider()
                    ActionRow(
                        icon = Icons.Default.Refresh,
                        title = stringResource(R.string.request_settings),
                        onClick = { viewModel.requestDeviceSettings() }
                    )
                }
                if (deviceSettings == null) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.loading_preset_pressures),
                            color = SecondaryText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    LaunchedEffect(Unit) {
                        while (true) {
                            viewModel.requestDeviceSettings()
                            delay(3000)
                        }
                    }
                }
            } else {
                GroupedSection {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
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
                            color = SecondaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Button(
                            onClick = { viewModel.connectToSavedDeviceIfNeeded() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandOrange)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(text = stringResource(R.string.try_reconnect), color = Color.White)
                        }
                    }
                }
            }
        }

        // Reset
        item {
            Spacer(modifier = Modifier.height(8.dp))
            GroupedSection {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShowReset() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Text(
                        text = stringResource(R.string.reset_to_defaults),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }

    when (pickerDialog) {
        "language" -> {
            val languages = remember { LocaleHelper.getSupportedLanguages() }
            OptionPickerDialog(
                title = stringResource(R.string.language),
                options = languages.keys.toList(),
                label = { code ->
                    if (code == LocaleHelper.LANGUAGE_SYSTEM) stringResource(R.string.system_default)
                    else languages[code] ?: code
                },
                isSelected = { it == currentLanguage },
                onSelect = {
                    viewModel.updateLanguage(it)
                    pickerDialog = null
                },
                onDismiss = { pickerDialog = null }
            )
        }
        "font" -> {
            OptionPickerDialog(
                title = stringResource(R.string.font_size),
                options = AppFontSize.entries,
                label = { stringResource(fontSizeLabel(it)) },
                isSelected = { it == fontSizeScale },
                onSelect = {
                    viewModel.updateFontSizeScale(it)
                    pickerDialog = null
                },
                onDismiss = { pickerDialog = null }
            )
        }
    }

    if (showAddPressure) {
        AddPressureDialog(
            onAdd = { value ->
                val updated = (presetPressures + value).sorted()
                viewModel.updatePredefinedPressures(updated)
                showAddPressure = false
            },
            onDismiss = { showAddPressure = false }
        )
    }
}

private fun fontSizeLabel(size: AppFontSize): Int = when (size) {
    AppFontSize.SMALLEST -> R.string.font_size_smallest
    AppFontSize.SMALLER -> R.string.font_size_smaller
    AppFontSize.DEFAULT -> R.string.font_size_default
    AppFontSize.BIGGER -> R.string.font_size_bigger
    AppFontSize.BIGGEST -> R.string.font_size_biggest
}

@Composable
private fun languageDisplayName(code: String): String {
    if (code == LocaleHelper.LANGUAGE_SYSTEM) return stringResource(R.string.system_default)
    val languages = remember { LocaleHelper.getSupportedLanguages() }
    return languages[code] ?: code
}

// MARK: - Device Configuration page (mirrors iOS DeviceSettingsView)

@Composable
private fun DeviceConfigurationPage(
    viewModel: SettingsViewModel,
    settings: DeviceSettings,
    networks: List<WifiNetworkInfo>,
    isUpdateAvailable: Boolean,
    hasUnsavedChanges: Boolean,
    onFirmwareUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissKeyboard = rememberDismissKeyboardActions()

    var deviceName by remember { mutableStateOf(settings.deviceName) }
    var wifiSsid by remember { mutableStateOf(settings.wifiSsid) }
    var wifiPassword by remember { mutableStateOf(settings.wifiPassword) }
    var accessPointPassword by remember { mutableStateOf(settings.accessPointPassword) }
    var accessCode by remember { mutableStateOf("") }
    var clearAccessCode by remember { mutableStateOf(settings.clearAccessCode) }
    var sensorValueAt0PSI by remember { mutableStateOf(fmt(settings.sensorValueAt0PSI, 3)) }
    var sensorValueAt50PSI by remember { mutableStateOf(fmt(settings.sensorValueAt50PSI, 3)) }
    var sensorFullScalePSI by remember { mutableStateOf(fmt(settings.sensorFullScalePSI, 1)) }
    var maxPressurePSI by remember { mutableStateOf(fmt(settings.maxPressurePSI, 1)) }
    var measureWait by remember { mutableStateOf(fmt(settings.measureWait, 1)) }
    var precision by remember { mutableStateOf(fmt(settings.precision, 1)) }

    var showWifiPassword by remember { mutableStateOf(false) }
    var showHotspotPassword by remember { mutableStateOf(false) }
    var showAccessCode by remember { mutableStateOf(false) }
    var showNetworkDropdown by remember { mutableStateOf(false) }
    var showCalcMethodPicker by remember { mutableStateOf(false) }

    // Reload fields when fresh settings arrive and the user has no pending edits (like iOS).
    LaunchedEffect(settings) {
        if (!hasUnsavedChanges) {
            deviceName = settings.deviceName
            wifiSsid = settings.wifiSsid
            wifiPassword = settings.wifiPassword
            accessPointPassword = settings.accessPointPassword
            sensorValueAt0PSI = fmt(settings.sensorValueAt0PSI, 3)
            sensorValueAt50PSI = fmt(settings.sensorValueAt50PSI, 3)
            sensorFullScalePSI = fmt(settings.sensorFullScalePSI, 1)
            maxPressurePSI = fmt(settings.maxPressurePSI, 1)
            measureWait = fmt(settings.measureWait, 1)
            precision = fmt(settings.precision, 1)
        }
    }
    LaunchedEffect(Unit) { viewModel.scanWifiNetworks() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SettingsBg)
            .padding(horizontal = 16.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Device Info
        if (settings.softwareVersion.isNotEmpty() || settings.ipAddress.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.device_info))
                GroupedSection {
                    if (settings.softwareVersion.isNotEmpty()) {
                        ValueRow(label = stringResource(R.string.firmware_version), value = settings.softwareVersion)
                    }
                    if (settings.softwareVersion.isNotEmpty() && settings.ipAddress.isNotEmpty()) SectionDivider()
                    NavRow(
                        icon = Icons.Default.SystemUpdateAlt,
                        title = stringResource(R.string.firmware_upgrade_title),
                        showBadge = isUpdateAvailable,
                        onClick = onFirmwareUpgradeClick
                    )
                    if (settings.ipAddress.isNotEmpty()) {
                        SectionDivider()
                        ValueRow(label = stringResource(R.string.ip_address), value = settings.ipAddress)
                    }
                }
            }
        }

        // General
        item {
            SectionHeader(stringResource(R.string.general))
            GroupedSection {
                LabeledTextRow(
                    label = stringResource(R.string.device_name),
                    text = deviceName,
                    onTextChange = {
                        deviceName = it
                        viewModel.updateDeviceSettings(deviceName = it)
                    },
                    dismissKeyboard = dismissKeyboard
                )
                SectionDivider()
                SwitchRow(
                    label = stringResource(R.string.simulation_mode),
                    checked = settings.simulationMode,
                    onCheckedChange = { viewModel.updateSimulationMode(it) }
                )
            }
        }

        // Wi-Fi & Hotspot
        item {
            SectionHeader(stringResource(R.string.wifi_and_hotspot))
            GroupedSection {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Box {
                        OutlinedTextField(
                            value = wifiSsid,
                            onValueChange = {
                                wifiSsid = it
                                viewModel.updateDeviceSettings(wifiSsid = it)
                            },
                            label = { Text(stringResource(R.string.ssid)) },
                            modifier = Modifier.fillMaxWidth().reportFocusedEditorBounds(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = dismissKeyboard,
                            colors = settingsTextFieldColors(),
                            trailingIcon = {
                                if (networks.isNotEmpty()) {
                                    IconButton(onClick = { showNetworkDropdown = true }) {
                                        Icon(Icons.Default.Wifi, contentDescription = stringResource(R.string.select_wifi_network), tint = Color.White)
                                    }
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = showNetworkDropdown,
                            onDismissRequest = { showNetworkDropdown = false },
                            modifier = Modifier.background(SettingsCardBg).heightIn(max = 300.dp)
                        ) {
                            networks.forEach { network ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${network.ssid} (${network.level} dBm)",
                                                color = Color.White,
                                                modifier = Modifier.weight(1f)
                                            )
                                            SignalStrengthIndicator(level = network.level)
                                        }
                                    },
                                    onClick = {
                                        wifiSsid = network.ssid
                                        viewModel.updateDeviceSettings(wifiSsid = network.ssid)
                                        showNetworkDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (networks.isEmpty()) {
                                stringResource(R.string.select_wifi_network)
                            } else {
                                "${stringResource(R.string.select_wifi_network)} (${networks.size})"
                            },
                            color = SecondaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { viewModel.scanWifiNetworks() },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandOrange),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp).padding(end = 4.dp))
                            Text(text = stringResource(R.string.scan_networks), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    PasswordFieldRow(
                        label = stringResource(R.string.wifi_password_keep_hint),
                        text = wifiPassword,
                        visible = showWifiPassword,
                        onVisibleChange = { showWifiPassword = it },
                        onTextChange = {
                            wifiPassword = it
                            viewModel.updateDeviceSettings(wifiPassword = it)
                        },
                        dismissKeyboard = dismissKeyboard
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    PasswordFieldRow(
                        label = stringResource(R.string.hotspot_password_hint),
                        text = accessPointPassword,
                        visible = showHotspotPassword,
                        onVisibleChange = { showHotspotPassword = it },
                        onTextChange = {
                            accessPointPassword = it
                            viewModel.updateDeviceSettings(accessPointPassword = it)
                        },
                        dismissKeyboard = dismissKeyboard
                    )
                }
            }
        }

        // BLE Security
        item {
            SectionHeader(stringResource(R.string.ble_security))
            GroupedSection {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    PasswordFieldRow(
                        label = if (settings.hasAccessCode) {
                            stringResource(R.string.access_code_set_hint)
                        } else {
                            stringResource(R.string.access_code_unlocked_hint)
                        },
                        text = accessCode,
                        visible = showAccessCode,
                        onVisibleChange = { showAccessCode = it },
                        onTextChange = {
                            accessCode = it
                            if (clearAccessCode) clearAccessCode = false
                            viewModel.updateDeviceSettings(accessCode = it, clearAccessCode = false)
                        },
                        enabled = !clearAccessCode,
                        dismissKeyboard = dismissKeyboard
                    )
                }
                SectionDivider()
                SwitchRow(
                    label = stringResource(R.string.clear_access_code),
                    checked = clearAccessCode,
                    onCheckedChange = { checked ->
                        clearAccessCode = checked
                        if (checked) accessCode = ""
                        viewModel.updateDeviceSettings(
                            clearAccessCode = checked,
                            accessCode = if (checked) "" else accessCode
                        )
                    }
                )
            }
        }

        // Safety Limits
        item {
            SectionHeader(stringResource(R.string.safety_limits))
            GroupedSection {
                LabeledFieldRow(
                    label = stringResource(R.string.sensor_full_scale_psi),
                    text = sensorFullScalePSI,
                    onTextChange = {
                        sensorFullScalePSI = it
                        it.toFloatOrNull()?.let { v -> viewModel.updateDeviceSettings(sensorFullScalePSI = v) }
                    },
                    dismissKeyboard = dismissKeyboard
                )
                SectionDivider()
                LabeledFieldRow(
                    label = stringResource(R.string.max_pressure_psi),
                    text = maxPressurePSI,
                    onTextChange = {
                        maxPressurePSI = it
                        it.toFloatOrNull()?.let { v -> viewModel.updateDeviceSettings(maxPressurePSI = v) }
                    },
                    dismissKeyboard = dismissKeyboard
                )
                SectionDivider()
                StepperRow(
                    label = stringResource(R.string.max_valve_open_format, settings.maxValveOpenSec),
                    onMinus = { if (settings.maxValveOpenSec > 10) viewModel.updateDeviceSettings(maxValveOpenSec = settings.maxValveOpenSec - 10) },
                    onPlus = { if (settings.maxValveOpenSec < 600) viewModel.updateDeviceSettings(maxValveOpenSec = settings.maxValveOpenSec + 10) },
                    minusEnabled = settings.maxValveOpenSec > 10,
                    plusEnabled = settings.maxValveOpenSec < 600
                )
            }
        }

        // Hardware Pins
        item {
            SectionHeader(stringResource(R.string.hardware_pins))
            GroupedSection {
                StepperRow(
                    label = stringResource(R.string.compressor_pin_format, settings.relayCompressorPin),
                    onMinus = { if (settings.relayCompressorPin > 1) viewModel.updateDeviceSettings(relayCompressorPin = settings.relayCompressorPin - 1) },
                    onPlus = { if (settings.relayCompressorPin < 39) viewModel.updateDeviceSettings(relayCompressorPin = settings.relayCompressorPin + 1) },
                    minusEnabled = settings.relayCompressorPin > 1,
                    plusEnabled = settings.relayCompressorPin < 39
                )
                SectionDivider()
                StepperRow(
                    label = stringResource(R.string.free_pin_format, settings.relayFreePin),
                    onMinus = { if (settings.relayFreePin > 1) viewModel.updateDeviceSettings(relayFreePin = settings.relayFreePin - 1) },
                    onPlus = { if (settings.relayFreePin < 39) viewModel.updateDeviceSettings(relayFreePin = settings.relayFreePin + 1) },
                    minusEnabled = settings.relayFreePin > 1,
                    plusEnabled = settings.relayFreePin < 39
                )
                SectionDivider()
                StepperRow(
                    label = stringResource(R.string.sensor_pin_format, settings.sensorPin),
                    onMinus = { if (settings.sensorPin > 32) viewModel.updateDeviceSettings(sensorPin = settings.sensorPin - 1) },
                    onPlus = { if (settings.sensorPin < 39) viewModel.updateDeviceSettings(sensorPin = settings.sensorPin + 1) },
                    minusEnabled = settings.sensorPin > 32,
                    plusEnabled = settings.sensorPin < 39
                )
            }
        }

        // Calibration & Timing
        item {
            SectionHeader(stringResource(R.string.calibration_and_timing))
            GroupedSection {
                LabeledFieldRow(
                    label = stringResource(R.string.sensor_at_0_psi),
                    text = sensorValueAt0PSI,
                    onTextChange = {
                        sensorValueAt0PSI = it
                        it.toFloatOrNull()?.let { v -> viewModel.updateDeviceSettings(sensorValueAt0PSI = v) }
                    },
                    dismissKeyboard = dismissKeyboard
                )
                SectionDivider()
                LabeledFieldRow(
                    label = stringResource(R.string.sensor_at_50_psi),
                    text = sensorValueAt50PSI,
                    onTextChange = {
                        sensorValueAt50PSI = it
                        it.toFloatOrNull()?.let { v -> viewModel.updateDeviceSettings(sensorValueAt50PSI = v) }
                    },
                    dismissKeyboard = dismissKeyboard
                )
                SectionDivider()
                PickerRow(
                    label = stringResource(R.string.pressure_calc_method),
                    value = stringResource(
                        if (settings.pressureCalcMethod == 2) R.string.voltage_calibration else R.string.adc_calibration
                    ),
                    onClick = { showCalcMethodPicker = true }
                )
                SectionDivider()
                LabeledFieldRow(
                    label = stringResource(R.string.precision_label),
                    text = precision,
                    onTextChange = {
                        precision = it
                        it.toFloatOrNull()?.let { v -> viewModel.updateDeviceSettings(precision = v) }
                    },
                    dismissKeyboard = dismissKeyboard
                )
                SectionDivider()
                StepperRow(
                    label = stringResource(R.string.max_status_time_format, settings.maxSecForStatus),
                    onMinus = { if (settings.maxSecForStatus > 1) viewModel.updateDeviceSettings(maxSecForStatus = settings.maxSecForStatus - 1) },
                    onPlus = { if (settings.maxSecForStatus < 120) viewModel.updateDeviceSettings(maxSecForStatus = settings.maxSecForStatus + 1) },
                    minusEnabled = settings.maxSecForStatus > 1,
                    plusEnabled = settings.maxSecForStatus < 120
                )
                SectionDivider()
                LabeledFieldRow(
                    label = stringResource(R.string.measure_wait_label),
                    text = measureWait,
                    onTextChange = {
                        measureWait = it
                        it.toFloatOrNull()?.let { v -> viewModel.updateDeviceSettings(measureWait = v) }
                    },
                    dismissKeyboard = dismissKeyboard
                )
            }
        }
    }

    if (showCalcMethodPicker) {
        OptionPickerDialog(
            title = stringResource(R.string.pressure_calc_method),
            options = listOf(1, 2),
            label = { stringResource(if (it == 2) R.string.voltage_calibration else R.string.adc_calibration) },
            isSelected = { it == settings.pressureCalcMethod },
            onSelect = {
                viewModel.updateDeviceSettings(pressureCalcMethod = it)
                showCalcMethodPicker = false
            },
            onDismiss = { showCalcMethodPicker = false }
        )
    }
}

private fun fmt(value: Float, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value)

// MARK: - Shared rows & sections (iOS Form-style)

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = SecondaryText,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun GroupedSection(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().reportFocusedEditorBounds(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SettingsCardBg),
        border = BorderStroke(1.dp, SettingsCardBorder)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        color = SettingsCardBorder.copy(alpha = 0.7f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 16.dp)
    )
}

@Composable
private fun settingsTextFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedTextColor = Color.White,
    focusedTextColor = Color.White,
    unfocusedBorderColor = SettingsCardBorder,
    focusedBorderColor = BrandOrange,
    cursorColor = Color.White,
    unfocusedLabelColor = SecondaryText,
    focusedLabelColor = BrandOrange
)

private val settingsSwitchColors: SwitchColors
    @Composable get() = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = SwitchGreen,
        uncheckedThumbColor = Color.Gray,
        uncheckedTrackColor = Color.DarkGray
    )

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = settingsSwitchColors)
    }
}

@Composable
private fun PickerRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(text = value, color = SecondaryText, style = MaterialTheme.typography.bodyLarge)
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = SecondaryText
        )
    }
}

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    showBadge: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) BrandOrange else SecondaryText,
            modifier = Modifier.padding(end = 12.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) Color.White else SecondaryText,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (showBadge) {
            Surface(
                color = BadgeAmber.copy(alpha = 0.18f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.update_available_badge),
                    color = BadgeAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = SecondaryText)
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = BadgeAmber, modifier = Modifier.padding(end = 12.dp))
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(text = value, color = SecondaryText, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun StepperRow(
    label: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean,
    plusEnabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onMinus, enabled = minusEnabled) {
            Icon(
                Icons.Default.Remove,
                contentDescription = null,
                tint = if (minusEnabled) Color.White else SecondaryText
            )
        }
        IconButton(onClick = onPlus, enabled = plusEnabled) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = if (plusEnabled) Color.White else SecondaryText
            )
        }
    }
}

/** Label on the left, right-aligned compact value field — like iOS Form rows. */
@Composable
private fun LabeledFieldRow(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    dismissKeyboard: KeyboardActions
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.width(110.dp).reportFocusedEditorBounds(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = dismissKeyboard,
            colors = settingsTextFieldColors()
        )
    }
}

@Composable
private fun LabeledTextRow(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    dismissKeyboard: KeyboardActions
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.width(180.dp).reportFocusedEditorBounds(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = dismissKeyboard,
            colors = settingsTextFieldColors()
        )
    }
}

@Composable
private fun PasswordFieldRow(
    label: String,
    text: String,
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    onTextChange: (String) -> Unit,
    enabled: Boolean = true,
    dismissKeyboard: KeyboardActions
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text(label) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = dismissKeyboard,
        modifier = Modifier.fillMaxWidth().reportFocusedEditorBounds(),
        colors = settingsTextFieldColors(),
        trailingIcon = {
            IconButton(onClick = { onVisibleChange(!visible) }) {
                Icon(
                    imageVector = if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (visible) "Hide" else "Show",
                    tint = SecondaryText
                )
            }
        }
    )
}

@Composable
private fun <T> OptionPickerDialog(
    title: String,
    options: List<T>,
    label: @Composable (T) -> String,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label(option),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        RadioButton(
                            selected = isSelected(option),
                            onClick = { onSelect(option) },
                            colors = RadioButtonDefaults.colors(selectedColor = BrandOrange)
                        )
                    }
                }
            }
        },
        confirmButton = { },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun AddPressureDialog(
    onAdd: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val parsed = text.replace(",", ".").toFloatOrNull()
    val valid = parsed != null && parsed >= 1f && parsed <= 80f
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_pressure)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("1-80 PSI") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    colors = settingsTextFieldColors()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.enter_pressure_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onAdd(parsed!!) }, enabled = valid) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun SignalStrengthIndicator(level: Int) {
    val icon = when (WifiManager.calculateSignalLevel(level, 4)) {
        0 -> Icons.Default.SignalWifiStatusbarConnectedNoInternet4
        1 -> Icons.Default.SignalWifiStatusbar4Bar
        2 -> Icons.Default.SignalWifi4Bar
        3 -> Icons.Default.SignalWifi4Bar
        else -> Icons.Default.SignalWifiOff
    }
    val description = when (WifiManager.calculateSignalLevel(level, 4)) {
        0 -> stringResource(R.string.weak_signal)
        1 -> stringResource(R.string.fair_signal)
        2 -> stringResource(R.string.good_signal)
        3 -> stringResource(R.string.excellent_signal)
        else -> stringResource(R.string.no_signal)
    }
    Icon(icon, contentDescription = "$description ($level dBm)", tint = SecondaryText)
}

@Composable
private fun rememberDismissKeyboardActions(): KeyboardActions {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    return remember(focusManager, keyboardController) {
        KeyboardActions(
            onDone = {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
        )
    }
}

private class FocusedEditorBounds {
    var owner: Any? = null
    var rootRect: Rect? = null
}

private val LocalFocusedEditorBounds = staticCompositionLocalOf { FocusedEditorBounds() }

@Composable
private fun Modifier.reportFocusedEditorBounds(): Modifier {
    val holder = LocalFocusedEditorBounds.current
    val owner = remember { Any() }
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { state ->
            focused = state.isFocused
            if (!state.isFocused && holder.owner === owner) {
                holder.owner = null
                holder.rootRect = null
            }
        }
        .onGloballyPositioned { coords ->
            if (focused) {
                val pos = coords.positionInRoot()
                val size = coords.size
                holder.owner = owner
                holder.rootRect = Rect(
                    pos.x,
                    pos.y,
                    pos.x + size.width,
                    pos.y + size.height
                )
            }
        }
}
