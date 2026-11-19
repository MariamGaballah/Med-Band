#include "alert_manager.h"
#include "config.h"

void AlertManager::begin() {
    pinMode(MEDBAND_BUZZER_PIN, OUTPUT);
    pinMode(MEDBAND_VIBRATION_PIN, OUTPUT);
    digitalWrite(MEDBAND_BUZZER_PIN, LOW);
    digitalWrite(MEDBAND_VIBRATION_PIN, LOW);
}

void AlertManager::setEmergencyAlert(bool active) {
    _emergencyActive = active;
    if (!active) {
        _patternHigh = false;
        digitalWrite(MEDBAND_BUZZER_PIN, LOW);
        digitalWrite(MEDBAND_VIBRATION_PIN, LOW);
    } else {
        _lastPatternToggleMs = 0;
    }
}

void AlertManager::pulseBuzzer(uint16_t durationMs) {
    digitalWrite(MEDBAND_BUZZER_PIN, HIGH);
    _buzzerOffAtMs = millis() + durationMs;
}

void AlertManager::pulseVibration(uint16_t durationMs) {
    digitalWrite(MEDBAND_VIBRATION_PIN, HIGH);
    _vibrationOffAtMs = millis() + durationMs;
}

void AlertManager::update() {
    uint32_t now = millis();
    if (_emergencyActive) {
        if (now - _lastPatternToggleMs >= 450) {
            _lastPatternToggleMs = now;
            _patternHigh = !_patternHigh;
            digitalWrite(MEDBAND_BUZZER_PIN, _patternHigh ? HIGH : LOW);
            digitalWrite(MEDBAND_VIBRATION_PIN, _patternHigh ? HIGH : LOW);
        }
        return;
    }

    if (_buzzerOffAtMs > 0 && now >= _buzzerOffAtMs) {
        digitalWrite(MEDBAND_BUZZER_PIN, LOW);
        _buzzerOffAtMs = 0;
    }
    if (_vibrationOffAtMs > 0 && now >= _vibrationOffAtMs) {
        digitalWrite(MEDBAND_VIBRATION_PIN, LOW);
        _vibrationOffAtMs = 0;
    }
}
