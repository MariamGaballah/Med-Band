#pragma once
#include <Arduino.h>

class AlertManager {
public:
    void begin();
    void update();

    void setEmergencyAlert(bool active);
    void pulseBuzzer(uint16_t durationMs);
    void pulseVibration(uint16_t durationMs);
    bool isEmergencyActive() const { return _emergencyActive; }

private:
    bool _emergencyActive = false;
    uint32_t _buzzerOffAtMs = 0;
    uint32_t _vibrationOffAtMs = 0;
    uint32_t _lastPatternToggleMs = 0;
    bool _patternHigh = false;
};
