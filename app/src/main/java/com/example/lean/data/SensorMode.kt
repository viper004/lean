package com.example.lean.data

enum class SensorMode(val displayName: String, val description: String) {
    AUTOMATIC(
        displayName = "Automatic",
        description = "Selects best available sensor: Game Rotation Vector → Rotation Vector → Gyro+Accel → Accel."
    ),
    GAME_ROTATION_VECTOR(
        displayName = "Game Rotation Vector",
        description = "Uncalibrated 3D rotation vector. Fast, accurate, drift-free orientation."
    ),
    ROTATION_VECTOR(
        displayName = "Rotation Vector",
        description = "Standard 3D rotation vector combining gyroscope, accelerometer, and compass."
    ),
    FUSED_GYRO_ACCEL(
        displayName = "Gyroscope + Accelerometer",
        description = "Sensor fusion combining accelerometer tilt with gyroscope rates."
    ),
    ACCEL_ONLY(
        displayName = "Accelerometer Only",
        description = "Fallback mode based on gravity tilt measurement."
    )
}

