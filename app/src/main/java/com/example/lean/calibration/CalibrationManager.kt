package com.example.lean.calibration

import com.example.lean.orientation.Vector3D
import kotlin.math.atan2

/**
 * CalibrationManager handles zero-referencing (CENTER calibration)
 * and calculates relative roll / lean angle.
 */
class CalibrationManager {

    private var referenceGravity: Vector3D? = null
    private var referenceRightAxis: Vector3D = Vector3D.UNIT_X
    private var referenceForwardAxis: Vector3D = Vector3D.UNIT_Y
    private var referenceUpAxis: Vector3D = Vector3D.UNIT_Z

    var isCalibrated: Boolean = false
        private set

    /**
     * Set current physical orientation as 0° reference baseline.
     */
    fun calibrate(currentGravity: Vector3D) {
        val normGrav = currentGravity.normalize()
        if (normGrav.length() < 0.5f) return // invalid sensor reading

        // Up vector at calibration (opposite to gravity vector)
        val zUp = normGrav.scale(-1f)

        // Choose phone hardware axis to project for forward reference
        // Use phone Y-axis (0, 1, 0) by default
        val phoneY = Vector3D(0f, 1f, 0f)
        var yProj = phoneY.minus(zUp.scale(phoneY.dot(zUp)))

        // If phone is flat face-up, phone Y-axis aligns with zUp, project phone X-axis (1, 0, 0)
        if (yProj.length() < 0.2f) {
            val phoneX = Vector3D(1f, 0f, 0f)
            yProj = phoneX.minus(zUp.scale(phoneX.dot(zUp)))
        }

        val yFwd = yProj.normalize()
        // Right axis = Forward x Up
        val xRight = yFwd.cross(zUp).normalize()

        referenceGravity = normGrav
        referenceUpAxis = zUp
        referenceForwardAxis = yFwd
        referenceRightAxis = xRight
        isCalibrated = true
    }

    /**
     * Compute relative roll / lean angle in degrees given current gravity vector.
     * Returns:
     * Positive value (+) = RIGHT tilt relative to calibration
     * Negative value (-) = LEFT tilt relative to calibration
     */
    fun calculateLeanAngle(currentGravity: Vector3D): Float {
        if (!isCalibrated || referenceGravity == null) {
            return 0f
        }

        val gNorm = currentGravity.normalize()
        if (gNorm.length() < 0.5f) return 0f

        // Project current gravity onto reference RIGHT and UP axes
        val gx = gNorm.dot(referenceRightAxis)
        val gz = gNorm.dot(referenceUpAxis)

        // Note: At zero calibration, current gravity points in -zUp direction:
        // gz = gNorm.dot(zUp) = -1.0, gx = 0.0 -> atan2(0.0, 1.0) = 0°
        // Lean RIGHT: gravity shifts LEFT in reference frame -> gx < 0 -> -gx > 0 -> positive degrees
        // Lean LEFT: gravity shifts RIGHT in reference frame -> gx > 0 -> -gx < 0 -> negative degrees
        val angleRad = atan2(-gx, -gz)
        return Math.toDegrees(angleRad.toDouble()).toFloat()
    }

    fun reset() {
        isCalibrated = false
        referenceGravity = null
        referenceRightAxis = Vector3D.UNIT_X
        referenceForwardAxis = Vector3D.UNIT_Y
        referenceUpAxis = Vector3D.UNIT_Z
    }
}
