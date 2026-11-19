#include "sensors.h"
#include "config.h"
#include <Wire.h>
#include <MAX30105.h>
#include <heartRate.h>
#include <spo2_algorithm.h>
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <Adafruit_SSD1306.h>

static MAX30105 particleSensor;
static Adafruit_MPU6050 mpu;
static Adafruit_SSD1306 display(128, 64, &Wire, -1);

bool MedBandSensors::probeI2C(uint8_t address) {
    Wire.beginTransmission(address);
    return Wire.endTransmission() == 0;
}

void MedBandSensors::begin() {
    Wire.begin(MEDBAND_I2C_SDA_PIN, MEDBAND_I2C_SCL_PIN);
    Wire.setClock(400000);

    if (particleSensor.begin(Wire, I2C_SPEED_FAST)) {
        particleSensor.setup(0x2A, 4, 2, 100, 411, 4096);
        _health.max30102 = "OK";
    } else {
        _health.max30102 = "ERROR";
    }

    if (mpu.begin(MEDBAND_MPU6050_I2C_ADDR, &Wire)) {
        mpu.setAccelerometerRange(MPU6050_RANGE_8_G);
        mpu.setGyroRange(MPU6050_RANGE_500_DEG);
        mpu.setFilterBandwidth(MPU6050_BAND_21_HZ);
        _health.mpu6050 = "OK";
    } else {
        _health.mpu6050 = "ERROR";
    }

    if (display.begin(SSD1306_SWITCHCAPVCC, MEDBAND_OLED_I2C_ADDR)) {
        _health.oled = "OK";
        display.clearDisplay();
        display.setTextSize(1);
        display.setTextColor(SSD1306_WHITE);
        display.setCursor(0, 0);
        display.println("MedBand ESP32-C3");
        display.println("Initializing...");
        display.display();
    } else {
        _health.oled = "ERROR";
    }

    _health.nfc = probeI2C(MEDBAND_NFC_I2C_ADDR) ? "OK" : "PASSIVE_TAG";
    updateBattery();
}

void MedBandSensors::setFallSensitivity(const String& mode) {
    if (mode == "SENSITIVE") {
        _impactThresholdG = 2.1f;
        _freeFallThresholdG = 0.55f;
        _gyroShiftDps = 110.0f;
    } else if (mode == "CONSERVATIVE") {
        _impactThresholdG = 3.1f;
        _freeFallThresholdG = 0.35f;
        _gyroShiftDps = 180.0f;
    } else {
        _impactThresholdG = 2.6f;
        _freeFallThresholdG = 0.45f;
        _gyroShiftDps = 140.0f;
    }
}

void MedBandSensors::pollFast() {
    if (_health.max30102 == "OK") {
        particleSensor.check();
        while (particleSensor.available()) {
            uint32_t red = particleSensor.getFIFORed();
            uint32_t ir = particleSensor.getFIFOIR();
            particleSensor.nextSample();

            bool fingerOn = (ir > 45000);
            _vitals.fingerDetected = fingerOn;

            if (fingerOn) {
                _redBuffer[_bufIdx] = red;
                _irBuffer[_bufIdx] = ir;
                _bufIdx = (_bufIdx + 1) % SPO2_BUF_LEN;
                if (_bufIdx == 0) _bufFilled = true;

                if (checkForBeat(ir)) {
                    uint32_t now = millis();
                    uint32_t delta = now - _lastBeatMs;
                    _lastBeatMs = now;
                    if (delta > 280 && delta < 2000) {
                        float bpm = 60.0f / (delta / 1000.0f);
                        _bpmAvg = (_bpmAvg <= 0.1f) ? bpm : (_bpmAvg * 0.75f + bpm * 0.25f);
                        _vitals.heartRateBpm = (int)round(_bpmAvg);
                    }
                }
            } else {
                _bufFilled = false;
                _bufIdx = 0;
                _bpmAvg = 0.0f;
                _vitals.heartRateBpm = 0;
                _vitals.spo2Valid = false;
                _vitals.spo2Percent = 0;
                _vitals.quality = "POOR";
            }
        }
    } else {
        _vitals.fingerDetected = false;
        _vitals.spo2Valid = false;
        _vitals.quality = "UNAVAILABLE";
    }

    if (_health.mpu6050 == "OK") {
        sensors_event_t a, g, temp;
        mpu.getEvent(&a, &g, &temp);
        float gxDps = g.gyro.x * 57.2958f;
        float gyDps = g.gyro.y * 57.2958f;
        float gzDps = g.gyro.z * 57.2958f;
        evaluateMotionAndFall(a.acceleration.x, a.acceleration.y, a.acceleration.z, gxDps, gyDps, gzDps);
    }
}

