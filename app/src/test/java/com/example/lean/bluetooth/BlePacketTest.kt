package com.example.lean.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlePacketTest {

    @Test
    fun testParseValidPositiveAnglePacket() {
        // 'L', 'N', Angle: +21.80° (2180 centideg = 0x0884 -> [0x84, 0x08]),
        // Gyro: +15.5°/s (155 = 0x009B -> [0x9B, 0x00]), Status: 0x03, Seq: 42, Reserved: 0
        val raw = byteArrayOf(
            0x4C, 0x4E,
            0x84.toByte(), 0x08.toByte(),
            0x9B.toByte(), 0x00.toByte(),
            0x03,
            42,
            0x00, 0x00
        )

        val packet = BlePacket.parse(raw)
        assertNotNull(packet)
        assertEquals(21.80f, packet!!.leanAngleDegrees, 0.001f)
        assertEquals(15.5f, packet.gyroRollRateDps, 0.001f)
        assertTrue(packet.isMpuOk)
        assertTrue(packet.isValidReading)
        assertEquals(0x03, packet.rawStatusFlags)
        assertEquals(42, packet.sequenceId)
        assertEquals(0, packet.reserved)
    }

    @Test
    fun testParseValidNegativeAnglePacket() {
        // 'L', 'N', Angle: -21.80° (-2180 centideg = 0xF77C -> [0x7C, 0xF7]),
        // Gyro: -32.5°/s (-325 = 0xFEBB -> [0xBB, 0xFE]), Status: 0x03, Seq: 255, Reserved: 0
        val raw = byteArrayOf(
            0x4C, 0x4E,
            0x7C.toByte(), 0xF7.toByte(),
            0xBB.toByte(), 0xFE.toByte(),
            0x03,
            0xFF.toByte(),
            0x00, 0x00
        )

        val packet = BlePacket.parse(raw)
        assertNotNull(packet)
        assertEquals(-21.80f, packet!!.leanAngleDegrees, 0.001f)
        assertEquals(-32.5f, packet.gyroRollRateDps, 0.001f)
        assertTrue(packet.isMpuOk)
        assertTrue(packet.isValidReading)
        assertEquals(255, packet.sequenceId)
    }

    @Test
    fun testParseZeroAnglePacket() {
        // Angle: 0.00°, Gyro: 0.0°/s
        val raw = byteArrayOf(
            0x4C, 0x4E,
            0x00, 0x00,
            0x00, 0x00,
            0x01, // Only MPU OK, valid bit clear
            0,
            0x00, 0x00
        )

        val packet = BlePacket.parse(raw)
        assertNotNull(packet)
        assertEquals(0.0f, packet!!.leanAngleDegrees, 0.001f)
        assertEquals(0.0f, packet.gyroRollRateDps, 0.001f)
        assertTrue(packet.isMpuOk)
        assertFalse(packet.isValidReading)
        assertEquals(0, packet.sequenceId)
    }

    @Test
    fun testParseMaxLeanExtremes() {
        // +65.00° (6500 = 0x1964 -> [0x64, 0x19])
        val rightExtreme = byteArrayOf(
            0x4C, 0x4E,
            0x64.toByte(), 0x19.toByte(),
            0x00, 0x00,
            0x03,
            1,
            0x00, 0x00
        )
        val pRight = BlePacket.parse(rightExtreme)
        assertNotNull(pRight)
        assertEquals(65.0f, pRight!!.leanAngleDegrees, 0.001f)

        // -65.00° (-6500 = 0xE69C -> [0x9C, 0xE6])
        val leftExtreme = byteArrayOf(
            0x4C, 0x4E,
            0x9C.toByte(), 0xE6.toByte(),
            0x00, 0x00,
            0x03,
            2,
            0x00, 0x00
        )
        val pLeft = BlePacket.parse(leftExtreme)
        assertNotNull(pLeft)
        assertEquals(-65.0f, pLeft!!.leanAngleDegrees, 0.001f)
    }

    @Test
    fun testRejectNullOrInvalidLength() {
        assertNull(BlePacket.parse(null))
        assertNull(BlePacket.parse(byteArrayOf()))
        assertNull(BlePacket.parse(ByteArray(9)))
        assertNull(BlePacket.parse(ByteArray(11)))
    }

    @Test
    fun testRejectInvalidHeader() {
        // Header 'X', 'N' instead of 'L', 'N'
        val badHeader1 = byteArrayOf(
            0x58, 0x4E,
            0x00, 0x00, 0x00, 0x00, 0x03, 0x01, 0x00, 0x00
        )
        assertNull(BlePacket.parse(badHeader1))

        // Header 'L', 'X' instead of 'L', 'N'
        val badHeader2 = byteArrayOf(
            0x4C, 0x58,
            0x00, 0x00, 0x00, 0x00, 0x03, 0x01, 0x00, 0x00
        )
        assertNull(BlePacket.parse(badHeader2))
    }
}
