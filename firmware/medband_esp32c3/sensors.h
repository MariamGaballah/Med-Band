#pragma once
#include <Arduino.h>

enum class FallStage {
    NONE,
    FREE_FALL_OR_IMPACT,
    ORIENTATION_SHIFT,
    POSSIBLE_FALL
};

struct VitalsReading {
    int heartRateBpm = 0;
    int spo2Percent = 0;
    bool spo2Valid = false;
    bool fingerDetected = false;
    String quality = "UNAVAILABLE"; // GOOD, FAIR, POOR, UNAVAILABLE
    int batteryPercent = -1;
};

struct MotionReading {
    float ax = 0.0f;
    float ay = 0.0f;
    float az = 0.0f;
    float gx = 0.0f;
    float gy = 0.0f;
    float gz = 0.0f;
    float magG = 0.0f;
    String activity = "RESTING"; // RESTING, WALKING, ACTIVE, UNUSUAL_MOVEMENT, POSSIBLE_FALL
    String fallStage = "NONE";   // NONE, FREE_FALL_OR_IMPACT, ORIENTATION_SHIFT, POSSIBLE_FALL
};

struct HardwareHealth {
    String max30102 = "ERROR";
    String mpu6050 = "ERROR";
    String oled = "ERROR";
    String nfc = "NOT_DETECTED";
    int batteryPercent = -1;
    bool charging = false;
};

class MedBandSensors {
public:
    void begin();
    void pollFast();
    void updateVitals();
    void updateOled(bool bleConnected, bool emergencyActive);
    void setFallSensitivity(const String& mode);

    VitalsReading getVitals();
    MotionReading getMotion() const { return _motion; }
    HardwareHealth getHealth() const { return _health; }

private:
    bool probeI2C(uint8_t address);
    void updateBattery();
    void evaluateMotionAndFall(float ax, float ay, float az, float gx, float gy, float gz);

    VitalsReading _vitals;
    MotionReading _motion;
    HardwareHealth _health;

    // Multi-stage experimental fall-pattern state machine
    FallStage _stage = FallStage::NONE;
    uint32_t _stageTimestampMs = 0;
    float _impactThresholdG = 2.6f;
    float _freeFallThresholdG = 0.45f;
    float _gyroShiftDps = 140.0f;

    // Heart rate & SpO2 rolling buffers
    static constexpr int SPO2_BUF_LEN = 100;
    uint32_t _irBuffer[SPO2_BUF_LEN] = {0};
    uint32_t _redBuffer[SPO2_BUF_LEN] = {0};
    int _bufIdx = 0;
    bool _bufFilled = false;
    uint32_t _lastBeatMs = 0;
    float _bpmAvg = 0.0f;
};
