# BLE Communication Protocol Documentation (`v1.0` Firmware / `v2.1.0` Apps)

See [`ble/docs/BLE_PROTOCOL.md`](../../ble/docs/BLE_PROTOCOL.md) and [`SPECIFICATION.md`](../../SPECIFICATION.md) in the parent repository for the canonical protocol specification.

## Summary of GATT UUIDs & Chunking
- **Service UUID:** `4fafc201-1fb5-459e-8fcc-c5c9c331914b`
- **Notification Characteristic (`ESP32 → Mobile`):** `beb5483e-36e1-4688-b7f5-ea07361b26a8` (`NOTIFY`)
- **Command Characteristic (`Mobile → ESP32`):** `e3223119-9445-4e96-a4a1-85358c4046a2` (`READ | WRITE`)
- **Bidirectional Chunking:** Payloads exceeding `180` bytes are split into `180`-byte slices (`25 ms` write pacing on mobile, `15 ms` notification pacing on ESP32) and reassembled until JSON brace depth balances to `0`.

## Commands (`Mobile → ESP32`)
- `1`: **Stop** (`{"command": 1}`)
- `2`: **Start / Set Target Pressure** (`{"command": 2, "targetPressure": 32.0}`)
- `3`: **Update Settings** (`{"command": 3, ...}` — supports all 18 controller settings fields plus `ClearAccessCode`)
- `4`: **Simulation Mode** (`{"command": 4, "enabled": true}`)
- `5`: **Log Level** (`{"command": 5, "level": 3}`)
- `6`: **Get Settings** (`{"command": 6}`)
- `7`: **Debug Mode** (`{"command": 7, "enabled": true}`)
- `8`: **Authenticate Access Code** (`{"command": 8, "accessCode": "..."}`)
- `9`: **Scan Wi-Fi Networks** (`{"command": 9, "rescan": true}`)

## Notifications (`ESP32 → Mobile`)
- **Status Update:** `{"Pressure": "28.5", "operationMode": "1", "pressureValid": true, "rawADC": ..., "voltage": ...}`
- **Settings Update:** `{"operationMode": 4, "DeviceName": "...", ...}` (all 18 settings + `CurrentPressure`, `Software_VER`, `IP`, `HasAccessCode`)
- **Target Reached:** `{"type": "target_reached", "targetPressure": 32.0, "currentPressure": 32.0}`
- **Auth State:** `{"type": "auth", "ok": true, "required": false}`
- **Wi-Fi Networks List:** `{"type": "wifi_list", "networks": "SSID|RSSI;..."}` or `{"type": "wifi_list", "scanning": true}`
