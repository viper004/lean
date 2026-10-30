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
    fun testBleCalibrationStatusEnumParsing() {
        assertEquals(BleCalibrationStatus.IDLE, BleCalibrationStatus.fromByte(0x00))
        assertEquals(BleCalibrationStatus.CALIBRATING, BleCalibrationStatus.fromByte(0x01))
        assertEquals(BleCalibrationStatus.SUCCESS, BleCalibrationStatus.fromByte(0x02))
        assertEquals(BleCalibrationStatus.FAILED, BleCalibrationStatus.fromByte(0x03))
        // Unknown byte falls back to IDLE
        assertEquals(BleCalibrationStatus.IDLE, BleCalibrationStatus.fromByte(0xFF.toByte()))
    }

    @Test
    fun testEsp32SingleSourceOfTruthPacket() {
        // The ESP32 produces calibrated angle directly (e.g. +14.25° after calibration)
        val calibratedPacket = BlePacket(
            leanAngleDegrees = 14.25f,
            gyroRollRateDps = 3.2f,
            isMpuOk = true,
            isValidReading = true,
            rawStatusFlags = 0x03,
            sequenceId = 10,
            reserved = 0
        )
        // Android consumes calibrated value directly without local offset modification
        assertEquals(14.25f, calibratedPacket.leanAngleDegrees, 0.001f)
        assertTrue(calibratedPacket.isValidReading)
    }

    @Test
    fun testLeanStateCalibrationFlags() {
        val idleState = com.example.lean.data.LeanState(
            isBleSensorActive = true,
            bleCalibrationStatus = BleCalibrationStatus.IDLE
        )
        assertFalse(idleState.isBleCalibrating)

        val calibratingState = com.example.lean.data.LeanState(
            isBleSensorActive = true,
            bleCalibrationStatus = BleCalibrationStatus.CALIBRATING
        )
        assertTrue(calibratingState.isBleCalibrating)

        val successState = com.example.lean.data.LeanState(
            isBleSensorActive = true,
            bleCalibrationStatus = BleCalibrationStatus.SUCCESS
        )
        assertFalse(successState.isBleCalibrating)
    }

    @Test
    fun testCalibrationUuidConstants() {
        assertEquals("7b8e0003-7a4d-4f2b-9c3a-6d1e5f000001", LeanBleManager.CALIBRATION_COMMAND_UUID.toString())
        assertEquals("7b8e0004-7a4d-4f2b-9c3a-6d1e5f000001", LeanBleManager.CALIBRATION_STATUS_UUID.toString())
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

    @Test
    fun testRidingProfileEnumValuesAndBytes() {
        val sports = com.example.lean.data.RidingProfile.SPORTS
        val casual = com.example.lean.data.RidingProfile.CASUAL

        assertEquals(0x01.toByte(), sports.id)
        assertEquals(0x02.toByte(), casual.id)

        assertEquals(sports, com.example.lean.data.RidingProfile.fromId(0x01))
        assertEquals(casual, com.example.lean.data.RidingProfile.fromId(0x02))
        // Default fallback is SPORTS
        assertEquals(com.example.lean.data.RidingProfile.SPORTS, com.example.lean.data.RidingProfile.fromId(0x99.toByte()))
    }

    @Test
    fun testRidingProfileUuidConstants() {
        assertEquals("7b8e0005-7a4d-4f2b-9c3a-6d1e5f000001", LeanBleManager.MODE_COMMAND_UUID.toString())
        assertEquals("7b8e0006-7a4d-4f2b-9c3a-6d1e5f000001", LeanBleManager.MODE_STATUS_UUID.toString())
    }

    @Test
    fun testLeanStateProfileSynchronizationProperties() {
        val syncedState = com.example.lean.data.LeanState(
            isBleSensorActive = true,
            isExternalSensorConnected = true,
            ridingProfile = com.example.lean.data.RidingProfile.SPORTS,
            esp32RidingProfile = com.example.lean.data.RidingProfile.SPORTS,
            isProfileSynchronizing = false
        )
        assertTrue(syncedState.isProfileSynchronizedWithEsp32)

        val unsyncedState = com.example.lean.data.LeanState(
            isBleSensorActive = true,
            isExternalSensorConnected = true,
            ridingProfile = com.example.lean.data.RidingProfile.CASUAL,
            esp32RidingProfile = com.example.lean.data.RidingProfile.SPORTS,
            isProfileSynchronizing = true
        )
        assertFalse(unsyncedState.isProfileSynchronizedWithEsp32)
    }
}
