package com.example.hardware

import com.example.data.model.DeviceHealthPacket
import com.example.data.model.FallStage
import com.example.data.model.LiveMotionPacket
import com.example.data.model.LiveVitalsPacket
import com.example.data.model.MovementActivity
import com.example.data.model.SensorQuality
import org.json.JSONObject
import java.util.UUID

object BleProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("4d656442-0001-4c9a-9f10-42616e645376")
    val CHAR_VITALS_UUID: UUID = UUID.fromString("4d656442-0002-4c9a-9f10-42616e645376")
    val CHAR_MOTION_UUID: UUID = UUID.fromString("4d656442-0003-4c9a-9f10-42616e645376")
    val CHAR_STATUS_UUID: UUID = UUID.fromString("4d656442-0004-4c9a-9f10-42616e645376")
    val CHAR_COMMAND_UUID: UUID = UUID.fromString("4d656442-0005-4c9a-9f10-42616e645376")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val DEVICE_NAME_PREFIX = "MedBand"

    fun parseVitalsJson(raw: String, timestampMs: Long = System.currentTimeMillis()): LiveVitalsPacket? {
        return try {
            val json = JSONObject(raw)
            if (json.optString("t") != "vitals") return null
            val finger = json.optBoolean("finger", false)
            val hrRaw = if (json.has("hr") && !json.isNull("hr")) json.optInt("hr", -1) else -1
            val spo2Valid = json.optBoolean("spo2Valid", false)
            val spo2Raw = if (spo2Valid && json.has("spo2") && !json.isNull("spo2")) json.optInt("spo2", -1) else -1
            val batRaw = if (json.has("bat") && !json.isNull("bat")) json.optInt("bat", -1) else -1
            LiveVitalsPacket(
                heartRateBpm = hrRaw.takeIf { it in 25..250 && finger },
                spo2Percent = spo2Raw.takeIf { it in 50..100 && spo2Valid && finger },
                spo2Valid = spo2Valid && finger && spo2Raw in 50..100,
                sensorQuality = SensorQuality.fromRaw(json.optString("sq", "UNAVAILABLE")),
                fingerDetected = finger,
                batteryPercent = batRaw.takeIf { it in 0..100 },
                sequence = json.optLong("seq", 0L),
                receivedAtMillis = timestampMs
            )
        } catch (_: Exception) {
            null
        }
    }

    fun parseMotionJson(raw: String, timestampMs: Long = System.currentTimeMillis()): LiveMotionPacket? {
        return try {
            val json = JSONObject(raw)
            if (json.optString("t") != "motion") return null
            LiveMotionPacket(
                accelX = json.optDouble("ax", 0.0),
                accelY = json.optDouble("ay", 0.0),
                accelZ = json.optDouble("az", 0.0),
                gyroX = json.optDouble("gx", 0.0),
                gyroY = json.optDouble("gy", 0.0),
                gyroZ = json.optDouble("gz", 0.0),
                accelMagnitudeG = json.optDouble("mag", 0.0),
                activity = MovementActivity.fromRaw(json.optString("act", "RESTING")),
                fallStage = FallStage.fromRaw(json.optString("fallStage", "NONE")),
                sequence = json.optLong("seq", 0L),
                receivedAtMillis = timestampMs
            )
        } catch (_: Exception) {
            null
        }
    }

    fun parseStatusJson(raw: String): DeviceHealthPacket? {
        return try {
            val json = JSONObject(raw)
            if (json.optString("t") != "status") return null
            val batRaw = if (json.has("bat") && !json.isNull("bat")) json.optInt("bat", -1) else -1
            DeviceHealthPacket(
                max30102Status = json.optString("max30102", "UNKNOWN"),
                mpu6050Status = json.optString("mpu6050", "UNKNOWN"),
                oledStatus = json.optString("oled", "UNKNOWN"),
                nfcStatus = json.optString("nfc", "UNKNOWN"),
                batteryPercent = batRaw.takeIf { it in 0..100 },
                isCharging = json.optBoolean("charging", false),
                intervalMs = json.optInt("intervalMs", 2000),
                firmwareVersion = json.optString("fw", "")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun buildAlertOnCommand(level: String = "EMERGENCY"): String =
        JSONObject().apply {
            put("cmd", "ALERT_ON")
            put("level", level)
        }.toString()

    fun buildAlertOffCommand(): String =
        JSONObject().apply { put("cmd", "ALERT_OFF") }.toString()

    fun buildBuzzerTestCommand(durationMs: Int = 300): String =
        JSONObject().apply {
            put("cmd", "BUZZ_TEST")
            put("ms", durationMs.coerceIn(50, 3000))
        }.toString()

    fun buildVibrationTestCommand(durationMs: Int = 300): String =
        JSONObject().apply {
            put("cmd", "VIBRATE_TEST")
            put("ms", durationMs.coerceIn(50, 3000))
        }.toString()

    fun buildSetIntervalCommand(intervalMs: Int): String =
        JSONObject().apply {
            put("cmd", "SET_INTERVAL")
            put("ms", intervalMs.coerceIn(1000, 30000))
        }.toString()

    fun buildSyncNowCommand(): String =
        JSONObject().apply { put("cmd", "SYNC_NOW") }.toString()

    fun buildSetFallSensitivityCommand(mode: String): String =
        JSONObject().apply {
            put("cmd", "SET_FALL_SENSITIVITY")
            put("mode", mode)
        }.toString()
}
