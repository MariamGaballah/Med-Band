package com.example

import com.example.data.model.FallStage
import com.example.data.model.MovementActivity
import com.example.data.model.SensorQuality
import com.example.hardware.BleProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BleProtocolTest {

    @Test
    fun `parseVitalsJson returns valid heart rate and spo2 when finger detected and spo2Valid true`() {
        val raw = """{"t":"vitals","hr":74,"spo2":98,"spo2Valid":true,"sq":"GOOD","finger":true,"bat":85,"seq":12}"""
        val packet = BleProtocol.parseVitalsJson(raw, 1000L)
        assertNotNull(packet)
        assertEquals(74, packet!!.heartRateBpm)
        assertEquals(98, packet.spo2Percent)
        assertTrue(packet.spo2Valid)
        assertEquals(SensorQuality.GOOD, packet.sensorQuality)
        assertTrue(packet.fingerDetected)
        assertEquals(85, packet.batteryPercent)
        assertEquals(12L, packet.sequence)
    }

    @Test
    fun `parseVitalsJson suppresses heart rate and spo2 when finger is not detected`() {
        val raw = """{"t":"vitals","hr":78,"spo2":97,"spo2Valid":true,"sq":"UNAVAILABLE","finger":false,"bat":60,"seq":2}"""
        val packet = BleProtocol.parseVitalsJson(raw)
        assertNotNull(packet)
        assertNull(packet!!.heartRateBpm)
        assertNull(packet.spo2Percent)
        assertFalse(packet.spo2Valid)
        assertFalse(packet.fingerDetected)
    }

    @Test
    fun `parseVitalsJson hides spo2 when spo2Valid is false`() {
        val raw = """{"t":"vitals","hr":82,"spo2":94,"spo2Valid":false,"sq":"FAIR","finger":true,"bat":70,"seq":9}"""
        val packet = BleProtocol.parseVitalsJson(raw)
        assertNotNull(packet)
        assertEquals(82, packet!!.heartRateBpm)
        assertNull(packet.spo2Percent)
        assertFalse(packet.spo2Valid)
    }

    @Test
    fun `parseMotionJson extracts movement activity and fall stage`() {
        val raw = """{"t":"motion","ax":0.2,"ay":-0.1,"az":2.4,"gx":180.0,"gy":50.0,"gz":10.0,"mag":2.41,"act":"POSSIBLE_FALL","fallStage":"POSSIBLE_FALL","seq":44}"""
        val packet = BleProtocol.parseMotionJson(raw)
        assertNotNull(packet)
        assertEquals(MovementActivity.POSSIBLE_FALL, packet!!.activity)
        assertEquals(FallStage.POSSIBLE_FALL, packet.fallStage)
        assertEquals(2.41, packet.accelMagnitudeG, 0.001)
    }

    @Test
    fun `parseStatusJson handles valid status and rejects wrong packet type`() {
        val statusRaw = """{"t":"status","max30102":"OK","mpu6050":"OK","oled":"OK","nfc":"EXTERNAL_TAG","bat":91,"charging":true,"intervalMs":2000,"fw":"1.0.0"}"""
        val status = BleProtocol.parseStatusJson(statusRaw)
        assertNotNull(status)
        assertEquals("OK", status!!.max30102Status)
        assertTrue(status.isCharging)
        assertEquals(91, status.batteryPercent)

        assertNull(BleProtocol.parseStatusJson("""{"t":"vitals"}"""))
        assertNull(BleProtocol.parseVitalsJson("not-json"))
    }

    @Test
    fun `command builders clamp durations and emit valid JSON`() {
        val buzz = BleProtocol.buildBuzzerTestCommand(9999)
        assertTrue(buzz.contains("\"cmd\":\"BUZZ_TEST\""))
        assertTrue(buzz.contains("\"ms\":3000"))

        val interval = BleProtocol.buildSetIntervalCommand(100)
        assertTrue(interval.contains("\"ms\":1000"))

        val alertOn = BleProtocol.buildAlertOnCommand("EMERGENCY")
        assertTrue(alertOn.contains("\"cmd\":\"ALERT_ON\""))
    }
}
