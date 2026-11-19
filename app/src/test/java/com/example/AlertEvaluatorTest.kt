package com.example

import com.example.data.model.FallStage
import com.example.data.model.LiveMotionPacket
import com.example.data.model.LiveVitalsPacket
import com.example.data.model.MovementActivity
import com.example.data.model.SensorQuality
import com.example.hardware.AlertEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEvaluatorTest {

    @Test
    fun `poor sensor quality emits INFO quality notice and skips threshold alarms`() {
        val packet = LiveVitalsPacket(
            heartRateBpm = 170,
            spo2Percent = 82,
            spo2Valid = false,
            sensorQuality = SensorQuality.POOR,
            fingerDetected = false
        )
        val alerts = AlertEvaluator.evaluateVitals(packet, hrMin = 50L, hrMax = 130L, spo2Min = 92L)
        assertEquals(1, alerts.size)
        assertEquals("SENSOR_QUALITY", alerts.first().category)
        assertFalse(alerts.first().triggersPendingEmergency)
    }

    @Test
    fun `extreme high heart rate and validated low spo2 trigger critical alerts`() {
        val packet = LiveVitalsPacket(
            heartRateBpm = 168,
            spo2Percent = 85,
            spo2Valid = true,
            sensorQuality = SensorQuality.GOOD,
            fingerDetected = true,
            batteryPercent = 12
        )
        val alerts = AlertEvaluator.evaluateVitals(packet, hrMin = 50L, hrMax = 130L, spo2Min = 92L)
        val categories = alerts.map { it.category }.toSet()
        assertTrue("HIGH_HR" in categories)
        assertTrue("LOW_SPO2" in categories)
        assertTrue("LOW_BATTERY" in categories)
        assertTrue(alerts.any { it.triggersPendingEmergency })
    }

    @Test
    fun `low spo2 is ignored when spo2Valid is false`() {
        val packet = LiveVitalsPacket(
            heartRateBpm = 72,
            spo2Percent = 80,
            spo2Valid = false,
            sensorQuality = SensorQuality.FAIR,
            fingerDetected = true
        )
        val alerts = AlertEvaluator.evaluateVitals(packet, hrMin = 50L, hrMax = 130L, spo2Min = 92L)
        assertTrue(alerts.none { it.category == "LOW_SPO2" })
    }

    @Test
    fun `multi-stage fall pattern triggers experimental emergency alert only when enabled`() {
        val motion = LiveMotionPacket(
            accelMagnitudeG = 2.6,
            activity = MovementActivity.POSSIBLE_FALL,
            fallStage = FallStage.POSSIBLE_FALL
        )
        val enabledAlerts = AlertEvaluator.evaluateMotion(motion, fallDetectionEnabled = true)
        assertEquals(1, enabledAlerts.size)
        assertEquals("POSSIBLE_FALL", enabledAlerts.first().category)
        assertTrue(enabledAlerts.first().triggersPendingEmergency)

        val disabledAlerts = AlertEvaluator.evaluateMotion(motion, fallDetectionEnabled = false)
        assertTrue(disabledAlerts.isEmpty())
    }
}
