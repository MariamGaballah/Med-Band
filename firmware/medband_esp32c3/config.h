#pragma once
#include <Arduino.h>

// ============================================================================
// MedBand ESP32-C3 Hardware Pinout & Configuration
// ============================================================================
// IMPORTANT: GPIO pin assignments vary by carrier board / custom PCB wiring.
// Set each macro below to match your physical ESP32-C3 schematic before
// flashing. Do NOT assume fixed pins without verifying your board layout.
// ============================================================================

#ifndef MEDBAND_I2C_SDA_PIN
#define MEDBAND_I2C_SDA_PIN          8   // Verify against your PCB I2C SDA line
#endif

#ifndef MEDBAND_I2C_SCL_PIN
#define MEDBAND_I2C_SCL_PIN          9   // Verify against your PCB I2C SCL line
#endif

#ifndef MEDBAND_BUZZER_PIN
#define MEDBAND_BUZZER_PIN           3   // PWM / digital output to active or passive buzzer
#endif

#ifndef MEDBAND_VIBRATION_PIN
#define MEDBAND_VIBRATION_PIN        4   // MOSFET gate driving coin vibration motor
#endif

#ifndef MEDBAND_BATTERY_ADC_PIN
#define MEDBAND_BATTERY_ADC_PIN      0   // ADC1 channel connected to voltage divider (-1 if unused)
#endif

#ifndef MEDBAND_BATTERY_DIVIDER_RATIO
#define MEDBAND_BATTERY_DIVIDER_RATIO 2.0f
#endif

#ifndef MEDBAND_OLED_I2C_ADDR
#define MEDBAND_OLED_I2C_ADDR        0x3C
#endif

#ifndef MEDBAND_MPU6050_I2C_ADDR
#define MEDBAND_MPU6050_I2C_ADDR     0x68
#endif

#ifndef MEDBAND_NFC_I2C_ADDR
#define MEDBAND_NFC_I2C_ADDR         0x24 // e.g., PN532 I2C or NT3H2111 (0x55) if connected on bus
#endif

// ============================================================================
// Firmware Identity & BLE GATT UUIDs (MUST match Android BleProtocol.kt)
// ============================================================================
#define MEDBAND_FW_VERSION           "1.0.0"
#define MEDBAND_DEVICE_NAME_PREFIX   "MedBand-ESP32C3"

#define MEDBAND_SERVICE_UUID         "4d656442-0001-4c9a-9f10-42616e645376"
#define MEDBAND_CHAR_VITALS_UUID     "4d656442-0002-4c9a-9f10-42616e645376"
#define MEDBAND_CHAR_MOTION_UUID     "4d656442-0003-4c9a-9f10-42616e645376"
#define MEDBAND_CHAR_STATUS_UUID     "4d656442-0004-4c9a-9f10-42616e645376"
#define MEDBAND_CHAR_COMMAND_UUID    "4d656442-0005-4c9a-9f10-42616e645376"

// Default telemetry interval (configurable via BLE SET_INTERVAL command)
#define MEDBAND_DEFAULT_INTERVAL_MS  2000
#define MEDBAND_MOTION_POLL_MS       50
#define MEDBAND_STATUS_INTERVAL_MS   10000