void MedBandSensors::evaluateMotionAndFall(float ax, float ay, float az, float gx, float gy, float gz) {
    _motion.ax = ax;
    _motion.ay = ay;
    _motion.az = az;
    _motion.gx = gx;
    _motion.gy = gy;
    _motion.gz = gz;

    float magMs2 = sqrtf(ax * ax + ay * ay + az * az);
    float magG = magMs2 / 9.80665f;
    float gyroMag = sqrtf(gx * gx + gy * gy + gz * gz);
    _motion.magG = magG;

    uint32_t now = millis();

    // Multi-stage experimental fall-pattern state machine (not single threshold)
    switch (_stage) {
        case FallStage::NONE:
            if (magG < _freeFallThresholdG || magG > _impactThresholdG) {
                _stage = FallStage::FREE_FALL_OR_IMPACT;
                _stageTimestampMs = now;
            }
            break;
        case FallStage::FREE_FALL_OR_IMPACT:
            if (now - _stageTimestampMs > 1500) {
                _stage = FallStage::NONE;
            } else if (gyroMag >= _gyroShiftDps) {
                _stage = FallStage::ORIENTATION_SHIFT;
                _stageTimestampMs = now;
            }
            break;
        case FallStage::ORIENTATION_SHIFT:
            // Require ~2 seconds of post-impact stillness (near 1g, low gyro)
            if (now - _stageTimestampMs >= 2000) {
                if (fabsf(magG - 1.0f) < 0.25f && gyroMag < 25.0f) {
                    _stage = FallStage::POSSIBLE_FALL;
                    _stageTimestampMs = now;
                } else {
                    _stage = FallStage::NONE;
                }
            }
            break;
        case FallStage::POSSIBLE_FALL:
            if (now - _stageTimestampMs > 10000) {
                _stage = FallStage::NONE;
            }
            break;
    }

    if (_stage == FallStage::POSSIBLE_FALL) {
        _motion.activity = "POSSIBLE_FALL";
        _motion.fallStage = "POSSIBLE_FALL";
    } else if (magG > 1.85f || gyroMag > 160.0f) {
        _motion.activity = "UNUSUAL_MOVEMENT";
        _motion.fallStage = (_stage == FallStage::NONE) ? "NONE" : "ORIENTATION_SHIFT";
    } else if (fabsf(magG - 1.0f) > 0.22f || gyroMag > 45.0f) {
        _motion.activity = "ACTIVE";
        _motion.fallStage = "NONE";
    } else if (fabsf(magG - 1.0f) > 0.08f || gyroMag > 15.0f) {
        _motion.activity = "WALKING";
        _motion.fallStage = "NONE";
    } else {
        _motion.activity = "RESTING";
        _motion.fallStage = "NONE";
    }
}

void MedBandSensors::updateVitals() {
    updateBattery();
    if (_health.max30102 != "OK") {
        _vitals.quality = "UNAVAILABLE";
        _vitals.spo2Valid = false;
        return;
    }
    if (!_vitals.fingerDetected) {
        _vitals.quality = "POOR";
        _vitals.spo2Valid = false;
        return;
    }
    if (_bufFilled) {
        int32_t spo2 = 0, heartRate = 0;
        int8_t validSpo2 = 0, validHeartRate = 0;
        maxim_heart_rate_and_oxygen_saturation(
            _irBuffer, SPO2_BUF_LEN, _redBuffer,
            &spo2, &validSpo2, &heartRate, &validHeartRate
        );
        if (validSpo2 == 1 && spo2 >= 70 && spo2 <= 100) {
            _vitals.spo2Percent = spo2;
            _vitals.spo2Valid = true;
            _vitals.quality = "GOOD";
        } else {
            _vitals.spo2Valid = false;
            _vitals.quality = (_vitals.heartRateBpm > 0) ? "FAIR" : "POOR";
        }
    } else {
        _vitals.spo2Valid = false;
        _vitals.quality = (_vitals.heartRateBpm > 0) ? "FAIR" : "POOR";
    }
}

void MedBandSensors::updateBattery() {
    if (MEDBAND_BATTERY_ADC_PIN < 0) {
        _health.batteryPercent = -1;
        _vitals.batteryPercent = -1;
        return;
    }
    uint32_t mv = analogReadMilliVolts(MEDBAND_BATTERY_ADC_PIN);
    float vbat = (mv / 1000.0f) * MEDBAND_BATTERY_DIVIDER_RATIO;
    if (vbat < 2.5f) {
        _health.batteryPercent = -1;
        _vitals.batteryPercent = -1;
        return;
    }
    int pct = (int)constrain(((vbat - 3.30f) / (4.20f - 3.30f)) * 100.0f, 0.0f, 100.0f);
    _health.batteryPercent = pct;
    _vitals.batteryPercent = pct;
}

VitalsReading MedBandSensors::getVitals() {
    return _vitals;
}

void MedBandSensors::updateOled(bool bleConnected, bool emergencyActive) {
    if (_health.oled != "OK") return;
    display.clearDisplay();
    display.setCursor(0, 0);
    display.setTextSize(1);
    display.print("MedBand ");
    display.println(bleConnected ? "[BLE OK]" : "[WAIT]");

    if (emergencyActive) {
        display.setTextSize(2);
        display.println("! SOS !");
        display.setTextSize(1);
    }

    display.print("HR: ");
    if (_vitals.fingerDetected && _vitals.heartRateBpm > 0) {
        display.print(_vitals.heartRateBpm);
        display.println(" BPM");
    } else {
        display.println("--");
    }

    display.print("SpO2: ");
    if (_vitals.spo2Valid) {
        display.print(_vitals.spo2Percent);
        display.println("%");
    } else {
        display.println("N/A");
    }

    display.print("Move: ");
    display.println(_motion.activity);

    if (_health.batteryPercent >= 0) {
        display.print("Bat: ");
        display.print(_health.batteryPercent);
        display.println("%");
    }
    display.display();
}
