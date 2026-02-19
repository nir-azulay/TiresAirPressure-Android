# TiresAirPressureManagement - iOS Development Reference Documentation

> **Purpose**: This document provides comprehensive reverse-engineered documentation of the Android app for developing an equivalent iOS application. It is designed as a reference for AI agents and iOS developers.

---

## Table of Contents
1. [App Overview](#1-app-overview)
2. [Architecture](#2-architecture)
3. [BLE Protocol Specification](#3-ble-protocol-specification)
4. [Data Models](#4-data-models)
5. [Screens & User Flows](#5-screens--user-flows)
6. [State Management](#6-state-management)
7. [Persistence & Storage](#7-persistence--storage)
8. [Business Logic](#8-business-logic)
9. [iOS Implementation Guide](#9-ios-implementation-guide)

---

## 1. App Overview

### 1.1 Purpose
The TiresAirPressureManagement app controls an ESP32-based tire air pressure controller via Bluetooth Low Energy (BLE). Users can:
- Monitor real-time tire pressure (PSI)
- Inflate or deflate tires to target pressures
- Configure device settings (WiFi, hardware pins, sensor calibration)
- Manage predefined pressure presets
- Use simulation mode for testing without physical hardware
- View debug information (raw ADC, voltage) for sensor calibration

### 1.2 Device Compatibility
- **Target Device**: ESP32 AirUpDownController (sibling project: AirUpDownControllerV2)
- **Communication**: BLE only (no WiFi direct connection from app)
- **Pressure Unit**: PSI (configurable precision, default 0.5)

### 1.3 Key Features
| Feature | Description |
|---------|-------------|
| Real-time pressure display | Live gauge showing current pressure |
| Inflate/Deflate | Set target pressure, device auto-inflates or deflates |
| Predefined presets | Quick-select pressure buttons (4-16 configurable) |
| Device settings | WiFi, relay pins, sensor calibration |
| Simulation mode | Fake pressure readings for testing |
| Debug mode | Show rawADC and voltage for calibration |
| Multi-language | Hebrew, English (via LocaleHelper) |
| Android Auto | Car interface (iOS: consider CarPlay) |
| Saved devices | Up to 2 devices, auto-reconnect on app start |

---

## 2. Architecture

### 2.1 Pattern: MVVM + Repository

```
┌─────────────────────────────────────────────────────────────────┐
│                         UI Layer (Compose/SwiftUI)               │
│  MainScreen | SettingsScreen | ConnectionScreen | AboutScreen   │
└────────────────────────────┬────────────────────────────────────┘
                              │ collectAsState() / @StateObject
┌────────────────────────────▼────────────────────────────────────┐
│                      ViewModel Layer                              │
│  MainViewModel | SettingsViewModel | DeviceManagementViewModel   │
└────────────────────────────┬────────────────────────────────────┘
                              │ sendCommand() / observe flows
┌────────────────────────────▼────────────────────────────────────┐
│                    Repository Layer                               │
│  BleRepository (wraps BleManager, SavedDevicesManager)           │
└────────────────────────────┬────────────────────────────────────┘
                              │
┌────────────────────────────▼────────────────────────────────────┐
│                      BLE Layer                                   │
│  BleManager (CoreBluetooth equivalent)                           │
│  - GATT connection, scan, read, write, notify                    │
│  - JSON serialization/parsing                                     │
│  - Write queue with retry logic                                   │
└──────────────────────────────────────────────────────────────────┘
```

### 2.2 Data Flow
- **Downstream**: UI → ViewModel → Repository → BleManager → BLE Device
- **Upstream**: BLE Device → BleManager (GATT callbacks) → StateFlow → ViewModel → UI
- **Single BleManager instance** shared across the app (created in MainActivity/AppDelegate)

### 2.3 Project Structure (Android - for reference)

```
app/src/main/java/com/nazulay/tiresairpressuremanagement/
├── MainActivity.kt              # Entry, screen routing, single BleManager
├── data/
│   ├── ble/
│   │   ├── BleManager.kt        # All BLE logic (~1800 lines)
│   │   └── ConnectionState.kt   # enum: Connected, Disconnected, etc.
│   ├── local/
│   │   ├── AppSettingsManager.kt   # Singleton, SharedPreferences
│   │   └── SavedDevicesManager.kt  # Up to 2 saved devices
│   └── repository/
│       └── BleRepository.kt     # Wraps BleManager + SavedDevicesManager
├── domain/models/
│   ├── BleCommand.kt           # Sealed class for all commands
│   ├── BleDevice.kt            # name, address, rssi
│   ├── DeviceStatus.kt         # pressure, operationMode, rawADC?, voltage?
│   ├── DeviceSettings.kt       # Full device config
│   ├── DeviceSettingsResponse.kt  # JSON response (string fields)
│   └── AppSettings.kt          # App-only settings
├── ui/screens/
│   ├── MainScreen.kt           # Pressure gauge, controls
│   ├── ConnectionManagementScreen.kt
│   ├── settings/SettingsScreen.kt, SettingsViewModel.kt
│   ├── main/MainViewModel.kt
│   └── device_management/
├── auto/                       # Android Auto (CarPlay equivalent)
└── utils/
    ├── BleConstants.kt         # UUIDs, MTU, modes
    └── LocaleHelper.kt
```

---

## 3. BLE Protocol Specification

### 3.1 UUIDs (Critical - must match exactly)

| Purpose | UUID | Notes |
|---------|------|-------|
| **Service** | `4fafc201-1fb5-459e-8fcc-c5c9c331914b` | Scan filter |
| **Notification Characteristic** | `beb5483e-36e1-4688-b7f5-ea07361b26a8` | Device → App |
| **Command Characteristic** | `e3223119-9445-4e96-a4a1-85358c4046a2` | App → Device |
| **Client Config Descriptor** | `00002902-0000-1000-8000-00805f9b34fb` | Enable notifications |

### 3.2 Connection Parameters
- **MTU**: Request 517 bytes
- **Scan**: Filter by service UUID `4fafc201-1fb5-459e-8fcc-c5c9c331914b`
- **Encoding**: UTF-8 JSON

### 3.3 Message Format: App → Device (Commands)

All commands are JSON objects written to the Command characteristic.

#### Command 1: Stop
```json
{
  "command": 1,
  "type": "command"
}
```

#### Command 2: Start Target Pressure (Inflate/Deflate)
```json
{
  "command": 2,
  "targetPressure": 30.0,
  "type": "command"
}
```
- `targetPressure`: Float, PSI value (e.g., 30.0)

#### Command 3: Update Settings
```json
{
  "command": 3,
  "type": "settings",
  "DeviceName": "esp32-AirUpDownController",
  "wifissid": "MyWiFi",
  "wifipassword": "password123",
  "RelayCompressorPin": 25,
  "RelayFreePin": 26,
  "SensorPin": 27,
  "MaxSecForStatus": 30,
  "SensorValueAt0PSI": 412.000,
  "SensorValueAt50PSI": 3182.000,
  "MessureWait": 2.0,
  "Precision": 0.5,
  "BTPinCode": "1234",
  "SimulationMode": false,
  "PressureCalcMethod": 1
}
```
- **Critical**: `SensorValueAt0PSI` and `SensorValueAt50PSI` must be sent as **numbers** (not strings) with 3 decimal precision
- After this command, device **restarts** (~3 seconds)
- All fields are required when sending (app sends full object)

#### Command 4: Simulation Mode
```json
{
  "command": 4,
  "enabled": true,
  "type": "command"
}
```

#### Command 5: Log Level (optional, rarely used)
```json
{
  "command": 5,
  "level": 3,
  "type": "command"
}
```
- Level: 0=NONE, 1=ERROR, 2=WARNING, 3=INFO, 4=DEBUG, 5=VERBOSE

#### Command 6: Get Settings
```json
{
  "command": 6,
  "type": "command"
}
```
- Device responds with full settings via Notification characteristic

#### Command 7: Debug Mode
```json
{
  "command": 7,
  "enabled": true,
  "type": "command"
}
```
- When enabled, device includes `rawADC` and `voltage` in status updates
- **Must be sent on every connection** (device does not persist this; resets on restart)
- **Must be sent after device restarts** from settings save

### 3.4 Message Format: Device → App (Notifications)

#### Type A: Status Update
```json
{
  "Pressure": 28.5,
  "operationMode": 2,
  "rawADC": "1850",
  "voltage": "1.234"
}
```
- `Pressure` or `CurrentPressure`: Float, current PSI
- `operationMode`: 1=Stop, 2=Inflate, 3=Deflate
- `rawADC`, `voltage`: Only present when **device debug mode is enabled** (command 7)
- Note: ESP32 may send `rawADC` and `voltage` as strings in JSON

#### Type B: Full Settings (on connect or command 6)
```json
{
  "operationMode": 4,
  "DeviceName": "esp32-AirUpDownController",
  "wifissid": "MyWiFi",
  "wifipassword": "",
  "RelayCompressorPin": "25",
  "RelayFreePin": "26",
  "SensorPin": "27",
  "MaxSecForStatus": "30",
  "SensorValueAt0PSI": "412.000",
  "SensorValueAt50PSI": "3182.000",
  "MessureWait": "2.0",
  "Precision": "0.5",
  "BTPinCode": "1234",
  "CurrentPressure": "28.5",
  "Software_VER": "1.04",
  "IP": "192.168.1.100",
  "SimulationMode": "false",
  "PressureCalcMethod": "1"
}
```
- Identified by presence of `DeviceName` field
- Numeric fields may arrive as strings (parse with toFloatOrNull())

#### Type C: Target Reached
```json
{
  "type": "target_reached",
  "targetPressure": 30.0,
  "currentPressure": 30.2
}
```

### 3.5 Message Parsing Logic (Priority Order)
1. If `type` == `"target_reached"` → Handle target reached event
2. If `DeviceName` present → Parse as full settings
3. If `Pressure` or `CurrentPressure` AND `operationMode` present → Parse as status update
4. Fallback: Try Moshi/JSON decoder for DeviceSettingsResponse, then DeviceStatus

### 3.6 Chunked Messages
- Device may send large JSON in multiple chunks
- Buffer until message ends with `}` before parsing
- Android uses `incompleteBLEMessage += chunk`

### 3.7 Write Queue & Retry
- **Queue**: Commands are queued; process one at a time
- **Delay**: ~50ms between writes (Android)
- **Retry**: On GATT status 201 (prior command not finished), retry after 200ms
- **Disconnect**: On status 133, disconnect and cleanup

### 3.8 Operation Modes (Device)
| Value | Constant | Meaning |
|-------|----------|---------|
| 1 | MODE_STOP | Idle |
| 2 | MODE_INFLATE | Inflating to target |
| 3 | MODE_DEFLATE | Deflating to target |
| 4 | MODE_SETTINGS | Settings response (not a real mode) |

---

## 4. Data Models

### 4.1 BleCommand (Sealed Class / Enum with Associated Values)

```swift
// Swift equivalent
enum BleCommand {
    case stop
    case startTargetPressure(Float)
    case updateSettings(DeviceSettings)
    case simulationMode(Bool)
    case requestSettings
    case requestStatus
    case debugMode(Bool)
}
```

| Command | command int | type string | Extra |
|---------|-------------|-------------|-------|
| Stop | 1 | "command" | - |
| StartTargetPressure | 2 | "command" | targetPressure: Float |
| UpdateSettings | 3 | "settings" | All DeviceSettings fields |
| SimulationMode | 4 | "command" | enabled: Bool |
| RequestSettings | 6 | "command" | - |
| RequestStatus | 7 | "command" | - |
| DebugMode | 7 | "command" | enabled: Bool |

### 4.2 DeviceStatus

```swift
struct DeviceStatus {
    let pressure: Float           // Current pressure in PSI
    let operationMode: Int        // 1=stop, 2=inflate, 3=deflate
    let rawADC: Int?              // Debug: raw ADC value (when debug mode on)
    let voltage: Float?           // Debug: voltage in V (when debug mode on)
}
```

### 4.3 DeviceSettings

```swift
struct DeviceSettings {
    var operationMode: Int = 4
    var deviceName: String = ""
    var wifiSsid: String = ""
    var wifiPassword: String = ""
    var relayCompressorPin: Int = 0
    var relayFreePin: Int = 0
    var sensorPin: Int = 0
    var maxSecForStatus: Int = 30
    var sensorValueAt0PSI: Float = 0.228
    var sensorValueAt50PSI: Float = 2.853
    var measureWait: Float = 2.0
    var precision: Float = 1.0
    var btPinCode: String = "1234"
    var currentPressure: Float = 0
    var softwareVersion: String = ""
    var ipAddress: String = ""
    var simulationMode: Bool = false
    var pressureCalcMethod: Int = 1  // 1=ADC, 2=voltage
    // Predefined pressures 1-8 (optional, from device)
}
```

### 4.4 AppSettings (App-Only, Not Sent to Device)

```swift
struct AppSettings {
    var themeMode: Int = 0
    var simulationMode: Bool = false
    var predefinedPressures: [Float] = [8, 10, 12, 16, 20, 30, 38, 40]
    var minPressureCount: Int = 4
    var maxPressureCount: Int = 8
    var debugMode: Bool = false
}
```

### 4.5 BleDevice (Scan Result)

```swift
struct BleDevice {
    let name: String
    let address: String   // MAC address
    let rssi: Int
}
```

### 4.6 ConnectionState

```swift
enum ConnectionState {
    case disconnected
    case connecting
    case connected
    case scanning
    case bluetoothDisabled
    case permissionDenied
    case error
}
```

---

## 5. Screens & User Flows

### 5.1 Screen Enum / Flow

```
SPLASH (3 sec) ──┬── Not connected ──► CONNECTION
                 │
                 └── Connected ──► MAIN

MAIN ──► Settings | About | Disconnect ──► CONNECTION
     └── CONNECTION (manage devices)
     └── SETTINGS (app + device settings)
     └── DEVICE_MANAGEMENT (delete/reorder saved devices)
```

### 5.2 MainScreen
- **Pressure gauge**: Circular progress, current pressure, target pressure
- **Preset buttons**: Grid of predefined pressures (from AppSettings)
- **Inflate/Deflate**: Set target, device auto-operates
- **Stop button**: Sends command 1
- **Debug info**: When debugMode && rawADC != nil && voltage != nil: show yellow text
- **Connection status card**: Device name, connection state
- **Navigation**: Settings, About, Disconnect

### 5.3 ConnectionManagementScreen
- **Scan**: Start BLE scan (filter by service UUID)
- **Device list**: Show name, address, RSSI
- **Connect**: Tap device → connect → save to SavedDevicesManager → navigate to Main
- **Auto-connect**: On app start, connect to saved devices (up to 3 attempts, 5s apart)

### 5.4 SettingsScreen
- **App Settings**: Predefined pressures, language, (theme fixed to dark)
- **Device Settings** (only when connected):
  - Device name
  - WiFi SSID, password (with scan)
  - Hardware: relay pins, sensor pin, maxSecForStatus
  - Calibration: sensorValueAt0PSI, sensorValueAt50PSI (3 decimal precision)
  - measureWait, precision, btPinCode
  - pressureCalcMethod: 1=ADC, 2=voltage
  - Simulation mode toggle
  - Debug mode toggle
- **Save**: Sends UpdateSettings → device restarts → disconnect → wait 3s → reconnect
- **After save**: Send DebugMode command again (device loses it on restart)

### 5.5 DeviceManagementScreen
- List saved devices (max 2)
- Delete, reorder
- Uses SavedDevicesManager

### 5.6 AboutScreen
- App version, info

---

## 6. State Management

### 6.1 BleManager Exposes (StateFlow / Combine Publisher)

| Flow | Type | Description |
|------|------|-------------|
| connectionState | ConnectionState | Current connection state |
| connectedDeviceName | String? | Name of connected device |
| deviceStatus | DeviceStatus? | Latest pressure, operationMode, rawADC, voltage |
| deviceSettings | DeviceSettings? | Full device config (from settings response) |
| scanResults | List<BleDevice> | Scanned devices |
| currentPressure | Float | Extracted from deviceStatus |
| isInflating | Boolean | operationMode == 2 or 3 |
| targetReachedEvent | SharedFlow | Emits when target_reached received |

### 6.2 Critical: Debug Mode Sync
- **On connection**: When connectionState becomes Connected, wait ~1.5s, then send `DebugMode(enabled: appSettings.debugMode)`
- **After settings save**: Device restarts. After reconnect, send DebugMode again
- **Track wasConnected**: Only send on transition to Connected (not on every state emission)

### 6.3 Preserving rawADC/voltage on Settings Update
- When full settings received (DeviceName present), deviceStatus is updated
- **Must preserve** existing rawADC and voltage from previous deviceStatus
- Otherwise they get overwritten with null and debug display disappears

---

## 7. Persistence & Storage

### 7.1 App Settings (UserDefaults / equivalent)

| Key | Type | Default |
|-----|------|---------|
| theme_mode | Int | 2 (dark) |
| simulation_mode | Bool | false |
| debug_mode | Bool | false |
| predefined_pressure_ | String | "8,10,12,16,20,30,38,40" (comma-separated) |
| min_pressure_count | Int | 4 |
| max_pressure_count | Int | 8 |

### 7.2 Saved Devices (max 2)

| Key | Type |
|-----|------|
| device_1 | String (MAC address) |
| device_1_name | String |
| device_2 | String (MAC address) |
| device_2_name | String |

- device_1 = most recently connected
- On new connection: new device becomes device_1, old device_1 → device_2

### 7.3 Device Settings Cache (from BLE, not user-editable directly)
- Stored in SharedPreferences "DeviceSettings" - keys like selectedDeviceAddress, deviceName, wifiSsid, etc.
- Used to pre-fill UI when loading settings screen
- Overwritten when device sends full settings

### 7.4 Language
- LocaleHelper: getSelectedLanguage(), setLocale(), getSupportedLanguages()
- Stored in default SharedPreferences
- Keys may vary; check LocaleHelper implementation

---

## 8. Business Logic

### 8.1 Pressure Operations
1. User selects target pressure (preset or manual)
2. App sends `StartTargetPressure(targetPressure)`
3. Device inflates or deflates automatically
4. Device sends status updates with Pressure, operationMode
5. When target reached, device sends `target_reached`
6. App plays sound, shows notification (optional)
7. User can tap Stop anytime → `Stop` command

### 8.2 Calibration
- `sensorValueAt0PSI`, `sensorValueAt50PSI`: Linear mapping for pressure calculation
- pressureCalcMethod 1: ADC values; 2: voltage values
- Sent in UpdateSettings; device uses for internal calculation
- App displays values in settings; user can edit (3 decimal precision)

### 8.3 Simulation Mode
- Toggle in settings
- Sends `SimulationMode(enabled)` immediately (no save needed for this)
- Device simulates pressure changes (+0.5 inflate, -0.3 deflate per reading)
- Persistent on device

### 8.4 Auto-Connect Flow
1. App start → get saved devices from SavedDevicesManager
2. If any, call connectToSavedDevices()
3. Try connect to first device
4. On failure: retry up to 3 times, 5 seconds apart
5. On success: navigate to Main

### 8.5 Settings Save Flow
1. User taps Save
2. Send UpdateSettings with all device settings
3. Wait 2 seconds
4. Disconnect
5. Wait 3 seconds (device restarting)
6. Reconnect (forceReconnect callback)
7. After 3 seconds, send DebugMode(appSettings.debugMode)

### 8.6 Predefined Pressures
- Stored in AppSettings
- Count: min 4, max 16, must be even
- Default: [8, 10, 12, 16, 20, 30, 38, 40]
- Displayed as quick-select buttons on MainScreen

---

## 9. iOS Implementation Guide

### 9.1 CoreBluetooth Setup
```swift
// Service UUID for scan filter
let serviceUUID = CBUUID(string: "4fafc201-1fb5-459e-8fcc-c5c9c331914b")
let notifyCharUUID = CBUUID(string: "beb5483e-36e1-4688-b7f5-ea07361b26a8")
let commandCharUUID = CBUUID(string: "e3223119-9445-4e96-a4a1-85358c4046a2")
let clientConfigUUID = CBUUID(string: "00002902-0000-1000-8000-00805f9b34fb")
```

### 9.2 Required Capabilities
- Bluetooth (Central role)
- Background modes: Bluetooth LE accessories (if needed)

### 9.3 Info.plist
- NSBluetoothAlwaysUsageDescription
- NSBluetoothPeripheralUsageDescription (iOS 12 and earlier)

### 9.4 State Management (Swift)
- Use `@Published` or Combine `CurrentValueSubject` for reactive state
- Equivalent to StateFlow: `CurrentValueSubject<DeviceStatus?, Never>`
- Collect in SwiftUI: `@StateObject` ViewModel with `@Published` properties

### 9.5 JSON
- Use `Codable` or `JSONSerialization`
- For flexible parsing (string vs number): use `JSONDecoder` with custom decoding or manual `JSONSerialization.jsonObject`

### 9.6 CarPlay (Optional)
- Android has Android Auto; iOS equivalent is CarPlay
- CarPlay template: List for presets, gauge for pressure
- Requires CarPlay entitlement from Apple

### 9.7 Permissions
- Bluetooth: Request when starting scan/connect
- Location (iOS 13+): May be required for BLE scan on some versions

### 9.8 Critical Implementation Notes
1. **Sensor calibration values**: Send as numbers with 3 decimals, e.g. `3182.000` not `"3182.000"`
2. **Debug mode**: Send command 7 on every connection and after device restart
3. **Chunked messages**: Buffer notification data until `}` received
4. **Preserve rawADC/voltage**: When receiving full settings, keep previous deviceStatus.rawADC and voltage
5. **Write queue**: Implement queue to avoid GATT 201 errors
6. **operationMode**: Device uses 1,2,3 for stop/inflate/deflate (not 0,1,2)

### 9.9 Testing Checklist
- [ ] Scan finds device with service UUID
- [ ] Connect and receive settings on first connection
- [ ] Send Stop, StartTargetPressure
- [ ] Receive status updates with pressure
- [ ] Target reached notification
- [ ] Update settings, device restarts, reconnect
- [ ] Debug mode: enable, see rawADC/voltage; restart app, still works
- [ ] Simulation mode toggle
- [ ] Saved devices: connect, close app, reopen, auto-connect
- [ ] Predefined pressures: change count, values persist

---

## Appendix A: Exact JSON Examples (From Android)

### Send: UpdateSettings
```json
{"command":3,"DeviceName":"esp32-AirUpDownController","wifissid":"","wifipassword":"","RelayCompressorPin":25,"RelayFreePin":26,"SensorPin":27,"MaxSecForStatus":30,"SensorValueAt0PSI":412.0,"SensorValueAt50PSI":3182.0,"MessureWait":2.0,"Precision":0.5,"BTPinCode":"1234","SimulationMode":false,"PressureCalcMethod":1,"type":"settings"}
```

### Send: DebugMode
```json
{"command":7,"enabled":true,"type":"command"}
```

### Send: StartTargetPressure
```json
{"command":2,"targetPressure":30.0,"type":"command"}
```

### Receive: Status (with debug)
```json
{"Pressure":28.5,"operationMode":2,"rawADC":"1850","voltage":"1.234"}
```

### Receive: Target Reached
```json
{"type":"target_reached","targetPressure":30.0,"currentPressure":30.1}
```

---

## Appendix B: Version Info
- **App Version**: 1.3.9 (as of this documentation)
- **Min SDK**: 34 (Android)
- **Target SDK**: 35 (Android)
- **Kotlin**: 1.9.0
- **Compose**: BOM 2023.10.00

---

*Document generated from reverse engineering of TiresAirPressureManagement Android app. For ESP32 device protocol details, see AirUpDownControllerV2/docs/BLE_PROTOCOL.md.*
