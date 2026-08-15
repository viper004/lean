package com.example.lean.data

enum class SensorMode(val displayName: String, val description: String) {
    AUTOMATIC(
        displayName = "Automatic",
        description = "Uses Gyroscope + Accelerometer if available, otherwise Accelerometer only."
    ),
    FUSED_GYRO_ACCEL(
        displayName = "Gyroscope + Accelerometer",
        description = "Uses sensor fusion for fast response and drift correction."
    ),
    ACCEL_ONLY(
        displayName = "Accelerometer Only",
        description = "Fallback mode based on gravity tilt measurement."
    )
}
