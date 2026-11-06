package com.nazulay.tiresairpressuremanagement.data.ota

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.nazulay.tiresairpressuremanagement.R
import com.nazulay.tiresairpressuremanagement.data.ble.BleManager
import com.nazulay.tiresairpressuremanagement.data.ble.ConnectionState
import com.nazulay.tiresairpressuremanagement.domain.models.BleCommand
import com.nazulay.tiresairpressuremanagement.domain.models.DeviceSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

data class BundledStaticFile(
    val name: String,
    val size: Int,
    val sha256: String
)

data class FirmwareManifest(
    val version: String,
    val versionFull: String,
    val buildNumber: Int,
    val firmwareFile: String,
    val firmwareSize: Int,
    val firmwareSha256: String,
    val defaultSettingsFile: String,
    val requiredSettingsKeys: List<String>,
    val deprecatedSettingsKeys: List<String>,
    val staticFiles: List<BundledStaticFile>
)

data class SettingsMigrationResult(
    val migratedJsonString: String,
    val retainedKeys: List<String>,
    val addedKeys: List<String>,
    val removedKeys: List<String>
) {
    val hasChanges: Boolean
        get() = addedKeys.isNotEmpty() || removedKeys.isNotEmpty()
}

enum class UpgradeOperationMode {
    FULL_UPGRADE,
    FIRMWARE_ONLY,
    SPIFFS_ONLY,
    SETTINGS_JSON_ONLY
}

sealed class UpgradeState {
    object Idle : UpgradeState()
    data class ConnectingWiFi(val ssid: String) : UpgradeState()
    object MigratingSettings : UpgradeState()
    data class UploadingStaticFiles(val current: Int, val total: Int, val fileName: String) : UpgradeState()
    data class UploadingFirmware(val progress: Float) : UpgradeState()
    object RebootingDevice : UpgradeState()
    data class Completed(val summary: String) : UpgradeState()
    data class Failed(val error: String) : UpgradeState()

    val isBusy: Boolean
        get() = when (this) {
            is Idle, is Completed, is Failed -> false
            else -> true
        }
}

data class WiFiConnectionPromptInfo(
    val ssid: String,
    val password: String,
    val expectedIp: String
)

class FirmwareUpgradeManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "FirmwareUpgradeManager"
        private const val ASSET_FOLDER = "FirmwarePackage"

        @Volatile
        private var instance: FirmwareUpgradeManager? = null

        fun getInstance(context: Context): FirmwareUpgradeManager {
            return instance ?: synchronized(this) {
                instance ?: FirmwareUpgradeManager(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Compares controller firmware version strings (e.g. `v1.0.261105_270` vs `v1.0.261102_266` or legacy `2.00`).
         */
        fun isBundledVersionNewer(bundled: String, device: String): Boolean {
            val cleanBundled = bundled.trim()
            val cleanDevice = device.trim()
            if (cleanBundled.isEmpty() || cleanDevice.isEmpty()) return false
            if (cleanBundled == cleanDevice) return false

            val bundledInfo = parseVersionComponents(cleanBundled)
            val deviceInfo = parseVersionComponents(cleanDevice)

            if (bundledInfo.second != null && deviceInfo.second != null) {
                if (bundledInfo.second != deviceInfo.second) {
                    return bundledInfo.second!! > deviceInfo.second!!
                }
            }

            if (bundledInfo.second != null && deviceInfo.second == null) {
                return true
            }

            val maxCount = maxOf(bundledInfo.first.size, deviceInfo.first.size)
            for (i in 0 until maxCount) {
                val bVal = bundledInfo.first.getOrElse(i) { 0 }
                val dVal = deviceInfo.first.getOrElse(i) { 0 }
                if (bVal != dVal) {
                    return bVal > dVal
                }
            }
            return false
        }

        private fun parseVersionComponents(version: String): Pair<List<Int>, Int?> {
            var working = version.trim()
            while (working.lowercase().startsWith("v")) {
                working = working.substring(1)
            }
            val dashIdx = working.indexOf(" - ")
            if (dashIdx >= 0) {
                working = working.substring(0, dashIdx)
            }

            val underscoreParts = working.split("_", limit = 2)
            val buildNumber = if (underscoreParts.size > 1) underscoreParts[1].toIntOrNull() else null
            val numericParts = underscoreParts[0].split(".").mapNotNull { it.toIntOrNull() }
            return numericParts to buildNumber
        }

        /**
         * Non-destructively merges the controller's current `settings.json` with `default_settings.json`:
         * - Preserves all user values for keys present in the target schema
         * - Migrates legacy `BTPinCode` + `BLESecurityEnabled` into `AccessCode`
         * - Adds missing schema keys with their default values
         * - Removes deprecated/unknown keys not present in the target schema
         */
        fun migrateSettingsJson(
            currentJsonString: String?,
            fallbackDeviceSettings: DeviceSettings?,
            defaultSchemaString: String,
            overrideSimulationMode: Boolean? = null
        ): SettingsMigrationResult {
            val defaultObj = JSONObject(defaultSchemaString)
            val currentObj = try {
                if (!currentJsonString.isNullOrBlank()) {
                    JSONObject(currentJsonString)
                } else if (fallbackDeviceSettings != null) {
                    jsonFromDeviceSettings(fallbackDeviceSettings)
                } else {
                    JSONObject()
                }
            } catch (_: Exception) {
                if (fallbackDeviceSettings != null) jsonFromDeviceSettings(fallbackDeviceSettings) else JSONObject()
            }

            if (overrideSimulationMode != null) {
                currentObj.put("SimulationMode", overrideSimulationMode)
            }

            // Legacy migration: BTPinCode -> AccessCode when BLESecurityEnabled was true
            val hasAccessCode = currentObj.has("AccessCode") && !currentObj.isNull("AccessCode")
            if (!hasAccessCode && currentObj.has("BTPinCode")) {
                val legacyPin = currentObj.optString("BTPinCode", "")
                val securityEnabled = when (val rawSec = currentObj.opt("BLESecurityEnabled")) {
                    is Boolean -> rawSec
                    is Number -> rawSec.toInt() != 0
                    is String -> rawSec.equals("true", ignoreCase = true) || rawSec == "1"
                    else -> false
                }
                if (securityEnabled && legacyPin.isNotEmpty()) {
                    currentObj.put("AccessCode", legacyPin)
                }
            }

            val removedKeys = mutableListOf<String>()
            val currentKeys = currentObj.keys().asSequence().toList().sorted()
            for (key in currentKeys) {
                if (!defaultObj.has(key)) {
                    removedKeys.add(key)
                }
            }

            val merged = JSONObject()
            val retainedKeys = mutableListOf<String>()
            val addedKeys = mutableListOf<String>()

            val schemaKeys = defaultObj.keys().asSequence().toList().sorted()
            for (key in schemaKeys) {
                val defaultVal = defaultObj.get(key)
                if (currentObj.has(key) && !currentObj.isNull(key)) {
                    val existingVal = currentObj.get(key)
                    merged.put(key, coerceValue(existingVal, defaultVal))
                    retainedKeys.add(key)
                } else {
                    merged.put(key, defaultVal)
                    addedKeys.add(key)
                }
            }

            return SettingsMigrationResult(
                migratedJsonString = merged.toString(2),
                retainedKeys = retainedKeys,
                addedKeys = addedKeys,
                removedKeys = removedKeys
            )
        }

        private fun coerceValue(existing: Any, defaultVal: Any): Any {
            return when (defaultVal) {
                is Boolean -> when (existing) {
                    is Boolean -> existing
                    is Number -> existing.toInt() != 0
                    is String -> existing.equals("true", ignoreCase = true) || existing == "1"
                    else -> defaultVal
                }
                is String -> existing.toString()
                is Int -> when (existing) {
                    is Number -> existing.toInt()
                    is String -> existing.toDoubleOrNull()?.toInt() ?: defaultVal
                    else -> defaultVal
                }
                is Double, is Float -> when (existing) {
                    is Number -> existing.toDouble()
                    is String -> existing.toDoubleOrNull() ?: defaultVal
                    else -> defaultVal
                }
                else -> existing
            }
        }

        private fun jsonFromDeviceSettings(s: DeviceSettings): JSONObject {
            return JSONObject().apply {
                put("DeviceName", s.deviceName)
                put("wifissid", s.wifiSsid)
                put("wifipassword", s.wifiPassword)
                put("RelayCompressorPin", s.relayCompressorPin)
                put("RelayFreePin", s.relayFreePin)
                put("SensorPin", s.sensorPin)
                put("MaxSecForStatus", s.maxSecForStatus)
                put("SensorValueAt0PSI", s.sensorValueAt0PSI.toDouble())
                put("SensorValueAt50PSI", s.sensorValueAt50PSI.toDouble())
                put("AccessCode", s.accessCode)
                put("MessureWait", s.measureWait.toDouble())
                put("Precision", s.precision.toDouble())
                put("SimulationMode", s.simulationMode)
                put("PressureCalcMethod", s.pressureCalcMethod)
                put("MaxPressurePSI", s.maxPressurePSI.toDouble())
                put("MaxValveOpenSec", s.maxValveOpenSec)
                put("SensorFullScalePSI", s.sensorFullScalePSI.toDouble())
                put("AccessPointPassword", s.accessPointPassword)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var attachedBleManager: BleManager? = null
    private var cachedDeviceSettings: DeviceSettings? = null
    private var dismissedForDeviceVersion: String? = null

    private val _manifest = MutableStateFlow<FirmwareManifest?>(null)
    val manifest: StateFlow<FirmwareManifest?> = _manifest.asStateFlow()

    private val _deviceVersion = MutableStateFlow("")
    val deviceVersion: StateFlow<String> = _deviceVersion.asStateFlow()

    private val _isUpdateAvailable = MutableStateFlow(false)
    val isUpdateAvailable: StateFlow<Boolean> = _isUpdateAvailable.asStateFlow()

    private val _updateSuggestionDismissed = MutableStateFlow(false)
    val updateSuggestionDismissed: StateFlow<Boolean> = _updateSuggestionDismissed.asStateFlow()

    private val _upgradeState = MutableStateFlow<UpgradeState>(UpgradeState.Idle)
    val upgradeState: StateFlow<UpgradeState> = _upgradeState.asStateFlow()

    private val _overallProgress = MutableStateFlow(0f)
    val overallProgress: StateFlow<Float> = _overallProgress.asStateFlow()

    private val _stepLogs = MutableStateFlow<List<String>>(emptyList())
    val stepLogs: StateFlow<List<String>> = _stepLogs.asStateFlow()

    private val _lastMigrationResult = MutableStateFlow<SettingsMigrationResult?>(null)
    val lastMigrationResult: StateFlow<SettingsMigrationResult?> = _lastMigrationResult.asStateFlow()

    private val _reachableHost = MutableStateFlow<String?>(null)
    val reachableHost: StateFlow<String?> = _reachableHost.asStateFlow()

    private val _customHostIp = MutableStateFlow("")
    val customHostIp: StateFlow<String> = _customHostIp.asStateFlow()

    private val _wifiPromptInfo = MutableStateFlow<WiFiConnectionPromptInfo?>(null)
    val wifiPromptInfo: StateFlow<WiFiConnectionPromptInfo?> = _wifiPromptInfo.asStateFlow()

    private val _wifiConnectedAlertHost = MutableStateFlow<String?>(null)
    val wifiConnectedAlertHost: StateFlow<String?> = _wifiConnectedAlertHost.asStateFlow()

    init {
        loadBundledManifest()
    }

    val bundledVersion: String
        get() = _manifest.value?.version.orEmpty()

    fun setCustomHostIp(ip: String) {
        _customHostIp.value = ip
    }

    fun clearWifiPrompt() {
        _wifiPromptInfo.value = null
    }

    fun clearWifiConnectedAlert() {
        _wifiConnectedAlertHost.value = null
    }

    fun dismissUpdateSuggestion() {
        _updateSuggestionDismissed.value = true
        dismissedForDeviceVersion = _deviceVersion.value
    }

    fun resetState() {
        if (!_upgradeState.value.isBusy) {
            _upgradeState.value = UpgradeState.Idle
            _overallProgress.value = 0f
        }
    }

    fun attachBleManager(bleManager: BleManager) {
        if (attachedBleManager === bleManager) return
        attachedBleManager = bleManager

        scope.launch {
            bleManager.deviceSettings.collect { settings ->
                if (settings != null) {
                    cachedDeviceSettings = settings
                    val newVer = settings.softwareVersion.trim()
                    if (newVer.isNotEmpty()) {
                        _deviceVersion.value = newVer
                    }
                    val newIp = settings.ipAddress.trim()
                    if (newIp.isNotBlank()) {
                        val currentCustom = _customHostIp.value.trim()
                        if (currentCustom.isBlank() || (currentCustom == "192.168.4.1" && newIp != "192.168.4.1")) {
                            _customHostIp.value = newIp
                        }
                    }
                    if (newVer.isNotEmpty() && newVer != dismissedForDeviceVersion) {
                        _updateSuggestionDismissed.value = false
                    }
                    evaluateUpdateAvailability()
                }
            }
        }

        scope.launch {
            bleManager.connectionState.collect { state ->
                if (state == ConnectionState.Disconnected && !_upgradeState.value.isBusy) {
                    _deviceVersion.value = ""
                    _isUpdateAvailable.value = false
                    _reachableHost.value = null
                }
            }
        }
    }

    private fun evaluateUpdateAvailability() {
        val bundled = bundledVersion
        val devVer = _deviceVersion.value
        _isUpdateAvailable.value = if (bundled.isNotEmpty() && devVer.isNotEmpty()) {
            isBundledVersionNewer(bundled, devVer)
        } else {
            false
        }
    }

    private fun loadBundledManifest() {
        try {
            val jsonStr = readAssetText("$ASSET_FOLDER/manifest.json")
            val obj = JSONObject(jsonStr)
            val reqKeys = mutableListOf<String>()
            val reqArr = obj.optJSONArray("requiredSettingsKeys") ?: JSONArray()
            for (i in 0 until reqArr.length()) reqKeys.add(reqArr.getString(i))

            val depKeys = mutableListOf<String>()
            val depArr = obj.optJSONArray("deprecatedSettingsKeys") ?: JSONArray()
            for (i in 0 until depArr.length()) depKeys.add(depArr.getString(i))

            val staticFiles = mutableListOf<BundledStaticFile>()
            val filesArr = obj.optJSONArray("staticFiles") ?: JSONArray()
            for (i in 0 until filesArr.length()) {
                val fObj = filesArr.getJSONObject(i)
                staticFiles.add(
                    BundledStaticFile(
                        name = fObj.getString("name"),
                        size = fObj.getInt("size"),
                        sha256 = fObj.optString("sha256", "")
                    )
                )
            }

            _manifest.value = FirmwareManifest(
                version = obj.getString("version"),
                versionFull = obj.optString("versionFull", obj.getString("version")),
                buildNumber = obj.optInt("buildNumber", 0),
                firmwareFile = obj.optString("firmwareFile", "firmware.bin"),
                firmwareSize = obj.optInt("firmwareSize", 0),
                firmwareSha256 = obj.optString("firmwareSha256", ""),
                defaultSettingsFile = obj.optString("defaultSettingsFile", "default_settings.json"),
                requiredSettingsKeys = reqKeys,
                deprecatedSettingsKeys = depKeys,
                staticFiles = staticFiles
            )
            evaluateUpdateAvailability()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load FirmwarePackage/manifest.json: ${e.message}")
        }
    }

    private fun readAssetBytes(assetPath: String): ByteArray {
        return context.assets.open(assetPath).use { it.readBytes() }
    }

    private fun readAssetText(assetPath: String): String {
        return readAssetBytes(assetPath).toString(Charsets.UTF_8)
    }

    // MARK: - Wi-Fi Reachability & Connection

    private fun activeWiFiNetwork(): Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        for (net in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(net) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return net
            }
        }
        return null
    }

    private fun candidateHosts(settings: DeviceSettings?): List<String> {
        val hosts = mutableListOf<String>()
        val bleIp = settings?.ipAddress?.trim().orEmpty()
        val trimmedCustom = _customHostIp.value.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        if (bleIp.isNotEmpty() && bleIp != "192.168.4.1") {
            hosts.add(bleIp)
        }
        if (trimmedCustom.isNotEmpty() && !hosts.contains(trimmedCustom)) {
            hosts.add(trimmedCustom)
        }
        if (bleIp.isNotEmpty() && !hosts.contains(bleIp)) {
            hosts.add(bleIp)
        }
        if (!hosts.contains("192.168.4.1")) {
            hosts.add("192.168.4.1")
        }
        return hosts
    }

    suspend fun checkReachability(): String? {
        val settings = attachedBleManager?.deviceSettings?.value ?: cachedDeviceSettings
        val host = resolveReachableHost(settings)
        _reachableHost.value = host
        if (host != null) {
            _customHostIp.value = host
        }
        return host
    }

    suspend fun connectWiFiFromButton() {
        if (_upgradeState.value.isBusy) return
        val settings = attachedBleManager?.deviceSettings?.value ?: cachedDeviceSettings

        val host = resolveReachableHost(settings)
        if (host != null) {
            _reachableHost.value = host
            _customHostIp.value = host
            _upgradeState.value = UpgradeState.Idle
            appendLog(context.getString(R.string.upgrade_log_connected_ip_format, host))
            _wifiConnectedAlertHost.value = host
            return
        }

        val target = preferredWiFiTargets().firstOrNull() ?: ("esp32-AirUpDownController" to "12345678")
        copyPasswordToClipboard(target.second)
        val expectedIp = settings?.ipAddress?.takeIf { it.isNotBlank() } ?: "192.168.4.1"
        _reachableHost.value = null
        _wifiPromptInfo.value = WiFiConnectionPromptInfo(
            ssid = target.first,
            password = target.second,
            expectedIp = expectedIp
        )
    }

    fun preferredWiFiTargets(): List<Pair<String, String>> {
        val settings = attachedBleManager?.deviceSettings?.value ?: cachedDeviceSettings
        val apSsid = settings?.deviceName?.takeIf { it.isNotBlank() }
            ?: attachedBleManager?.connectedDeviceName?.value?.takeIf { it.isNotBlank() }
            ?: "esp32-AirUpDownController"
        val apPass = settings?.accessPointPassword?.takeIf { it.length >= 8 } ?: "12345678"

        val targets = mutableListOf<Pair<String, String>>()
        if (settings != null &&
            settings.wifiSsid.isNotBlank() &&
            settings.ipAddress.isNotBlank() &&
            settings.ipAddress != "192.168.4.1"
        ) {
            targets.add(settings.wifiSsid to settings.wifiPassword)
        }
        if (targets.none { it.first == apSsid }) {
            targets.add(apSsid to apPass)
        }
        return targets
    }

    private fun copyPasswordToClipboard(password: String) {
        if (password.isBlank()) return
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Controller Wi-Fi Password", password))
        } catch (_: Exception) {
        }
    }

    private suspend fun resolveReachableHost(settings: DeviceSettings?): String? {
        val wifiNet = activeWiFiNetwork()
        for (host in candidateHosts(settings)) {
            val ver = probeSettingsValues(host, wifiNet)
            if (ver != null) {
                if (ver.isNotEmpty()) {
                    _deviceVersion.value = ver
                    cachedDeviceSettings = cachedDeviceSettings?.copy(softwareVersion = ver)
                    evaluateUpdateAvailability()
                }
                return host
            }
            val resp = DirectWiFiHttpClient.sendGet(host, "/settings.json", 2000, wifiNet)
            if (resp != null && resp.first in 200..299) {
                return host
            }
        }
        return null
    }

    private suspend fun probeSettingsValues(host: String, wifiNet: Network?): String? {
        val resp = DirectWiFiHttpClient.sendGet(host, "/settingsValues", 2500, wifiNet) ?: return null
        if (resp.first !in 200..299) return null
        val text = resp.second.toString(Charsets.UTF_8).trim()
        val parts = text.split(";")
        if (parts.size > 6) {
            val simFlag = parts[6].trim()
            val isSim = simFlag == "1" || simFlag.equals("true", ignoreCase = true)
            cachedDeviceSettings = (cachedDeviceSettings ?: DeviceSettings(deviceName = parts[0])).copy(
                simulationMode = isSim
            )
        }
        if (parts.size > 15) {
            val ver = parts[15].trim()
            if (ver.isNotEmpty() && ver != "0") {
                return ver
            }
        }
        return ""
    }

    private suspend fun connectToControllerWiFi(): String {
        val settings = attachedBleManager?.deviceSettings?.value ?: cachedDeviceSettings
        val existingHost = resolveReachableHost(settings)
        if (existingHost != null) {
            _reachableHost.value = existingHost
            _customHostIp.value = existingHost
            appendLog(context.getString(R.string.upgrade_log_connected_ip_format, existingHost))
            return existingHost
        }

        val target = preferredWiFiTargets().firstOrNull() ?: ("esp32-AirUpDownController" to "12345678")
        copyPasswordToClipboard(target.second)
        val expectedIp = settings?.ipAddress?.takeIf { it.isNotBlank() } ?: "192.168.4.1"
        _wifiPromptInfo.value = WiFiConnectionPromptInfo(
            ssid = target.first,
            password = target.second,
            expectedIp = expectedIp
        )
        throw IllegalStateException(
            context.getString(R.string.upgrade_error_unreachable_format, target.first, expectedIp)
        )
    }

    // MARK: - Upgrade Operations

    fun performOperation(mode: UpgradeOperationMode) {
        if (_upgradeState.value.isBusy) return
        scope.launch {
            executeOperationInternal(mode)
        }
    }

    private suspend fun executeOperationInternal(mode: UpgradeOperationMode) {
        _stepLogs.value = emptyList()
        _overallProgress.value = 0f
        _lastMigrationResult.value = null

        val ble = attachedBleManager
        if (ble?.deviceSettings?.value != null) {
            cachedDeviceSettings = ble.deviceSettings.value
        }
        if (ble?.connectionState?.value == ConnectionState.Connected) {
            ble.sendCommand(BleCommand.Stop())
        }

        try {
            val host = connectToControllerWiFi()
            val originalSettingsBytes = fetchExistingSettingsJson(host)
            val originalSettingsStr = originalSettingsBytes?.toString(Charsets.UTF_8)
            val originalSimMode = resolveOriginalSimulationMode(originalSettingsStr)
            var didEnableTempSim = false

            if (mode != UpgradeOperationMode.SETTINGS_JSON_ONLY && !originalSimMode) {
                didEnableTempSim = enableTemporarySimulationMode(host)
            }

            when (mode) {
                UpgradeOperationMode.FULL_UPGRADE -> {
                    executeStaticFilesUpload(host, 0.05f, 0.32f)
                    executeSettingsMigration(
                        host = host,
                        prefetchedJsonString = originalSettingsStr,
                        overrideSimulationMode = originalSimMode,
                        startProg = 0.32f,
                        endProg = 0.42f
                    )
                    delay(500)
                    executeFirmwareUpload(host, 0.42f, 0.92f)
                    finalizeWithRebootAndReconnect(
                        host = host,
                        alreadyRebooting = true,
                        expectBundledFirmware = true,
                        restoreSimulationModeTo = if (didEnableTempSim) originalSimMode else null
                    )
                    val summary = context.getString(R.string.upgrade_complete_full_format, _deviceVersion.value)
                    _upgradeState.value = UpgradeState.Completed(summary)
                    appendLog(summary)
                }

                UpgradeOperationMode.FIRMWARE_ONLY -> {
                    if (didEnableTempSim && originalSettingsBytes != null) {
                        try {
                            uploadMultipartFile(
                                host = host,
                                endpoint = "/doUpload",
                                fieldName = "file",
                                fileName = "/settings.json",
                                mimeType = "application/json",
                                fileData = originalSettingsBytes,
                                isFirmwareOta = false,
                                onProgress = null
                            )
                            delay(300)
                        } catch (_: Exception) {
                        }
                    }
                    executeFirmwareUpload(host, 0.08f, 0.92f)
                    finalizeWithRebootAndReconnect(
                        host = host,
                        alreadyRebooting = true,
                        expectBundledFirmware = true,
                        restoreSimulationModeTo = if (didEnableTempSim) originalSimMode else null
                    )
                    val summary = context.getString(R.string.upgrade_complete_firmware_format, _deviceVersion.value)
                    _upgradeState.value = UpgradeState.Completed(summary)
                    appendLog(summary)
                }

                UpgradeOperationMode.SPIFFS_ONLY -> {
                    executeStaticFilesUpload(host, 0.05f, 0.92f)
                    if (didEnableTempSim) {
                        restoreSimulationModeAfterMaintenance(host, originalSimMode, originalSettingsBytes)
                    }
                    _overallProgress.value = 1.0f
                    val count = _manifest.value?.staticFiles?.size ?: 0
                    val summary = context.getString(R.string.upgrade_complete_spiffs_format, count)
                    _upgradeState.value = UpgradeState.Completed(summary)
                    appendLog(summary)
                }

                UpgradeOperationMode.SETTINGS_JSON_ONLY -> {
                    executeSettingsMigration(
                        host = host,
                        prefetchedJsonString = originalSettingsStr,
                        overrideSimulationMode = originalSimMode,
                        startProg = 0.10f,
                        endProg = 0.85f
                    )
                    finalizeWithRebootAndReconnect(
                        host = host,
                        alreadyRebooting = false,
                        expectBundledFirmware = false,
                        restoreSimulationModeTo = null
                    )
                    val res = _lastMigrationResult.value
                    val summary = context.getString(
                        R.string.upgrade_complete_settings_format,
                        res?.retainedKeys?.size ?: 0,
                        res?.addedKeys?.size ?: 0,
                        res?.removedKeys?.size ?: 0
                    )
                    _upgradeState.value = UpgradeState.Completed(summary)
                    appendLog(summary)
                }
            }
        } catch (e: Exception) {
            val msg = e.message ?: context.getString(R.string.connection_error)
            appendLog(msg)
            _upgradeState.value = UpgradeState.Failed(msg)
        }
    }

    private fun resolveOriginalSimulationMode(settingsJsonStr: String?): Boolean {
        if (!settingsJsonStr.isNullOrBlank()) {
            try {
                val obj = JSONObject(settingsJsonStr)
                if (obj.has("SimulationMode")) {
                    return when (val v = obj.get("SimulationMode")) {
                        is Boolean -> v
                        is Number -> v.toInt() != 0
                        is String -> v.equals("true", ignoreCase = true) || v == "1"
                        else -> false
                    }
                }
            } catch (_: Exception) {
            }
        }
        return (attachedBleManager?.deviceSettings?.value ?: cachedDeviceSettings)?.simulationMode ?: false
    }

    private suspend fun enableTemporarySimulationMode(host: String): Boolean {
        appendLog("Switching controller to Simulation Mode for uninterrupted OTA...")
        val ble = attachedBleManager
        if (ble != null && ble.connectionState.value == ConnectionState.Connected) {
            ble.sendCommand(BleCommand.SimulationMode(enabled = true))
            delay(1500)
            return true
        }
        // Do NOT call `/updateSettings?SimulationMode=1` over HTTP here because `saveSettings`
        // triggers `scheduleRestart(1500)`, which drops the ESP32 SoftAP and causes Android
        // to auto-reconnect to home Wi-Fi right before the OTA upload starts!
        return false
    }

    private suspend fun restoreSimulationModeAfterMaintenance(
        host: String,
        originalSimMode: Boolean,
        originalSettingsBytes: ByteArray?
    ) {
        appendLog("Restoring controller Simulation Mode...")
        val ble = attachedBleManager
        if (ble != null && ble.connectionState.value == ConnectionState.Connected) {
            ble.sendCommand(BleCommand.SimulationMode(enabled = originalSimMode))
            delay(800)
        } else if (originalSettingsBytes != null) {
            try {
                uploadMultipartFile(
                    host = host,
                    endpoint = "/doUpload",
                    fieldName = "file",
                    fileName = "/settings.json",
                    mimeType = "application/json",
                    fileData = originalSettingsBytes,
                    isFirmwareOta = false,
                    onProgress = null
                )
                DirectWiFiHttpClient.sendGet(host, "/reboot", 2500, activeWiFiNetwork())
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun executeSettingsMigration(
        host: String,
        prefetchedJsonString: String?,
        overrideSimulationMode: Boolean?,
        startProg: Float,
        endProg: Float
    ) {
        _upgradeState.value = UpgradeState.MigratingSettings
        _overallProgress.value = startProg
        appendLog(context.getString(R.string.upgrade_log_fetching_settings))

        val defaultSchemaStr = readAssetText("$ASSET_FOLDER/default_settings.json")
        val currentStr = prefetchedJsonString ?: fetchExistingSettingsJson(host)?.toString(Charsets.UTF_8)

        val migration = migrateSettingsJson(
            currentJsonString = currentStr,
            fallbackDeviceSettings = attachedBleManager?.deviceSettings?.value ?: cachedDeviceSettings,
            defaultSchemaString = defaultSchemaStr,
            overrideSimulationMode = overrideSimulationMode
        )
        _lastMigrationResult.value = migration

        appendLog(
            context.getString(
                R.string.upgrade_log_settings_merged_format,
                migration.retainedKeys.size,
                migration.addedKeys.size,
                migration.removedKeys.size
            )
        )

        _overallProgress.value = (startProg + endProg) / 2f

        uploadMultipartFile(
            host = host,
            endpoint = "/doUpload",
            fieldName = "file",
            fileName = "/settings.json",
            mimeType = "application/json",
            fileData = migration.migratedJsonString.toByteArray(Charsets.UTF_8),
            isFirmwareOta = false,
            onProgress = null
        )

        _overallProgress.value = endProg
        appendLog(context.getString(R.string.upgrade_log_settings_uploaded))
    }

    private suspend fun executeStaticFilesUpload(host: String, startProg: Float, endProg: Float) {
        val files = _manifest.value?.staticFiles ?: throw IllegalStateException(
            context.getString(R.string.upgrade_error_package_missing)
        )
        if (files.isEmpty()) return

        val total = files.size
        for ((idx, fileInfo) in files.withIndex()) {
            val stepNum = idx + 1
            _upgradeState.value = UpgradeState.UploadingStaticFiles(stepNum, total, fileInfo.name)
            appendLog(context.getString(R.string.upgrade_log_uploading_file_format, fileInfo.name, stepNum, total))

            val fileBytes = readAssetBytes("$ASSET_FOLDER/spiffs/${fileInfo.name}")
            uploadMultipartFile(
                host = host,
                endpoint = "/doUpload",
                fieldName = "file",
                fileName = "/${fileInfo.name}",
                mimeType = mimeTypeFor(fileInfo.name),
                fileData = fileBytes,
                isFirmwareOta = false,
                onProgress = null
            )

            delay(350)
            val fraction = stepNum.toFloat() / total.toFloat()
            _overallProgress.value = startProg + fraction * (endProg - startProg)
        }
    }

    private suspend fun executeFirmwareUpload(host: String, startProg: Float, endProg: Float) {
        val fwBytes = readAssetBytes("$ASSET_FOLDER/firmware.bin")
        _upgradeState.value = UpgradeState.UploadingFirmware(0f)
        appendLog(context.getString(R.string.upgrade_log_flashing_firmware_format, bundledVersion, fwBytes.size / 1024))

        // 1. Prime lastUpdateChunkMs only on controllers running older firmware
        // (build < 270) where loop() on Core 1 checked `millis() - lastUpdateChunkMs > 20000`
        // before lastUpdateChunkMs was initialized on the first OTA chunk. Controllers on
        // build >= 270 already set lastUpdateChunkMs first, and the prime upload's failed
        // Update.end() ("Flash Read Failed") can leave the updater in a bad state.
        val deviceBuild = parseVersionComponents(_deviceVersion.value).second
        val needsPrime = deviceBuild == null || deviceBuild < 270
        if (needsPrime) try {
            uploadMultipartFile(
                host = host,
                endpoint = "/doUpdate",
                fieldName = "update",
                fileName = "prime.bin",
                mimeType = "application/octet-stream",
                fileData = byteArrayOf(0x00),
                isFirmwareOta = false,
                onProgress = null
            )
            delay(250)
        } catch (_: Exception) {
        }

        uploadMultipartFile(
            host = host,
            endpoint = "/doUpdate",
            fieldName = "update",
            fileName = "firmware.bin",
            mimeType = "application/octet-stream",
            fileData = fwBytes,
            isFirmwareOta = true
        ) { pct ->
            scope.launch {
                _upgradeState.value = UpgradeState.UploadingFirmware(pct)
                _overallProgress.value = startProg + pct * (endProg - startProg)
            }
        }

        _overallProgress.value = endProg
        appendLog(context.getString(R.string.upgrade_log_firmware_uploaded))
    }

    private suspend fun finalizeWithRebootAndReconnect(
        host: String,
        alreadyRebooting: Boolean,
        expectBundledFirmware: Boolean,
        restoreSimulationModeTo: Boolean?
    ) {
        _upgradeState.value = UpgradeState.RebootingDevice
        _overallProgress.value = 0.94f
        appendLog(context.getString(R.string.upgrade_log_rebooting))

        if (!alreadyRebooting) {
            DirectWiFiHttpClient.sendGet(host, "/reboot", 2500, activeWiFiNetwork())
        }

        delay(5500)
        attachedBleManager?.connectToSavedDevice()

        var verifiedVersion: String? = null
        var didRestoreSim = restoreSimulationModeTo == null
        for (i in 0 until 10) {
            delay(1000)
            val wifiNet = activeWiFiNetwork()
            val httpVer = probeSettingsValues(host, wifiNet)
            if (!httpVer.isNullOrEmpty()) {
                verifiedVersion = httpVer
                _deviceVersion.value = httpVer
                evaluateUpdateAvailability()
            }
            val ble = attachedBleManager
            if (ble != null && ble.connectionState.value == ConnectionState.Connected) {
                if (!didRestoreSim && restoreSimulationModeTo != null) {
                    ble.sendCommand(BleCommand.SimulationMode(enabled = restoreSimulationModeTo))
                    didRestoreSim = true
                    delay(400)
                }
                ble.requestDeviceSettings()
                delay(900)
                val bleVer = ble.deviceSettings.value?.softwareVersion?.trim().orEmpty()
                if (bleVer.isNotEmpty()) {
                    verifiedVersion = bleVer
                    _deviceVersion.value = bleVer
                    evaluateUpdateAvailability()
                }
            } else {
                ble?.connectToSavedDevice()
            }
            if (verifiedVersion != null) {
                if (!expectBundledFirmware || !isBundledVersionNewer(bundledVersion, verifiedVersion)) {
                    break
                }
            }
        }

        if (expectBundledFirmware && verifiedVersion == null && bundledVersion.isNotEmpty()) {
            // If Android switched away from SoftAP during ESP32 reboot and BLE is still reconnecting,
            // reflect the newly flashed version until BLE finishes reconnecting.
            _deviceVersion.value = bundledVersion
        }
        evaluateUpdateAvailability()
        if (!_isUpdateAvailable.value) {
            _updateSuggestionDismissed.value = true
            dismissedForDeviceVersion = _deviceVersion.value
        }
        _overallProgress.value = 1.0f
    }

    private suspend fun fetchExistingSettingsJson(host: String): ByteArray? {
        val resp = DirectWiFiHttpClient.sendGet(host, "/settings.json", 4000, activeWiFiNetwork())
        if (resp != null && resp.first in 200..299 && resp.second.isNotEmpty()) {
            return resp.second
        }
        return null
    }

    private suspend fun uploadMultipartFile(
        host: String,
        endpoint: String,
        fieldName: String,
        fileName: String,
        mimeType: String,
        fileData: ByteArray,
        isFirmwareOta: Boolean,
        onProgress: ((Float) -> Unit)?
    ) {
        val statusCode = DirectWiFiHttpClient.uploadMultipart(
            host = host,
            path = endpoint,
            fieldName = fieldName,
            fileName = fileName,
            mimeType = mimeType,
            fileData = fileData,
            isFirmwareOta = isFirmwareOta,
            wifiNetwork = activeWiFiNetwork(),
            onProgress = onProgress
        )
        if (statusCode !in 200..299) {
            throw IllegalStateException(
                context.getString(R.string.upgrade_error_upload_failed_format, fileName, statusCode)
            )
        }
    }

    private fun mimeTypeFor(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".html") -> "text/html"
            lower.endsWith(".css") -> "text/css"
            lower.endsWith(".js") -> "text/javascript"
            lower.endsWith(".json") -> "application/json"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            else -> "application/octet-stream"
        }
    }

    private fun appendLog(text: String) {
        Log.d(TAG, text)
        _stepLogs.value = _stepLogs.value + text
    }
}

/**
 * Direct Wi-Fi Socket HTTP client for Android:
 * Binds sockets to the active `TRANSPORT_WIFI` [Network] so Android never routes
 * `192.168.4.1` over Cellular when the ESP32 SoftAP has no internet access, and paces
 * 1436-byte chunks to match ESP32 SPI flash erase timing.
 */
object DirectWiFiHttpClient {

    suspend fun sendGet(
        host: String,
        path: String,
        timeoutMs: Int,
        wifiNetwork: Network?
    ): Pair<Int, ByteArray>? = withContext(Dispatchers.IO) {
        try {
            openSocket(host, 80, timeoutMs, wifiNetwork).use { socket ->
                val req = "GET $path HTTP/1.1\r\nHost: $host\r\nConnection: close\r\nCache-Control: no-cache\r\n\r\n"
                val out = socket.getOutputStream()
                out.write(req.toByteArray(Charsets.UTF_8))
                out.flush()
                readHttpResponse(socket)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun uploadMultipart(
        host: String,
        path: String,
        fieldName: String,
        fileName: String,
        mimeType: String,
        fileData: ByteArray,
        isFirmwareOta: Boolean,
        wifiNetwork: Network?,
        onProgress: ((Float) -> Unit)?
    ): Int = withContext(Dispatchers.IO) {
        val timeoutMs = if (isFirmwareOta) 90_000 else 30_000
        openSocket(host, 80, timeoutMs, wifiNetwork).use { socket ->
            val boundary = "ESP32FormBoundary${UUID.randomUUID().toString().replace("-", "")}"
            val preamble = buildString {
                append("--$boundary\r\n")
                append("Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"\r\n")
                append("Content-Type: $mimeType\r\n\r\n")
            }.toByteArray(Charsets.UTF_8)

            val epilogue = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
            val contentLength = preamble.size + fileData.size + epilogue.size

            val headers = buildString {
                append("POST $path HTTP/1.1\r\n")
                append("Host: $host\r\n")
                append("Content-Type: multipart/form-data; boundary=$boundary\r\n")
                append("Content-Length: $contentLength\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray(Charsets.UTF_8)

            val out = socket.getOutputStream()

            // 1. Write headers + preamble together
            out.write(headers + preamble)
            out.flush()

            // 2. Stream fileData in paced chunks (3 ms per chunk, 25 ms at each
            // 64 KB boundary where the controller erases a flash block).
            val totalBytes = fileData.size
            val chunkSize = if (isFirmwareOta) 1436 else 4096
            var offset = 0
            var lastPct = -1

            while (offset < totalBytes) {
                var end = minOf(offset + chunkSize, totalBytes)
                // Avoid ending a TCP write on 0x0D ('\r') because ESPAsyncWebServer's
                // _parseMultipartPostByte holds '\r' in state EXPECT_FEED1 without flushing
                // _itemBuffer when `last == true`, which can overflow its 1460-byte buffer.
                while (end < totalBytes && end - offset > 1 && fileData[end - 1] == 0x0D.toByte()) {
                    end--
                }
                out.write(fileData, offset, end - offset)
                out.flush()

                val prevBlock = offset / 65536
                offset = end
                val newBlock = offset / 65536

                if (isFirmwareOta) {
                    if (newBlock > prevBlock) {
                        Thread.sleep(25)
                    } else {
                        Thread.sleep(3)
                    }
                }

                val pctInt = ((offset.toFloat() / maxOf(totalBytes, 1).toFloat()) * 100f).toInt()
                if (pctInt != lastPct) {
                    lastPct = pctInt
                    onProgress?.invoke(offset.toFloat() / maxOf(totalBytes, 1).toFloat())
                }
            }

            // 3. Write closing multipart boundary
            out.write(epilogue)
            out.flush()
            onProgress?.invoke(1.0f)

            // 4. Read HTTP response. After a firmware upload the controller
            // reboots as soon as it has answered, and its AsyncWebServer does
            // not promptly close the connection — cap the wait so a successful
            // flash is not stuck here until the 90 s socket timeout.
            if (isFirmwareOta) {
                socket.soTimeout = 15_000
            }
            val resp = readHttpResponse(socket)
            resp?.first ?: -1
        }
    }

    private fun openSocket(host: String, port: Int, timeoutMs: Int, wifiNetwork: Network?): Socket {
        val socket = wifiNetwork?.socketFactory?.createSocket() ?: Socket()
        socket.tcpNoDelay = true
        socket.sendBufferSize = 16384
        socket.soTimeout = timeoutMs
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        return socket
    }

    /** True once headers are complete and the body matches Content-Length / final chunk. */
    private fun isCompleteHttpResponse(bytes: ByteArray): Boolean {
        var sepIdx = -1
        for (i in 0 until bytes.size - 3) {
            if (bytes[i] == 0x0D.toByte() && bytes[i + 1] == 0x0A.toByte() &&
                bytes[i + 2] == 0x0D.toByte() && bytes[i + 3] == 0x0A.toByte()
            ) {
                sepIdx = i
                break
            }
        }
        if (sepIdx < 0) return false
        val headers = bytes.copyOfRange(0, sepIdx).toString(Charsets.UTF_8)
        val bodySize = bytes.size - (sepIdx + 4)
        val contentLength = headers.lineSequence()
            .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toIntOrNull()
        if (contentLength != null) {
            return bodySize >= contentLength
        }
        if (headers.lineSequence().any { it.contains("Transfer-Encoding:", true) && it.contains("chunked", true) }) {
            // Final zero-size chunk terminates with CRLF CRLF after the body start.
            val body = bytes.copyOfRange(sepIdx + 4, bytes.size)
            return body.size >= 5 &&
                body[body.size - 5] == '0'.code.toByte() &&
                body[body.size - 4] == 0x0D.toByte() && body[body.size - 3] == 0x0A.toByte() &&
                body[body.size - 2] == 0x0D.toByte() && body[body.size - 1] == 0x0A.toByte()
        }
        // No length information: headers plus any body bytes is all we get
        // (the server closes per `Connection: close`).
        return bodySize > 0
    }

    private fun readHttpResponse(socket: Socket): Pair<Int, ByteArray>? {
        val rawBuffer = ByteArrayOutputStream()
        val buf = ByteArray(2048)
        val input = socket.getInputStream()
        try {
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                rawBuffer.write(buf, 0, n)
                // Return as soon as a complete response has arrived instead of
                // waiting for the server to close the connection (the ESP32
                // AsyncWebServer often keeps it open, e.g. after /doUpdate).
                if (isCompleteHttpResponse(rawBuffer.toByteArray())) break
            }
        } catch (_: Exception) {
            if (rawBuffer.size() == 0) return null
        }

        val allBytes = rawBuffer.toByteArray()
        if (allBytes.isEmpty()) return null

        var sepIdx = -1
        for (i in 0 until allBytes.size - 3) {
            if (allBytes[i] == 0x0D.toByte() &&
                allBytes[i + 1] == 0x0A.toByte() &&
                allBytes[i + 2] == 0x0D.toByte() &&
                allBytes[i + 3] == 0x0A.toByte()
            ) {
                sepIdx = i
                break
            }
        }

        val headerBytes = if (sepIdx >= 0) allBytes.copyOfRange(0, sepIdx) else allBytes
        val bodyBytes = if (sepIdx >= 0) allBytes.copyOfRange(sepIdx + 4, allBytes.size) else ByteArray(0)

        val headerStr = headerBytes.toString(Charsets.UTF_8)
        val firstLine = headerStr.lineSequence().firstOrNull().orEmpty()
        val statusParts = firstLine.split(" ")
        val statusCode = if (statusParts.size >= 2) statusParts[1].toIntOrNull() ?: -1 else -1

        return statusCode to bodyBytes
    }
}
