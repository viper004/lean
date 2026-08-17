package com.example.lean.sensor

import com.example.lean.data.SensorMode

enum class SensorHardwareType {
    PHYSICAL,      // Dedicated physical hardware sensor chip
    LOGICAL,       // Software / virtual / derived sensor
    FUSED,         // Multi-sensor composite / fused orientation sensor
    UNAVAILABLE    // Sensor not present on device
}

data class SensorInfo(
    val type: Int,
    val typeName: String,
    val name: String,
    val vendor: String,
    val version: Int,
    val resolution: Float,
    val maximumRange: Float,
    val isAvailable: Boolean,
    val hardwareType: SensorHardwareType = SensorHardwareType.UNAVAILABLE
) {
    val displayStatusText: String
        get() = when (hardwareType) {
            SensorHardwareType.PHYSICAL -> "Available • Physical"
            SensorHardwareType.LOGICAL -> "Logical Sensor (No physical hardware)"
            SensorHardwareType.FUSED -> "Available • Fused Sensor"
            SensorHardwareType.UNAVAILABLE -> "Not Available"
        }
}

data class SensorStatus(
    val hasAccelerometer: Boolean = false,
    val accelHardwareType: SensorHardwareType = SensorHardwareType.UNAVAILABLE,

    val hasGyroscope: Boolean = false,
    val gyroHardwareType: SensorHardwareType = SensorHardwareType.UNAVAILABLE,

    val hasGameRotationVector: Boolean = false,
    val gameRotHardwareType: SensorHardwareType = SensorHardwareType.UNAVAILABLE,

    val hasRotationVector: Boolean = false,
    val rotVecHardwareType: SensorHardwareType = SensorHardwareType.UNAVAILABLE,

    val hasGravity: Boolean = false,
    val gravityHardwareType: SensorHardwareType = SensorHardwareType.UNAVAILABLE,

    val hasMagnetometer: Boolean = false,
    val magnetometerHardwareType: SensorHardwareType = SensorHardwareType.UNAVAILABLE,

    val hasLinearAccel: Boolean = false,
    val linearAccelHardwareType: SensorHardwareType = SensorHardwareType.UNAVAILABLE,

    val availableSensors: List<SensorInfo> = emptyList(),
    val requestedMode: SensorMode = SensorMode.AUTOMATIC,
    val activeMode: SensorMode = SensorMode.AUTOMATIC,
    val activeSensorName: String = "Game Rotation Vector",
    val gyroStateLabel: String = "CHECKING",
    val accelStateLabel: String = "CHECKING",
    val warningMessage: String? = null,
    val errorMessage: String? = null
)
