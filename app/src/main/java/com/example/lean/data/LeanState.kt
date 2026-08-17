package com.example.lean.data

import kotlin.math.abs
import kotlin.math.roundToInt

data class LeanState(
    val currentAngleDegrees: Float = 0.0f,
    val filteredAngleDegrees: Float = 0.0f,
    val maxLeftDegrees: Float = 0.0f,
    val maxRightDegrees: Float = 0.0f,
    val deadZoneThresholdDegrees: Float = 2.0f,
    val isCalibrated: Boolean = false,
    val showCenteredFeedback: Boolean = false,
    val feedbackMessage: String? = null,
    // Sensor diagnostic info
    val isGyroAvailable: Boolean = false,
    val isAccelAvailable: Boolean = false,
    val hasGameRotationVector: Boolean = false,
    val hasRotationVector: Boolean = false,
    val hasGravity: Boolean = false,
    val availableSensors: List<com.example.lean.sensor.SensorInfo> = emptyList(),
    val activeMode: SensorMode = SensorMode.AUTOMATIC,
    val activeSensorName: String = "Game Rotation Vector",
    val activeSensorLabel: String = "Initializing...",
    val rawAccel: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    val rawGyro: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    val sensorFps: Int = 0
) {
    /**
     * Integer lean angle after dead zone threshold and rounding to whole degrees.
     */
    val displayAngleDegrees: Int
        get() {
            if (abs(filteredAngleDegrees) <= deadZoneThresholdDegrees) {
                return 0
            }
            return filteredAngleDegrees.roundToInt()
        }

    val directionText: String
        get() = when {
            displayAngleDegrees > 0 -> "RIGHT"
            displayAngleDegrees < 0 -> "LEFT"
            else -> "LEVEL"
        }

    val displayAngleText: String
        get() {
            val angle = displayAngleDegrees
            val absAngle = abs(angle)
            val sign = if (angle > 0) "+" else if (angle < 0) "−" else ""
            return "$sign$absAngle°"
        }
}

