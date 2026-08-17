package com.example.lean.orientation

import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * OrientationEstimator tracks device gravity vector in phone body coordinates.
 * Supports:
 * 1. GAME_ROTATION_VECTOR / ROTATION_VECTOR (Android OS Fused 3D Matrix)
 * 2. FUSED_GYRO_ACCEL (Complementary Filter with dynamic accelerometer weighting)
 * 3. ACCEL_ONLY (Low-pass filtered gravity tilt)
 */
class OrientationEstimator {

    private var gravityVector: Vector3D = Vector3D(0f, 0f, 9.81f)
    private var isInitialized = false
    private var isRotationVectorActive = false
    private var lastGyroTimestamp: Long = 0L

    fun processAccelerometer(x: Float, y: Float, z: Float, alpha: Float = 0.15f) {
        if (isRotationVectorActive) return // Primary rotation vector sensor is active

        val rawAcc = Vector3D(x, y, z)
        val accelMag = rawAcc.length()

        if (!isInitialized) {
            gravityVector = rawAcc
            isInitialized = true
            return
        }

        // Calculate deviation from 1G (9.81 m/s²)
        val dev = abs(accelMag - 9.81f)

        // Dynamic alpha: when total acceleration is close to 1G, trust accelerometer gravity direction.
        // During dynamic acceleration, braking, or sustained cornering centrifugal force, lower accelerometer weight
        // to prevent dynamic acceleration from pulling the lean angle toward 0°.
        val dynamicAlpha = when {
            dev <= 1.0f -> alpha
            dev <= 2.5f -> (alpha * (1.0f - (dev - 1.0f) / 1.5f)).coerceIn(0.005f, alpha)
            else -> 0.005f
        }

        gravityVector = Vector3D(
            gravityVector.x + dynamicAlpha * (rawAcc.x - gravityVector.x),
            gravityVector.y + dynamicAlpha * (rawAcc.y - gravityVector.y),
            gravityVector.z + dynamicAlpha * (rawAcc.z - gravityVector.z)
        )
    }

    fun processGyroscope(gx: Float, gy: Float, gz: Float, timestampNs: Long) {
        if (isRotationVectorActive) return // Primary rotation vector sensor is active

        if (lastGyroTimestamp == 0L) {
            lastGyroTimestamp = timestampNs
            return
        }

        val dt = (timestampNs - lastGyroTimestamp) * 1e-9f
        lastGyroTimestamp = timestampNs

        if (dt <= 0f || dt > 0.5f) return

        val angleX = gx * dt
        val angleY = gy * dt
        val angleZ = gz * dt

        var g = gravityVector

        val cosX = cos(angleX)
        val sinX = sin(angleX)
        g = Vector3D(g.x, g.y * cosX + g.z * sinX, -g.y * sinX + g.z * cosX)

        val cosY = cos(angleY)
        val sinY = sin(angleY)
        g = Vector3D(g.x * cosY - g.z * sinY, g.y, g.x * sinY + g.z * cosY)

        val cosZ = cos(angleZ)
        val sinZ = sin(angleZ)
        g = Vector3D(g.x * cosZ + g.y * sinZ, -g.x * sinZ + g.y * cosZ, g.z)

        // Update orientation state directly from integrated gyro rotation
        gravityVector = g
    }

    fun processRotationVector(rotationVector: FloatArray) {
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)

        // R^T * [0, 0, 1]^T gives the world UP vector in phone coordinates: (R[6], R[7], R[8])
        val gx = rotationMatrix[6] * 9.81f
        val gy = rotationMatrix[7] * 9.81f
        val gz = rotationMatrix[8] * 9.81f

        gravityVector = Vector3D(gx, gy, gz)
        isInitialized = true
        isRotationVectorActive = true
    }

    fun getEstimatedGravity(): Vector3D {
        return gravityVector.normalize()
    }

    fun isRotationVectorActive(): Boolean = isRotationVectorActive

    fun reset() {
        isInitialized = false
        isRotationVectorActive = false
        lastGyroTimestamp = 0L
        gravityVector = Vector3D(0f, 0f, 9.81f)
    }
}

