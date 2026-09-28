# WiFi Manager

A complete, modern Android application built with **Kotlin**, **Jetpack Compose**, and **Material 3** for scanning nearby Wi-Fi networks, connecting to authorized access points, importing password lists from CSV/TXT files, and monitoring Wi-Fi connection health in the background.

---

## ✨ Features

### 1. Nearby Wi-Fi Scanner
- **Real-Time Scanning**: Discovers nearby 2.4 GHz, 5 GHz, and 6 GHz Wi-Fi networks.
- **Detailed Signal & RF Telemetry**: Displays SSID, RSSI (dBm), calculated signal level (0–4 bars), frequency channel, and encryption capability.
- **Security Identification**: Distinguishes Open, WPA, WPA2-PSK, WPA3-SAE, WEP, and Enterprise (802.1x) networks.
- **Pull-To-Refresh**: Manual refresh button and automatic scanning on app launch.
- **Zero-Crash Resilience**: Gracefully handles disabled Wi-Fi, turned-off location services, and scan throttles on Android 9+.

### 2. Secure Wi-Fi Connect
- **Modern Connection Dialog**: Tap any detected network to initiate connection.
- **Official Android Wi-Fi APIs**: Implements Android 10+ `WifiNetworkSpecifier` and `ConnectivityManager.requestNetwork` alongside system Wi-Fi panel shortcuts.
- **Live State Machine**: Clearly displays `Idle`, `Connecting`, `Connected`, and `Failed` states with intuitive error explanations.
- **Automatic Vault Matching**: Automatically detects if the network exists in your Authorized Credentials Vault and pre-fills the password.

### 3. Password File Import (CSV / TXT)
- **Zero-Permission Document Picker**: Select `.txt` or `.csv` files using the Android system storage picker.
- **Flexible File Formats**:
  ```csv
  SSID,Password
  HomeWiFi,SuperSecret123
  Office_5G,CorpPass2026,WPA3
  ```
  Also supports `SSID=Password` and semicolon/tab delimiters.
- **Safe Review Preview**: Shows all parsed credentials before saving.
- **In-Range Match Highlighting**: Instantly badges imported networks that are currently visible nearby.
- **Strict Privacy**: Imported credentials are never automatically brute-forced, guessed, or sent across the network.

### 4. Background Monitoring (Foreground Service)
- **Optional Toggle**: User-controlled in Settings.
- **Android Official Foreground Service**: Persistent, low-priority notification showing active Wi-Fi status, link speed, and signal strength.
- **One-Tap Stop Action**: Terminate the background monitor directly from the notification shade.
- **Zero Battery Drain**: Uses event-driven `ConnectivityManager.NetworkCallback` rather than aggressive scanning loops.
- **Resume on Boot**: Optional auto-resume on device reboot using Android-approved `RECEIVE_BOOT_COMPLETED` receiver.

### 5. Security & Privacy
- **Encrypted Local Vault**: All passwords stored in Room are encrypted using **AES-GCM**.
- **No Telemetry / No Plaintext Logs**: Passwords are never logged, sent to analytics, or exposed in notifications.
- **Granular Management**: Users can delete individual credentials or clear the entire vault at any time.

---

## 🛠️ Architecture & Tech Stack

- **UI**: Jetpack Compose + Material 3 (Dynamic Color, Dark & Light theme support)
- **Architecture**: MVVM + Repository Pattern
- **Persistence**: Android Room 2.7.0 (with KSP)
- **Concurrency**: Kotlin Coroutines & StateFlow / SharedFlow
- **Cryptography**: AES/GCM/NoPadding with Android KeyStore integration
- **Permissions Handled**:
  - `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`
  - `NEARBY_WIFI_DEVICES` (Android 13+)
  - `POST_NOTIFICATIONS` (Android 13+)
  - `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_SPECIAL_USE` (Android 14+)
  - `RECEIVE_BOOT_COMPLETED`

---

## 🚀 Build & Run Instructions

### Prerequisites
- Android Studio Ladybug / Meerkat (or newer)
- Android SDK 36 (compileSdk 36, minSdk 24)
- JDK 17 or JDK 21

### Building from Command Line
```bash
# Build Debug APK
gradle assembleDebug

# Run Unit / Robolectric Tests
gradle :app:testDebugUnitTest
```
The generated APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.
