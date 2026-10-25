package com.example.lean.data

import com.example.lean.sensor.SensorHardwareType
import com.example.lean.sensor.SensorInfo
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
    val hasMagnetometer: Boolean = false,
    val hasLinearAccel: Boolean = false,
    val isGyroPhysical: Boolean = false,
    val isAccelPhysical: Boolean = false,
    val gyroHardwareType: SensorHardwareType = SensorHardwareType.UNAVAILABLE,
    val accelHardwareType: SensorHardwareType = SensorHardwareType.UNAVAILABLE,
    val gameRotHardwareType: SensorHardwareType = SensorHardwareType.UNAVAILABLE,
    val rotVecHardwareType: SensorHardwareType = SensorHardwareType.UNAVAILABLE,
    val gravityHardwareType: SensorHardwareType = SensorHardwareType.UNAVAILABLE,
    val magnetometerHardwareType: SensorHardwareType = SensorHardwareType.UNAVAILABLE,
    val linearAccelHardwareType: SensorHardwareType = SensorHardwareType.UNAVAILABLE,
    val availableSensors: List<SensorInfo> = emptyList(),
    val activeMode: SensorMode = SensorMode.AUTOMATIC,
    val activeSensorName: String = "Game Rotation Vector",
    val activeSensorLabel: String = "Initializing...",
    val rawAccel: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    val rawGyro: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    val sensorFps: Int = 0,
    val possibleFallDetected: Boolean = false,
    val fallDetectionMaxAngle: Float = 0f,
    val fallDetectionTimeMs: Long = 0L,
    val fallDetectionSpeedKmh: Float = 0f,
    val fallDetectionLat: Double? = null,
    val fallDetectionLng: Double? = null,
    val fallDetectionSmsCountdown: Int? = null,
    val currentFallEventId: Long? = null,
    // BLE integration fields
    val bleConnectionState: com.example.lean.bluetooth.BleConnectionState = com.example.lean.bluetooth.BleConnectionState.DISCONNECTED,
    val bleDeviceName: String? = null,
    val bleDeviceAddress: String? = null,
    val bleLastPacket: com.example.lean.bluetooth.BlePacket? = null,
    val bleErrorMessage: String? = null,
    val isBleSensorActive: Boolean = false,
    val isExternalSensorConnected: Boolean = false
) {
    /**
     * Whether telemetry data is actively available from the current sensor source.
     */
    val isTelemetryAvailable: Boolean
        get() = if (isBleSensorActive) isExternalSensorConnected else true

    /**
     * Integer lean angle after dead zone threshold and rounding to whole degrees.
     */
    val displayAngleDegrees: Int
        get() {
            if (isBleSensorActive && !isExternalSensorConnected) {
                return 0
            }
            if (abs(filteredAngleDegrees) <= deadZoneThresholdDegrees) {
                return 0
            }
            return filteredAngleDegrees.roundToInt()
        }

    val directionText: String
        get() {
            if (isBleSensorActive && !isExternalSensorConnected) {
                return "SIGNAL LOST"
            }
            return when {
                displayAngleDegrees > 0 -> "RIGHT"
                displayAngleDegrees < 0 -> "LEFT"
                else -> "LEVEL"
            }
        }

    val displayAngleText: String
        get() {
            if (isBleSensorActive && !isExternalSensorConnected) {
                return "−−°"
            }
            val angle = displayAngleDegrees
            val absAngle = abs(angle)
            val sign = if (angle > 0) "+" else if (angle < 0) "−" else ""
            return "$sign$absAngle°"
        }
}
