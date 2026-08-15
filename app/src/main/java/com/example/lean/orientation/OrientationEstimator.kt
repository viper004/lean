package com.example.lean.orientation

import android.hardware.SensorManager
import kotlin.math.cos
import kotlin.math.sin

/**
 * OrientationEstimator handles sensor fusion and orientation calculation.
 *
 * Algorithms implemented:
 * 1. Fused Mode (Game Rotation Vector / Gyro + Accel complementary filter):
 *    - Integrates gyroscope angular velocity for short-term rotation.
 *    - Uses accelerometer gravity for long-term drift correction.
 * 2. Accelerometer Fallback Mode:
 *    - Uses low-pass filtered gravity estimation.
 */
class OrientationEstimator {

    // Current estimated gravity vector in phone hardware body coordinates
    private var gravityVector: Vector3D = Vector3D(0f, 0f, 9.81f)
    private var isInitialized = false
    private var lastGyroTimestamp: Long = 0L

    // Complementary filter alpha (0.98 gyro integration, 0.02 accel gravity correction)
    private val gyroAlpha = 0.98f

    fun processAccelerometer(x: Float, y: Float, z: Float, alpha: Float = 0.15f) {
        val rawAcc = Vector3D(x, y, z)
        if (!isInitialized) {
            gravityVector = rawAcc
            isInitialized = true
        } else {
            // Low-pass filter to smooth gravity vector estimation
            gravityVector = Vector3D(
                gravityVector.x + alpha * (rawAcc.x - gravityVector.x),
                gravityVector.y + alpha * (rawAcc.y - gravityVector.y),
                gravityVector.z + alpha * (rawAcc.z - gravityVector.z)
            )
        }
    }

    fun processGyroscope(gx: Float, gy: Float, gz: Float, timestampNs: Long) {
        if (lastGyroTimestamp == 0L) {
            lastGyroTimestamp = timestampNs
            return
        }

        val dt = (timestampNs - lastGyroTimestamp) * 1e-9f // nanoseconds to seconds
        lastGyroTimestamp = timestampNs

        if (dt <= 0f || dt > 0.5f) return // Ignore invalid dt gaps

        // Small angle rotation vector from gyro velocities
        val angleX = gx * dt
        val angleY = gy * dt
        val angleZ = gz * dt

        // Rotate current gravity vector by small gyro step
        // Rotation around X, Y, Z axes
        var g = gravityVector

        // Rotate around X
        val cosX = cos(angleX)
        val sinX = sin(angleX)
        g = Vector3D(g.x, g.y * cosX - g.z * sinX, g.y * sinX + g.z * cosX)

        // Rotate around Y
        val cosY = cos(angleY)
        val sinY = sin(angleY)
        g = Vector3D(g.x * cosY + g.z * sinY, g.y, -g.x * sinY + g.z * cosY)

        // Rotate around Z
        val cosZ = cos(angleZ)
        val sinZ = sin(angleZ)
        g = Vector3D(g.x * cosZ - g.y * sinZ, g.x * sinZ + g.y * cosZ, g.z)

        // Complementary filter: blend gyro-rotated gravity with current filtered accel gravity
        gravityVector = Vector3D(
            gyroAlpha * g.x + (1f - gyroAlpha) * gravityVector.x,
            gyroAlpha * g.y + (1f - gyroAlpha) * gravityVector.y,
            gyroAlpha * g.z + (1f - gyroAlpha) * gravityVector.z
        )
    }

    fun processRotationVector(rotationVector: FloatArray) {
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)

        // Rotation matrix maps phone coordinates to world coordinates.
        // In world coordinates, gravity is (0, 0, 9.81).
        // Transpose of rotation matrix converts world gravity to phone coordinates:
        // g_phone = R^T * (0, 0, 9.81)
        val gx = rotationMatrix[6] * 9.81f
        val gy = rotationMatrix[7] * 9.81f
        val gz = rotationMatrix[8] * 9.81f

        gravityVector = Vector3D(gx, gy, gz)
        isInitialized = true
    }

    fun getEstimatedGravity(): Vector3D {
        return gravityVector.normalize()
    }

    fun reset() {
        isInitialized = false
        lastGyroTimestamp = 0L
        gravityVector = Vector3D(0f, 0f, 9.81f)
    }
}
