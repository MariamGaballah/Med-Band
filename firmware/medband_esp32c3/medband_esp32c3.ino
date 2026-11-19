#include <ArduinoJson.h>
#include "config.h"
#include "sensors.h"
#include "ble_manager.h"
#include "alert_manager.h"

MedBandSensors sensors;
BleManager bleManager;
AlertManager alertManager;

uint32_t telemetryIntervalMs = MEDBAND_DEFAULT_INTERVAL_MS;
uint32_t lastFastPollMs = 0;
uint32_t lastTelemetryMs = 0;
uint32_t lastStatusMs = 0;
uint32_t seqCounter = 0;

void publishTelemetry(bool forceStatus) {
    VitalsReading v = sensors.getVitals();
    MotionReading m = sensors.getMotion();
    HardwareHealth h = sensors.getHealth();

    StaticJsonDocument<256> vitalsDoc;
    vitalsDoc["t"] = "vitals";
    if (v.fingerDetected && v.heartRateBpm > 0) vitalsDoc["hr"] = v.heartRateBpm;
    else vitalsDoc["hr"] = nullptr;
    if (v.spo2Valid) vitalsDoc["spo2"] = v.spo2Percent;
    else vitalsDoc["spo2"] = nullptr;
    vitalsDoc["spo2Valid"] = v.spo2Valid;
    vitalsDoc["sq"] = v.quality;
    vitalsDoc["finger"] = v.fingerDetected;
    if (v.batteryPercent >= 0) vitalsDoc["bat"] = v.batteryPercent;
    vitalsDoc["seq"] = ++seqCounter;

    String vitalsJson;
    serializeJson(vitalsDoc, vitalsJson);
    bleManager.notifyVitals(vitalsJson);

    StaticJsonDocument<256> motionDoc;
    motionDoc["t"] = "motion";
    motionDoc["ax"] = roundf(m.ax * 100.0f) / 100.0f;
    motionDoc["ay"] = roundf(m.ay * 100.0f) / 100.0f;
    motionDoc["az"] = roundf(m.az * 100.0f) / 100.0f;
    motionDoc["gx"] = roundf(m.gx * 10.0f) / 10.0f;
    motionDoc["gy"] = roundf(m.gy * 10.0f) / 10.0f;
    motionDoc["gz"] = roundf(m.gz * 10.0f) / 10.0f;
    motionDoc["mag"] = roundf(m.magG * 100.0f) / 100.0f;
    motionDoc["act"] = m.activity;
    motionDoc["fallStage"] = m.fallStage;
    motionDoc["seq"] = ++seqCounter;

    String motionJson;
    serializeJson(motionDoc, motionJson);
    bleManager.notifyMotion(motionJson);

    if (forceStatus || (millis() - lastStatusMs >= MEDBAND_STATUS_INTERVAL_MS)) {
        lastStatusMs = millis();
        StaticJsonDocument<256> statusDoc;
        statusDoc["t"] = "status";
        statusDoc["max30102"] = h.max30102;
        statusDoc["mpu6050"] = h.mpu6050;
        statusDoc["oled"] = h.oled;
        statusDoc["nfc"] = h.nfc;
        if (h.batteryPercent >= 0) statusDoc["bat"] = h.batteryPercent;
        statusDoc["charging"] = h.charging;
        statusDoc["intervalMs"] = telemetryIntervalMs;
        statusDoc["fw"] = MEDBAND_FW_VERSION;

        String statusJson;
        serializeJson(statusDoc, statusJson);
        bleManager.notifyStatus(statusJson);
    }

    sensors.updateOled(bleManager.isConnected(), alertManager.isEmergencyActive());
}

void handleBleCommand(const String& jsonCommand) {
    StaticJsonDocument<192> doc;
    if (deserializeJson(doc, jsonCommand)) return;
    String cmd = doc["cmd"] | "";

    if (cmd == "ALERT_ON") {
        alertManager.setEmergencyAlert(true);
    } else if (cmd == "ALERT_OFF") {
        alertManager.setEmergencyAlert(false);
    } else if (cmd == "BUZZ_TEST") {
        uint16_t ms = doc["ms"] | 300;
        alertManager.pulseBuzzer(constrain(ms, 50, 3000));
    } else if (cmd == "VIBRATE_TEST") {
        uint16_t ms = doc["ms"] | 300;
        alertManager.pulseVibration(constrain(ms, 50, 3000));
    } else if (cmd == "SET_INTERVAL") {
        uint32_t ms = doc["ms"] | MEDBAND_DEFAULT_INTERVAL_MS;
        telemetryIntervalMs = constrain(ms, 1000, 30000);
    } else if (cmd == "SET_FALL_SENSITIVITY") {
        String mode = doc["mode"] | "BALANCED";
        sensors.setFallSensitivity(mode);
    } else if (cmd == "SYNC_NOW") {
        publishTelemetry(true);
    }
}

void setup() {
    Serial.begin(115200);
    alertManager.begin();
    sensors.begin();
    bleManager.begin(handleBleCommand);
    publishTelemetry(true);
}

void loop() {
    uint32_t now = millis();
    bleManager.loop();
    alertManager.update();

    if (now - lastFastPollMs >= MEDBAND_MOTION_POLL_MS) {
        lastFastPollMs = now;
        sensors.pollFast();
    }

    if (now - lastTelemetryMs >= telemetryIntervalMs) {
        lastTelemetryMs = now;
        sensors.updateVitals();
        publishTelemetry(false);
    }
}
