package com.example.lean.sensor

import com.example.lean.data.SensorMode

data class SensorInfo(
    val type: Int,
    val typeName: String,
    val name: String,
    val vendor: String,
    val version: Int,
    val resolution: Float,
    val maximumRange: Float,
    val isAvailable: Boolean
)

data class SensorStatus(
    val hasAccelerometer: Boolean = false,
    val hasGyroscope: Boolean = false,
    val hasGameRotationVector: Boolean = false,
    val hasRotationVector: Boolean = false,
    val hasGravity: Boolean = false,
    val availableSensors: List<SensorInfo> = emptyList(),
    val requestedMode: SensorMode = SensorMode.AUTOMATIC,
    val activeMode: SensorMode = SensorMode.AUTOMATIC,
    val activeSensorName: String = "Game Rotation Vector",
    val gyroStateLabel: String = "CHECKING",
    val accelStateLabel: String = "CHECKING",
    val warningMessage: String? = null,
    val errorMessage: String? = null
)

