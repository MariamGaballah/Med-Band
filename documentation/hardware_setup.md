# MedBand ESP32-C3 Hardware Setup & Firmware Guide

> **Disclaimer:** MedBand is an engineering/educational prototype and wellness monitor. It is **not** a certified medical diagnostic or life-support device. Fall detection thresholds require empirical calibration on your physical enclosure before relying on them.

---

## 1. Bill of Materials
| Component | Role | Interface | Notes |
|---|---|---|---|
| **ESP32-C3** (DevKit / SuperMini / custom PCB) | Main MCU + BLE 5.0 radio | BLE / GPIO / I2C / ADC | Single-core RISC-V |
| **MAX30102** | Heart rate (BPM) & SpO2 optical PPG | I2C (`0x57` via SparkFun lib) | Requires steady skin/finger contact |
| **MPU6050** | 3-axis accelerometer + 3-axis gyroscope | I2C (`0x68` configurable) | Motion activity & multi-stage fall checks |
| **SSD1306 OLED (128x64)** | On-wrist status & vitals display | I2C (`0x3C` configurable) | Displays BPM, SpO2, BLE state, alerts |
| **NFC Tag / Module** | Stores opaque `medband://profile/<uid>/<tokenId>` URI | Passive NTAG213/215 or I2C NT3H2111/PN532 | Passive NTAG sticker/inlay works directly with Android NFC without MCU power |
| **Buzzer** | Audible alerts & SOS alarm | Digital / LEDC PWM GPIO | Use transistor driver if current > 20 mA |
| **Coin Vibration Motor** | Haptic alerts & SOS pulse | Digital / PWM via N-MOSFET | Include flyback diode across motor |
| **Li-Po Battery + Charger (e.g. TP4056)** | Power & battery voltage divider | ADC1 GPIO (`0..3.3V` max) | Use 100k/100k divider (`RATIO = 2.0f`) |

---

## 2. Configurable Pin Assignments (`firmware/medband_esp32c3/config.h`)

**Do NOT assume fixed pins across ESP32-C3 carrier boards.** Inspect your schematic and edit `/firmware/medband_esp32c3/config.h` before flashing:

```cpp
#define MEDBAND_I2C_SDA_PIN          8   // Match your PCB I2C SDA line
#define MEDBAND_I2C_SCL_PIN          9   // Match your PCB I2C SCL line
#define MEDBAND_BUZZER_PIN           3   // Active/passive buzzer driver pin
#define MEDBAND_VIBRATION_PIN        4   // MOSFET gate for vibration motor
#define MEDBAND_BATTERY_ADC_PIN      0   // ADC1 pin (-1 to disable battery reporting)
#define MEDBAND_BATTERY_DIVIDER_RATIO 2.0f

#define MEDBAND_OLED_I2C_ADDR        0x3C
#define MEDBAND_MPU6050_I2C_ADDR     0x68
#define MEDBAND_NFC_I2C_ADDR         0x24
```

### Shared I2C Bus Guidelines
- Connect **MAX30102**, **MPU6050**, and **SSD1306** VCC to **3.3V** (never 5V directly to ESP32-C3 GPIOs).
- Use `4.7k Ohm` pull-up resistors on `SDA` and `SCL` to `3.3V` if breakout boards omit them.
- Verify I2C addresses with an I2C scanner sketch if your OLED (`0x3C` vs `0x3D`) or MPU6050 (`0x68` when `AD0=GND`, `0x69` when `AD0=VCC`) differs.

---

## 3. Required Arduino Libraries
Install these libraries in **Arduino IDE** (Library Manager) or **PlatformIO**:
1. **NimBLE-Arduino** (recommended lightweight BLE stack for ESP32-C3)
2. **SparkFun MAX3010x Pulse and Proximity Sensor Library** (`MAX30105.h`, `heartRate.h`, `spo2_algorithm.h`)
3. **Adafruit MPU6050** + **Adafruit Unified Sensor**
4. **Adafruit SSD1306** + **Adafruit GFX Library**
5. **ArduinoJson** (v6 or v7)

---

## 4. Flashing & Sensor Behavior
1. Open `/firmware/medband_esp32c3/medband_esp32c3.ino` in Arduino IDE 2.x.
2. Select Board: **ESP32C3 Dev Module** (enable **USB CDC On Boot** if viewing serial logs over native USB).
3. Verify `config.h` pin macros match your wiring.
4. Click **Upload** and open Serial Monitor at `115200` baud to confirm initialization status for `MAX30102`, `MPU6050`, `SSD1306`, and BLE advertising (`MedBand-ESP32C3`).
5. **SpO2 Validation Rule**: The firmware sets `"spo2Valid": false` and `"spo2": null` whenever IR contact is below threshold (`< 35000`), finger is lifted, or the Maxim SpO2 algorithm flags invalid quality. The mobile app displays `--` / `Unavailable` instead of inventing numbers.
