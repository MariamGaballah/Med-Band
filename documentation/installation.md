# Installation & Build Guide (Windows / Android Studio / Arduino IDE / iOS Notes)

---

## 1. Prerequisites on Windows 10 / 11
1. **Java Development Kit (JDK 17 or 21)**: Install Microsoft OpenJDK or Temurin JDK 17+.
2. **Android Studio (Ladybug or newer)**:
   - Install **Android SDK Platform 36**, **Android SDK Build-Tools**, and **Platform-Tools** via SDK Manager.
3. **Node.js 20+ & Firebase CLI** (only required when editing/testing `firestore.rules` locally):
   ```powershell
   npm install -g firebase-tools
   ```
4. **Arduino IDE 2.x** (for ESP32-C3 firmware):
   - Add ESP32 board index URL in *File -> Preferences*:
     `https://espressif.github.io/arduino-esp32/package_esp32_index.json`
   - Install **esp32 by Espressif Systems** via Boards Manager.

---

## 2. Building & Running the Android App
1. Open the root project folder in **Android Studio** (or run from PowerShell/Terminal):
   ```powershell
   gradle :app:assembleDebug
   ```
   *(If using standard Gradle wrapper outside this container: `./gradlew assembleDebug`.)*
2. **Firebase Configuration**:
   - The project includes `/app/google-services.json` and `/app/src/main/res/values/firebase_applet_config.xml` provisioned for Firebase project `gen-lang-client-0830022926`.
   - Ensure your Android signing key's **SHA-1** and **SHA-256** fingerprints are registered in Firebase Console if creating a new keystore.
   - Ensure **Google Sign-In** provider is enabled in Firebase Authentication -> Sign-in method.
3. **Install on Physical Android Device** (recommended for real BLE, NFC, and GPS):
   - Enable **Developer Options -> USB Debugging** on your Android phone.
   - Run `Run 'app'` from Android Studio or install `app/build/outputs/apk/debug/app-debug.apk`.
   - Note: Android Emulators do **not** have physical BLE radios or NFC hardware for talking to ESP32-C3 or NTAG stickers.

---

## 3. Permissions Requested by the Android App
- **Bluetooth (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, legacy `BLUETOOTH`, `ACCESS_FINE_LOCATION` on Android <= 11)**: Required for scanning and connecting to `MedBand-ESP32C3`.
- **Location (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`)**: Required when attaching real GPS coordinates to SOS/emergency events and searching nearby medical facilities.
- **NFC (`android.permission.NFC`)**: Required for reading/writing opaque profile tokens (`medband://profile/<uid>/<tokenId>`) on NTAG tags.
- **Notifications (`POST_NOTIFICATIONS` on Android 13+)**: Required for high-priority health and fall alerts.

---

## 4. Flashing the ESP32-C3 Firmware
1. Open `/firmware/medband_esp32c3/medband_esp32c3.ino` in Arduino IDE.
2. Install required libraries (`NimBLE-Arduino`, `SparkFun MAX3010x`, `Adafruit MPU6050`, `Adafruit SSD1306`, `Adafruit GFX`, `ArduinoJson`).
3. Edit `/firmware/medband_esp32c3/config.h` to match your exact SDA/SCL/Buzzer/Vibration/ADC GPIOs.
4. Connect ESP32-C3 via USB-C and upload.

---

## 5. Cross-Platform / iOS Companion Notes
While this repository provides the native **Android (Kotlin / Jetpack Compose)** mobile client matching this Android Studio/Gradle build environment, deploying an equivalent iOS companion app requires:
- **macOS + Xcode 15+** with an Apple Developer account.
- **BLE (`CoreBluetooth`)**: Add `NSBluetoothAlwaysUsageDescription` to `Info.plist` scanning for service `4d656442-0001-4c9a-9f10-42616e645376`.
- **NFC (`CoreNFC`)**: Enable the **Near Field Communication Tag Reading** capability and add `NFCReaderUsageDescription` + `com.apple.developer.nfc.readersession.formats` (`NDEF`) to `Info.plist`.
- **Same Firebase Project**: Download `GoogleService-Info.plist` from the same Firebase project (`gen-lang-client-0830022926`) and configure URL scheme `medband` for Universal/Custom URL handling.
