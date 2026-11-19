# MedBand BLE GATT Protocol Specification

All BLE UUIDs and JSON field names are synchronized 1:1 between `/firmware/medband_esp32c3/config.h` and `/app/src/main/java/com/example/hardware/BleProtocol.kt`.

---

## 1. GATT Service & Characteristics

- **Advertised Device Name Prefix**: `MedBand-ESP32C3` (filter accepts prefix `MedBand`)
- **Primary Service UUID**: `4d656442-0001-4c9a-9f10-42616e645376`

| Characteristic | UUID | Properties | Direction | Purpose |
|---|---|---|---|---|
| **Vitals** | `4d656442-0002-4c9a-9f10-42616e645376` | `READ`, `NOTIFY` | ESP32-C3 -> App | Live HR, SpO2, `spo2Valid`, signal quality, finger detect, battery |
| **Motion** | `4d656442-0003-4c9a-9f10-42616e645376` | `READ`, `NOTIFY` | ESP32-C3 -> App | 6-axis accel/gyro, magnitude (`g`), activity state, fall stage |
| **Status** | `4d656442-0004-4c9a-9f10-42616e645376` | `READ`, `NOTIFY` | ESP32-C3 -> App | Hardware self-test (`MAX30102`, `MPU6050`, `OLED`, `NFC`), charging, interval, FW |
| **Command** | `4d656442-0005-4c9a-9f10-42616e645376` | `WRITE`, `WRITE_NO_RESPONSE` | App -> ESP32-C3 | Buzzer/vibration control, alarm state, sync, interval, fall sensitivity |

Standard Client Characteristic Configuration Descriptor (CCCD): `00002902-0000-1000-8000-00805f9b34fb`.

---

## 2. Upstream JSON Payloads (ESP32-C3 -> Mobile App)

### A. Vitals Packet (`CHAR_VITALS_UUID`)
```json
{
  "t": "vitals",
  "hr": 76,
  "spo2": 98,
  "spo2Valid": true,
  "sq": "GOOD",
  "finger": true,
  "bat": 87,
  "seq": 42
}
```
- When no finger/wrist contact is detected or SpO2 calculation is unreliable:
  - `"finger": false` (or `"sq": "POOR"` / `"UNAVAILABLE"`)
  - `"spo2Valid": false`, `"spo2": null`
  - Android parser (`BleProtocol.parseVitalsJson`) sets `heartRateBpm = null` when `!finger` and `spo2Percent = null` when `!spo2Valid`.

### B. Motion Packet (`CHAR_MOTION_UUID`)
```json
{
  "t": "motion",
  "ax": 0.04,
  "ay": -0.11,
  "az": 0.99,
  "gx": 1.2,
  "gy": -0.8,
  "gz": 0.4,
  "mag": 1.00,
  "act": "RESTING",
  "fallStage": "NONE",
  "seq": 108
}
```
- `act` enum: `RESTING`, `WALKING`, `ACTIVE`, `UNUSUAL_MOVEMENT`, `POSSIBLE_FALL`
- `fallStage` enum: `NONE`, `IMPACT_DETECTED`, `ORIENTATION_SHIFT`, `POST_IMPACT_STILLNESS`, `POSSIBLE_FALL`

### C. Device Status Packet (`CHAR_STATUS_UUID`)
```json
{
  "t": "status",
  "max30102": "OK",
  "mpu6050": "OK",
  "oled": "OK",
  "nfc": "EXTERNAL_TAG",
  "bat": 87,
  "charging": false,
  "intervalMs": 2000,
  "fw": "1.0.0"
}
```

---

## 3. Downstream Commands (Mobile App -> ESP32-C3 `CHAR_COMMAND_UUID`)

| Command | Payload | Effect on Firmware |
|---|---|---|
| Activate Alert | `{"cmd":"ALERT_ON","level":"EMERGENCY"}` | Starts buzzer + vibration alarm pattern & updates OLED |
| Silence Alert | `{"cmd":"ALERT_OFF"}` | Stops buzzer + vibration & clears OLED emergency banner |
| Test Buzzer | `{"cmd":"BUZZ_TEST","ms":300}` | Pulses buzzer for `50..3000` ms |
| Test Vibration | `{"cmd":"VIBRATE_TEST","ms":300}` | Pulses vibration motor for `50..3000` ms |
| Set Telemetry Interval | `{"cmd":"SET_INTERVAL","ms":2000}` | Adjusts BLE notification cadence (`1000..30000` ms) |
| Sync Now | `{"cmd":"SYNC_NOW"}` | Immediately emits fresh `vitals`, `motion`, and `status` notifications |
| Fall Sensitivity | `{"cmd":"SET_FALL_SENSITIVITY","mode":"NORMAL"}` | Sets `LOW`, `NORMAL`, or `HIGH` impact/gyro thresholds |

---

## 4. Connection Recovery & Error Handling
- Upon link loss, `MedBandBleManager` tracks `userInitiatedDisconnect`. If unexpected disconnect occurs and auto-reconnect is enabled, Android retries connection using `autoConnect = true` / bounded backoff and logs a `BLE_DISCONNECTED` alert only when active monitoring drops unexpectedly.
- MTU negotiation requests `185` bytes after GATT discovery (`onMtuChanged`) before enabling CCCD notifications sequentially on `VITALS`, `MOTION`, and `STATUS`.
