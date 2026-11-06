package com.nazulay.tiresairpressuremanagement.ui.screens.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nazulay.tiresairpressuremanagement.R
import com.nazulay.tiresairpressuremanagement.data.ble.BleManager
import com.nazulay.tiresairpressuremanagement.data.ble.ConnectionState
import com.nazulay.tiresairpressuremanagement.data.ota.FirmwareUpgradeManager
import com.nazulay.tiresairpressuremanagement.data.ota.UpgradeOperationMode
import com.nazulay.tiresairpressuremanagement.data.ota.UpgradeState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirmwareUpgradeScreen(
    bleManager: BleManager,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val upgradeManager = remember {
        FirmwareUpgradeManager.getInstance(context).also { it.attachBleManager(bleManager) }
    }

    val manifest by upgradeManager.manifest.collectAsState()
    val deviceVersion by upgradeManager.deviceVersion.collectAsState()
    val isUpdateAvailable by upgradeManager.isUpdateAvailable.collectAsState()
    val upgradeState by upgradeManager.upgradeState.collectAsState()
    val overallProgress by upgradeManager.overallProgress.collectAsState()
    val stepLogs by upgradeManager.stepLogs.collectAsState()
    val lastMigrationResult by upgradeManager.lastMigrationResult.collectAsState()
    val reachableHost by upgradeManager.reachableHost.collectAsState()
    val customHostIp by upgradeManager.customHostIp.collectAsState()
    val wifiPromptInfo by upgradeManager.wifiPromptInfo.collectAsState()
    val wifiConnectedAlertHost by upgradeManager.wifiConnectedAlertHost.collectAsState()
    val connectionState by bleManager.connectionState.collectAsState()

    var isCheckingWifi by remember { mutableStateOf(false) }
    var confirmMode by remember { mutableStateOf<UpgradeOperationMode?>(null) }
    var completedSummaryAlert by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (connectionState == ConnectionState.Connected && bleManager.deviceSettings.value == null) {
            bleManager.requestDeviceSettings()
        }
        upgradeManager.checkReachability()
    }

    // Re-check Wi-Fi reachability when returning from Android Wi-Fi Settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !upgradeState.isBusy) {
                scope.launch {
                    upgradeManager.checkReachability()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Keep the screen (and therefore the app's Wi-Fi socket) awake while an
    // upgrade is running — the tablet turning its display off mid-flash can
    // stall the upload to the controller.
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity, upgradeState.isBusy) {
        val window = activity?.window
        if (upgradeState.isBusy) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(upgradeState) {
        if (upgradeState is UpgradeState.Completed) {
            completedSummaryAlert = (upgradeState as UpgradeState.Completed).summary
        }
    }

    // Confirmation Dialog before running an upgrade operation
    if (confirmMode != null) {
        val mode = confirmMode!!
        AlertDialog(
            onDismissRequest = { confirmMode = null },
            title = { Text(stringResource(R.string.confirm_upgrade_title)) },
            text = {
                Text(
                    when (mode) {
                        UpgradeOperationMode.FULL_UPGRADE -> stringResource(R.string.confirm_upgrade_full_message)
                        UpgradeOperationMode.FIRMWARE_ONLY -> stringResource(R.string.confirm_upgrade_firmware_message)
                        UpgradeOperationMode.SPIFFS_ONLY -> stringResource(R.string.confirm_upgrade_spiffs_message)
                        UpgradeOperationMode.SETTINGS_JSON_ONLY -> stringResource(R.string.confirm_upgrade_settings_message)
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmMode = null
                        upgradeManager.performOperation(mode)
                    }
                ) {
                    Text(stringResource(R.string.start))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmMode = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Completion Dialog
    if (completedSummaryAlert != null) {
        AlertDialog(
            onDismissRequest = {
                completedSummaryAlert = null
                upgradeManager.resetState()
                onBackClick()
            },
            title = { Text(stringResource(R.string.firmware_upgrade_title)) },
            text = { Text(completedSummaryAlert.orEmpty()) },
            confirmButton = {
                Button(
                    onClick = {
                        completedSummaryAlert = null
                        upgradeManager.resetState()
                        onBackClick()
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    // Wi-Fi Connected Dialog
    if (wifiConnectedAlertHost != null) {
        AlertDialog(
            onDismissRequest = { upgradeManager.clearWifiConnectedAlert() },
            title = { Text(stringResource(R.string.wifi_connected_alert_title)) },
            text = {
                Text(stringResource(R.string.wifi_connected_alert_message_format, wifiConnectedAlertHost.orEmpty()))
            },
            confirmButton = {
                Button(onClick = { upgradeManager.clearWifiConnectedAlert() }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    // Manual Wi-Fi Join Prompt Dialog
    if (wifiPromptInfo != null) {
        val info = wifiPromptInfo!!
        AlertDialog(
            onDismissRequest = { upgradeManager.clearWifiPrompt() },
            title = { Text(stringResource(R.string.wifi_manual_join_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.wifi_manual_join_message_format,
                        info.ssid,
                        info.password,
                        info.expectedIp
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        upgradeManager.clearWifiPrompt()
                        try {
                            val panelIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                Intent(Settings.Panel.ACTION_WIFI)
                            } else {
                                Intent(Settings.ACTION_WIFI_SETTINGS)
                            }
                            panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(panelIntent)
                        } catch (_: Exception) {
                            val fallback = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(fallback)
                        }
                    }
                ) {
                    Text(stringResource(R.string.open_wifi_settings_button))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            upgradeManager.clearWifiPrompt()
                            scope.launch {
                                upgradeManager.checkReachability()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.check_again_button))
                    }
                    TextButton(onClick = { upgradeManager.clearWifiPrompt() }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.firmware_upgrade_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!upgradeState.isBusy) {
                                upgradeManager.resetState()
                                onBackClick()
                            }
                        },
                        enabled = !upgradeState.isBusy
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF131A24),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D1117))
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Version Comparison Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181C24)),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isUpdateAvailable) Color(0xFFFF6D00).copy(alpha = 0.7f) else Color(0xFF262C38)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = Color(0xFFFF6D00),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.firmware_version_status),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )

                        val badgeColor = if (isUpdateAvailable) Color(0xFFF59E0B) else Color(0xFF10B981)
                        val badgeText = if (isUpdateAvailable) {
                            stringResource(R.string.update_available_badge)
                        } else {
                            stringResource(R.string.up_to_date_badge)
                        }
                        Surface(
                            color = badgeColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = badgeText,
                                color = badgeColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Divider(color = Color(0xFF262C38))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.installed_on_device),
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                            Text(
                                text = deviceVersion.ifEmpty { "—" },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8)
                        )

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = stringResource(R.string.bundled_in_app),
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                            Text(
                                text = upgradeManager.bundledVersion.ifEmpty { "—" },
                                color = Color(0xFFFF9E40),
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            )
                        }
                    }

                    manifest?.let { m ->
                        Text(
                            text = "${m.firmwareSize / 1024} KB .bin  •  ${m.staticFiles.size} Web UI files  •  ${m.requiredSettingsKeys.size} JSON keys",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // 2. Wi-Fi Link Card
            val primarySsid = upgradeManager.preferredWiFiTargets().firstOrNull()?.first
                ?: "esp32-AirUpDownController"
            val isReachable = reachableHost != null

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181C24)),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isReachable) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF262C38)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = if (isReachable) Color(0xFF10B981) else Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.wifi_connection_title),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (reachableHost != null) {
                            Text(
                                text = reachableHost!!,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.wifi_not_verified),
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.wifi_upgrade_instructions_format, primarySsid),
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = customHostIp,
                            onValueChange = { upgradeManager.setCustomHostIp(it) },
                            placeholder = { Text("192.168.4.1", color = Color.Gray) },
                            singleLine = true,
                            enabled = !upgradeState.isBusy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedTextColor = Color.White,
                                focusedTextColor = Color.White,
                                unfocusedBorderColor = Color(0xFF323A4A),
                                focusedBorderColor = Color(0xFFFF6D00)
                            )
                        )

                        Button(
                            onClick = {
                                isCheckingWifi = true
                                scope.launch {
                                    upgradeManager.connectWiFiFromButton()
                                    isCheckingWifi = false
                                }
                            },
                            enabled = !isCheckingWifi && !upgradeState.isBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isReachable) Color(0xFF10B981) else Color(0xFFFF6D00)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isCheckingWifi) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = if (isReachable) {
                                    stringResource(R.string.wifi_connected_button)
                                } else {
                                    stringResource(R.string.connect_wifi_button)
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // 3. Progress & Activity Log Card
            if (upgradeState !is UpgradeState.Idle || stepLogs.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF181C24)),
                    border = BorderStroke(1.dp, Color(0xFF262C38))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (upgradeState.isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFFF6D00)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = stateLabel(upgradeState),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${(overallProgress * 100).toInt()}%",
                                color = Color(0xFFFF9E40),
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                        }

                        LinearProgressIndicator(
                            progress = overallProgress.coerceIn(0f, 1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFFFF6D00),
                            trackColor = Color(0xFF262C38)
                        )

                        lastMigrationResult?.let { migration ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.settings_migration_summary_format,
                                        migration.retainedKeys.size,
                                        migration.addedKeys.size,
                                        migration.removedKeys.size
                                    ),
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                                if (migration.addedKeys.isNotEmpty()) {
                                    Text(
                                        text = "+ ${migration.addedKeys.joinToString(", ")}",
                                        color = Color(0xFFFF9E40),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                }
                                if (migration.removedKeys.isNotEmpty()) {
                                    Text(
                                        text = "- ${migration.removedKeys.joinToString(", ")}",
                                        color = Color(0xFFEF4444),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        if (stepLogs.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                stepLogs.forEach { line ->
                                    Text(
                                        text = "• $line",
                                        color = Color(0xFF94A3B8),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Separate & Combined Upgrade Actions
            Text(
                text = stringResource(R.string.upgrade_options_header).uppercase(),
                color = Color(0xFF94A3B8),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )

            UpgradeOptionCard(
                icon = Icons.Default.SystemUpdateAlt,
                title = stringResource(R.string.upgrade_option_full_title),
                subtitle = stringResource(R.string.upgrade_option_full_subtitle),
                accentColor = Color(0xFFFF6D00),
                highlighted = isUpdateAvailable,
                enabled = !upgradeState.isBusy,
                onClick = { confirmMode = UpgradeOperationMode.FULL_UPGRADE }
            )

            UpgradeOptionCard(
                icon = Icons.Default.Memory,
                title = stringResource(R.string.upgrade_option_firmware_only_title),
                subtitle = stringResource(R.string.upgrade_option_firmware_only_subtitle),
                accentColor = Color(0xFF38BDF8),
                highlighted = false,
                enabled = !upgradeState.isBusy,
                onClick = { confirmMode = UpgradeOperationMode.FIRMWARE_ONLY }
            )

            UpgradeOptionCard(
                icon = Icons.Default.Folder,
                title = stringResource(R.string.upgrade_option_spiffs_only_title),
                subtitle = stringResource(R.string.upgrade_option_spiffs_only_subtitle),
                accentColor = Color(0xFFF59E0B),
                highlighted = false,
                enabled = !upgradeState.isBusy,
                onClick = { confirmMode = UpgradeOperationMode.SPIFFS_ONLY }
            )

            UpgradeOptionCard(
                icon = Icons.Default.Build,
                title = stringResource(R.string.upgrade_option_settings_only_title),
                subtitle = stringResource(R.string.upgrade_option_settings_only_subtitle),
                accentColor = Color(0xFF10B981),
                highlighted = false,
                enabled = !upgradeState.isBusy,
                onClick = { confirmMode = UpgradeOperationMode.SETTINGS_JSON_ONLY }
            )
        }
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? =
    when (this) {
        is android.app.Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
private fun stateLabel(state: UpgradeState): String {
    return when (state) {
        is UpgradeState.Idle -> stringResource(R.string.upgrade_state_ready)
        is UpgradeState.ConnectingWiFi -> stringResource(R.string.upgrade_state_connecting_wifi_format, state.ssid)
        is UpgradeState.MigratingSettings -> stringResource(R.string.upgrade_state_migrating_settings)
        is UpgradeState.UploadingStaticFiles -> stringResource(
            R.string.upgrade_state_uploading_spiffs_format,
            state.fileName,
            state.current,
            state.total
        )
        is UpgradeState.UploadingFirmware -> stringResource(
            R.string.upgrade_state_uploading_firmware_format,
            (state.progress * 100).toInt()
        )
        is UpgradeState.RebootingDevice -> stringResource(R.string.upgrade_state_rebooting)
        is UpgradeState.Completed -> state.summary
        is UpgradeState.Failed -> state.error
    }
}

@Composable
private fun UpgradeOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    highlighted: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181C24)),
        border = BorderStroke(
            width = if (highlighted) 1.5.dp else 1.dp,
            color = if (highlighted) accentColor else Color(0xFF262C38)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(accentColor.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}
