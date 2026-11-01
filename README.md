# Tire Pressure Manager — Android & Android Auto (`v2.1.0`)

Kotlin and Jetpack Compose companion application for the open-source **DIY (Do-It-Yourself)** ESP32 Tire Air Pressure Management System over Bluetooth Low Energy (BLE), with full Android Auto support (Android 10 through Android 14+).

## Features

- **270° Off-Road Speedometer Gauge:** Live manifold pressure in PSI or BAR with high-contrast off-road dark theme (`#101318` / `#181C24`).
- **Automated Closed-Loop Inflation & Deflation:** Set target pressure via slider, `+/-` steppers, or quick preset chips, with an animated **Target Reached Celebration** overlay when the target pressure is reached.
- **Full Controller Configuration over BLE:** Edit all 18 ESP32 hardware and safety settings directly from `SettingsScreen`:
  - Device Name, Wi-Fi SSID (combining local Android Wi-Fi scan and **ESP32 BLE Wi-Fi scan** via `command: 9`), and Wi-Fi Password (with Show/Hide toggle)
  - Hotspot Password and BLE Access Code (with Show/Hide toggles)
  - Relay Compressor Pin, Relay Free Pin, and Sensor Pin (ADC1)
  - Pressure Calculation Method (`1`: Raw ADC, `2`: Voltage Divider) and calibration values
  - Sensor Full Scale PSI, Max Pressure PSI, Max Valve Open Seconds, Measure Wait, Precision, Simulation Mode, and Debug Mode
- **Android Auto Integration:** Control inflation, deflation, presets, and emergency stop directly from the vehicle's Android Auto screen (`TiresPressureCarAppService`).
- **Bilingual Localization:** Runtime language switching between English (`values/strings.xml`) and Hebrew (`values-iw/strings.xml`).

## Technology Stack

- **Language:** Kotlin (`JDK 17`)
- **UI Framework:** Jetpack Compose + Material Design 3
- **BLE Transport:** Android BLE GATT with `180`-byte bidirectional chunking (`BleManager.kt`)
- **Automotive:** Jetpack Car App Library (`androidx.car.app`)
- **Target / Compile SDK:** `36` (Min SDK `29` / Android 10+)

## Build & Release

- **Debug APK:**
  ```bash
  ./gradlew assembleDebug
  ```
- **Release Bundle (`.aab`):**
  ```bash
  ./gradlew bundleRelease
  ```
- **Google Play Internal Track CI/CD:** GitHub Actions workflow [`android-play.yml`](.github/workflows/android-play.yml) (see [`docs/android-play-ci.md`](docs/android-play-ci.md)).

## License

Copyright © 2026 Nir Azulay. All rights reserved.
