package com.example.lean.data

data class LeanState(
    val currentAngleDegrees: Float = 0.0f,
    val filteredAngleDegrees: Float = 0.0f,
    val maxLeftDegrees: Float = 0.0f,
    val maxRightDegrees: Float = 0.0f,
    val isCalibrated: Boolean = false,
    val showCenteredFeedback: Boolean = false,
    val feedbackMessage: String? = null,
    // Sensor diagnostic info
    val isGyroAvailable: Boolean = false,
    val isAccelAvailable: Boolean = false,
    val activeMode: SensorMode = SensorMode.AUTOMATIC,
    val activeSensorLabel: String = "Initializing...",
    val rawAccel: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    val rawGyro: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    val sensorFps: Int = 0
) {
    val directionText: String
        get() = when {
            filteredAngleDegrees > 0.5f -> "RIGHT"
            filteredAngleDegrees < -0.5f -> "LEFT"
            else -> "LEVEL"
        }

    val displayAngleText: String
        get() {
            val absAngle = kotlin.math.abs(filteredAngleDegrees)
            val sign = if (filteredAngleDegrees > 0.1f) "+" else if (filteredAngleDegrees < -0.1f) "−" else ""
            return String.format(java.util.Locale.US, "%s%.1f°", sign, absAngle)
        }
}
