# Testing & Verification Guide

---

## 1. Automated JVM & Robolectric Unit Tests
Run the Android unit test suite:
```bash
gradle :app:testDebugUnitTest
```
Covered test suites in `/app/src/test/java/com/example/`:
- `BleProtocolTest.kt`: Validates JSON parsing for `vitals`, `motion`, and `status` packets, strict rejection of invalid/unverified SpO2 (`spo2Valid=false` or `!finger`), invalid JSON resilience, and BLE command JSON generation.
- `AlertEvaluatorTest.kt`: Verifies high/low heart rate thresholds, SpO2 gating (never alerts on `POOR` signal or invalid SpO2), low battery warnings, unusual movement, and experimental multi-stage fall alerts.
- `NfcPayloadTest.kt`: Validates `medband://profile/<uid>/<tokenId>` URI construction, regex sanitization, malformed URI rejection, and non-exposure of sensitive fields.
- `ExampleRobolectricTest.kt`: Confirms `R.string.app_name` matches `MedBand`.

---

## 2. Firestore Security Rules Emulator Tests
Tests live in `/firestore.test.js` and run against the local Firebase Firestore Emulator configured in `/firebase.json`:
```bash
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```
Verified scenarios:
- Unauthenticated reads/writes denied (`users`, `medical_profiles`, `measurements`, `alerts`).
- Cross-user `get`, `list`, `create`, `update` denied.
- Owner create + update on `medical_profiles` succeeds when matching schema; malformed `bloodGroup` or forged `userId` denied.
- Immutable history enforcement: updates to `measurements` denied (`allow update: if false`).
- Emergency state lock: updating an already `CANCELLED` or `RESOLVED` emergency denied.

---

## 3. Physical Hardware & Field Checklist
| Test | Expected Result |
|---|---|
| Scan for `MedBand-ESP32C3` | Device appears with RSSI dBm; connecting discovers all 4 GATT characteristics |
| Lift bracelet off wrist | App shows `Unavailable` / `--`, `spo2Valid=false`, no fabricated BPM/SpO2 |
| Place finger steadily on MAX30102 | Live BPM and validated SpO2 update on Home & Health screens and persist to Firestore |
| Trigger Buzzer / Vibration test | ESP32-C3 sounds buzzer / pulses motor cleanly for requested duration |
| Write & Tap NTAG213/215 NFC tag | Tag stores `medband://profile/<uid>/<tokenId>` only; scanning authenticates owner and loads profile |
| Trigger SOS & Cancel within 15s | Countdown cancels cleanly and updates emergency record to `CANCELLED` |
| Confirm SOS with GPS enabled | Captures real coordinates, prepares emergency SMS/call handoff, and queries Google Maps Grounding for nearby hospitals |
