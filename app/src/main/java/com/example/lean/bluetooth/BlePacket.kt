package com.example.lean.bluetooth

/**
 * Parsed representation of a 10-byte telemetry packet from the LEAN IoT ESP32 sensor.
 *
 * Packet specification:
 * - Length: exactly 10 bytes
 * - Bytes 0..1: ASCII 'L', 'N' (0x4C, 0x4E)
 * - Bytes 2..3: Signed 16-bit little-endian lean angle in centidegrees (divide by 100 for degrees)
 * - Bytes 4..5: Signed 16-bit little-endian gyro roll rate in tenths of deg/s (divide by 10 for deg/s)
 * - Byte 6: Status bitfield (Bit 0: MPU OK, Bit 1: Calibrated/Valid reading)
 * - Byte 7: Unsigned 8-bit rolling sequence counter (0..255)
 * - Bytes 8..9: Reserved for future expansion (0x00, 0x00)
 */
data class BlePacket(
    val leanAngleDegrees: Float,
    val gyroRollRateDps: Float,
    val isMpuOk: Boolean,
    val isValidReading: Boolean,
    val rawStatusFlags: Int,
    val sequenceId: Int,
    val reserved: Int,
    val timestampMs: Long = System.currentTimeMillis()
) {
    companion object {
        const val PACKET_LENGTH = 10
        const val HEADER_BYTE_0: Byte = 0x4C // 'L'
        const val HEADER_BYTE_1: Byte = 0x4E // 'N'

        /**
         * Safely validates and decodes a raw 10-byte buffer from GATT notification.
         *
         * @param bytes Incoming notification byte array.
         * @return Decoded [BlePacket] if format and headers match, or null if invalid.
         */
        fun parse(bytes: ByteArray?): BlePacket? {
            if (bytes == null || bytes.size != PACKET_LENGTH) {
                return null
            }

            // Verify ASCII header "LN"
            if (bytes[0] != HEADER_BYTE_0 || bytes[1] != HEADER_BYTE_1) {
                return null
            }

            // Bytes 2-3: Signed 16-bit little-endian lean angle in centidegrees
            val rawAngleShort = ((bytes[2].toInt() and 0xFF) or ((bytes[3].toInt() and 0xFF) shl 8)).toShort()
            val leanAngle = rawAngleShort / 100.0f

            // Bytes 4-5: Signed 16-bit little-endian gyro roll rate in 0.1 deg/s
            val rawGyroShort = ((bytes[4].toInt() and 0xFF) or ((bytes[5].toInt() and 0xFF) shl 8)).toShort()
            val gyroRollRate = rawGyroShort / 10.0f

            // Byte 6: Status bitfield
            val status = bytes[6].toInt() and 0xFF
            val mpuOk = (status and 0x01) != 0
            val validReading = (status and 0x02) != 0

            // Byte 7: Unsigned sequence ID (0..255)
            val sequence = bytes[7].toInt() and 0xFF

            // Bytes 8-9: Reserved
            val reservedVal = (bytes[8].toInt() and 0xFF) or ((bytes[9].toInt() and 0xFF) shl 8)

            return BlePacket(
                leanAngleDegrees = leanAngle,
                gyroRollRateDps = gyroRollRate,
                isMpuOk = mpuOk,
                isValidReading = validReading,
                rawStatusFlags = status,
                sequenceId = sequence,
                reserved = reservedVal
            )
        }
    }
}
