package com.example.data.model

enum class BleConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    DISCOVERING_SERVICES,
    CONNECTED,
    ERROR
}

enum class SensorQuality {
    GOOD,
    FAIR,
    POOR,
    UNAVAILABLE;

    companion object {
        fun fromRaw(raw: String?): SensorQuality =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNAVAILABLE
    }
}

enum class MovementActivity {
    RESTING,
    WALKING,
    ACTIVE,
    UNUSUAL_MOVEMENT,
    POSSIBLE_FALL;

    companion object {
        fun fromRaw(raw: String?): MovementActivity =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: RESTING
    }
}

enum class FallStage {
    NONE,
    FREE_FALL_OR_IMPACT,
    ORIENTATION_SHIFT,
    POSSIBLE_FALL;

    companion object {
        fun fromRaw(raw: String?): FallStage =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: NONE
    }
}

data class DiscoveredBleDevice(
    val name: String,
    val address: String,
    val rssi: Int
)

data class LiveVitalsPacket(
    val heartRateBpm: Int? = null,
    val spo2Percent: Int? = null,
    val spo2Valid: Boolean = false,
    val sensorQuality: SensorQuality = SensorQuality.UNAVAILABLE,
    val fingerDetected: Boolean = false,
    val batteryPercent: Int? = null,
    val sequence: Long = 0L,
    val receivedAtMillis: Long = 0L
)

data class LiveMotionPacket(
    val accelX: Double = 0.0,
    val accelY: Double = 0.0,
    val accelZ: Double = 0.0,
    val gyroX: Double = 0.0,
    val gyroY: Double = 0.0,
    val gyroZ: Double = 0.0,
    val accelMagnitudeG: Double = 0.0,
    val activity: MovementActivity = MovementActivity.RESTING,
    val fallStage: FallStage = FallStage.NONE,
    val sequence: Long = 0L,
    val receivedAtMillis: Long = 0L
) {
    val gyroMagnitudeDps: Double
        get() = kotlin.math.sqrt(gyroX * gyroX + gyroY * gyroY + gyroZ * gyroZ)
}

data class DeviceHealthPacket(
    val max30102Status: String = "UNKNOWN",
    val mpu6050Status: String = "UNKNOWN",
    val oledStatus: String = "UNKNOWN",
    val nfcStatus: String = "UNKNOWN",
    val batteryPercent: Int? = null,
    val isCharging: Boolean = false,
    val intervalMs: Int = 2000,
    val firmwareVersion: String = ""
)
