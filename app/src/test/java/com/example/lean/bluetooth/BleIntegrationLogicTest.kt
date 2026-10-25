package com.example.lean.bluetooth

import com.example.lean.data.SensorMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleIntegrationLogicTest {

    @Test
    fun testSensorModeEnumContainsExternalBle() {
        val mode = SensorMode.valueOf("EXTERNAL_BLE")
        assertEquals(SensorMode.EXTERNAL_BLE, mode)
        assertEquals("ESP32 IoT Sensor (BLE)", mode.displayName)
        assertFalse(mode.description.isEmpty())
    }

    @Test
    fun testEsp32ZeroOffsetMath() {
        // Motorcycle placed on stand/mount at +5.4° pitch/roll
        var rawBleAngle = 5.40f
        var esp32ZeroOffset = 0f

        // Initial uncalibrated reading
        assertEquals(5.40f, rawBleAngle - esp32ZeroOffset, 0.001f)

        // User performs "CENTER & START" -> zero offset captures current angle
        esp32ZeroOffset = rawBleAngle
        val calibratedAtCenter = rawBleAngle - esp32ZeroOffset
        assertEquals(0.00f, calibratedAtCenter, 0.001f)

        // Rider leans RIGHT to +30.4° raw
        rawBleAngle = 30.40f
        val rightLean = rawBleAngle - esp32ZeroOffset
        assertEquals(25.00f, rightLean, 0.001f)
        assertTrue("Right lean must be positive", rightLean > 0f)

        // Rider leans LEFT to -19.6° raw
        rawBleAngle = -19.60f
        val leftLean = rawBleAngle - esp32ZeroOffset
        assertEquals(-25.00f, leftLean, 0.001f)
        assertTrue("Left lean must be negative", leftLean < 0f)

        // Reset calibration restores offset to 0.0
        esp32ZeroOffset = 0f
        val resetAngle = rawBleAngle - esp32ZeroOffset
        assertEquals(-19.60f, resetAngle, 0.001f)
    }

    @Test
    fun testBleConnectionStateEnumValues() {
        assertEquals("DISCONNECTED", BleConnectionState.DISCONNECTED.name)
        assertEquals("SCANNING", BleConnectionState.SCANNING.name)
        assertEquals("CONNECTING", BleConnectionState.CONNECTING.name)
        assertEquals("CONNECTED", BleConnectionState.CONNECTED.name)
        assertEquals("DISCONNECTING", BleConnectionState.DISCONNECTING.name)
    }

    @Test
    fun testLeanStateDisplayWhenBleDisconnectedMidRide() {
        val disconnectedBleState = com.example.lean.data.LeanState(
            filteredAngleDegrees = 24.5f,
            isBleSensorActive = true,
            isExternalSensorConnected = false,
            bleConnectionState = BleConnectionState.DISCONNECTED
        )

        assertFalse("Telemetry must not be available when BLE is disconnected", disconnectedBleState.isTelemetryAvailable)
        assertEquals("−−°", disconnectedBleState.displayAngleText)
        assertEquals("SIGNAL LOST", disconnectedBleState.directionText)
        assertEquals(0, disconnectedBleState.displayAngleDegrees)
    }

    @Test
    fun testLeanStateDisplayWhenBleConnected() {
        val connectedBleState = com.example.lean.data.LeanState(
            filteredAngleDegrees = 24.5f,
            isBleSensorActive = true,
            isExternalSensorConnected = true,
            bleConnectionState = BleConnectionState.CONNECTED
        )

        assertTrue("Telemetry must be available when BLE is connected", connectedBleState.isTelemetryAvailable)
        assertEquals("+25°", connectedBleState.displayAngleText)
        assertEquals("RIGHT", connectedBleState.directionText)
        assertEquals(25, connectedBleState.displayAngleDegrees)
    }

    @Test
    fun testLeanStateDisplayInPhoneSensorModes() {
        val phoneSensorState = com.example.lean.data.LeanState(
            filteredAngleDegrees = -18.2f,
            isBleSensorActive = false,
            isExternalSensorConnected = false
        )

        assertTrue("Telemetry must be available in phone sensor mode", phoneSensorState.isTelemetryAvailable)
        assertEquals("−18°", phoneSensorState.displayAngleText)
        assertEquals("LEFT", phoneSensorState.directionText)
        assertEquals(-18, phoneSensorState.displayAngleDegrees)
    }

    @Test
    fun testDataFreshnessCalculation() {
        val now = System.currentTimeMillis()
        val freshPacket = BlePacket(
            leanAngleDegrees = 12.5f,
            gyroRollRateDps = 2.0f,
            isMpuOk = true,
            isValidReading = true,
            rawStatusFlags = 0x03,
            sequenceId = 42,
            reserved = 0,
            timestampMs = now - 500L
        )
        val stalePacket = BlePacket(
            leanAngleDegrees = 12.5f,
            gyroRollRateDps = 2.0f,
            isMpuOk = true,
            isValidReading = true,
            rawStatusFlags = 0x03,
            sequenceId = 41,
            reserved = 0,
            timestampMs = now - 5000L
        )

        val isFresh = (now - freshPacket.timestampMs) <= 3000L
        val isStale = (now - stalePacket.timestampMs) <= 3000L

        assertTrue("Packet within 3000ms must be fresh", isFresh)
        assertFalse("Packet older than 3000ms must be stale", isStale)
    }
}
