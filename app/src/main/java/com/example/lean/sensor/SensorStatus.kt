package com.example.lean.sensor

import com.example.lean.data.SensorMode

data class SensorStatus(
    val hasGyroscope: Boolean = false,
    val hasAccelerometer: Boolean = false,
    val hasGameRotationVector: Boolean = false,
    val activeMode: SensorMode = SensorMode.AUTOMATIC,
    val gyroStateLabel: String = "CHECKING",
    val accelStateLabel: String = "CHECKING",
    val warningMessage: String? = null,
    val errorMessage: String? = null
)
