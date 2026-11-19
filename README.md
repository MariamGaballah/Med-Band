# MedBand — Smart Medical Bracelet | Health in Your Hand

**صحتك في إيدك، واستجابة أسرع وقت الطوارئ**

MedBand is a production-oriented medical bracelet ecosystem pairing an **ESP32-C3 wearable firmware** with an **Android Jetpack Compose mobile application** and a **Firebase Auth + Firestore + Firebase AI Logic backend**.

---

## Key Capabilities
- **Real Bluetooth Low Energy (BLE) Telemetry**: Connects to `MedBand-ESP32C3` over a documented GATT service (`4d656442-0001-4c9a-9f10-42616e645376`) receiving live heart rate, validated SpO2, sensor quality, 6-axis MPU6050 movement/gyro data, battery percentage, and bidirectional alert/sync commands.
- **No Fake / Simulated Readings**: All vitals and movement metrics display live sensor state (`Unavailable` / `Poor Signal` / `Disconnected`) when hardware is absent or finger contact is insufficient.
- **Arabic RTL & English LTR**: Full dynamic language and layout-direction switching across all 11 screens, plus Dark Telemetry and Light Clinical themes.
- **Secure Medical Profile & Encrypted-Pointer NFC**: Stores medical profile data in private user-scoped Cloud Firestore (`users/{uid}/medical_profiles/primary`). Only an opaque pointer URI (`medband://profile/<uid>/<tokenId>`) is written to NFC NDEF tags—no plaintext health PII on tags.
- **Multi-Stage Fall Detection & Emergency Response**: Combines MPU6050 impact + angular rotation + post-impact stillness stages (marked experimental until physically calibrated) with a 15-second confirmation countdown, SOS trigger, GPS coordinate capture, system SMS/dialer handoff, and Google Maps Grounding via Firebase AI Logic for nearby hospitals and clinics.
- **Zero-Trust Firebase Backend**: Authenticated exclusively via Google Sign-In (Jetpack Credential Manager), App Check protected, and guarded by strict schema-enforced `firestore.rules`.

---

## Project Structure
- `/app/` — Native Android application (Kotlin, Jetpack Compose, Material 3, Navigation Compose, DataStore, Android BLE GATT, Android NFC NDEF, FusedLocationProviderClient, Firebase Auth, Cloud Firestore, Firebase AI Logic).
- `/firmware/medband_esp32c3/` — Arduino C++ ESP32-C3 firmware (`config.h`, `sensors.cpp`, `ble_manager.cpp`, `alert_manager.cpp`, `medband_esp32c3.ino`).
- `/firestore.rules`, `/firebase.json`, `/firebase-blueprint.json`, `/firestore.test.js` — Authoritative Firestore database configuration, zero-trust security rules, and emulator test suite.
- `/backend/firebase/firestore.indexes.json` — Composite/collection index definitions.
- `/documentation/` — Hardware wiring, BLE GATT specification, Windows/iOS/Android installation, deployment, and verification guides.

---

## Documentation Index
1. [Hardware Setup & Wiring](/documentation/hardware_setup.md)
2. [BLE GATT Protocol Specification](/documentation/ble_protocol.md)
3. [Installation & Build Guide (Windows / Android / Arduino / iOS Notes)](/documentation/installation.md)
4. [Deployment & Release Guide](/documentation/deployment.md)
5. [Testing & Verification Guide](/documentation/testing.md)
