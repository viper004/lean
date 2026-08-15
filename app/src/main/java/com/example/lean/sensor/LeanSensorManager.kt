package com.example.lean.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.lean.data.SensorMode
import com.example.lean.orientation.OrientationEstimator
import com.example.lean.orientation.Vector3D

interface SensorDataListener {
    fun onOrientationUpdated(gravity: Vector3D, rawAccel: Triple<Float, Float, Float>, rawGyro: Triple<Float, Float, Float>)
    fun onSensorStatusChanged(status: SensorStatus)
}

class LeanSensorManager(context: Context) : SensorEventListener {

    private val systemSensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometerSensor: Sensor? = systemSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscopeSensor: Sensor? = systemSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val gameRotationSensor: Sensor? = systemSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    private val orientationEstimator = OrientationEstimator()
    private var requestedMode: SensorMode = SensorMode.AUTOMATIC
    private var isListening = false

    private var listener: SensorDataListener? = null

    // Cache latest raw sensor values to avoid object creation during high-rate callbacks
    private var rawAx = 0f
    private var rawAy = 0f
    private var rawAz = 0f

    private var rawGx = 0f
    private var rawGy = 0f
    private var rawGz = 0f

    private var rotationVector = FloatArray(4)

    fun setListener(listener: SensorDataListener?) {
        this.listener = listener
    }

    fun getInitialStatus(): SensorStatus {
        val hasGyro = gyroscopeSensor != null
        val hasAccel = accelerometerSensor != null
        val hasGameVector = gameRotationSensor != null

        val gyroLabel = if (hasGyro) "ACTIVE" else "UNAVAILABLE"
        val accelLabel = if (hasAccel) "ACTIVE" else "UNAVAILABLE"

        var warningMsg: String? = null
        var errorMsg: String? = null

        if (!hasGyro) {
            warningMsg = "Gyroscope unavailable — using accelerometer mode."
        }
        if (!hasAccel) {
            errorMsg = "Accelerometer unavailable — tilt measurement cannot function!"
        }

        return SensorStatus(
            hasGyroscope = hasGyro,
            hasAccelerometer = hasAccel,
            hasGameRotationVector = hasGameVector,
            activeMode = determineEffectiveMode(requestedMode, hasGyro),
            gyroStateLabel = gyroLabel,
            accelStateLabel = accelLabel,
            warningMessage = warningMsg,
            errorMessage = errorMsg
        )
    }

    fun start(mode: SensorMode) {
        if (isListening) stop()
        requestedMode = mode

        val hasGyro = gyroscopeSensor != null
        val effectiveMode = determineEffectiveMode(mode, hasGyro)

        // Register sensors based on effective mode
        if (accelerometerSensor != null) {
            systemSensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME)
        }

        if (effectiveMode == SensorMode.FUSED_GYRO_ACCEL) {
            if (gameRotationSensor != null) {
                systemSensorManager.registerListener(this, gameRotationSensor, SensorManager.SENSOR_DELAY_GAME)
            } else if (gyroscopeSensor != null) {
                systemSensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }

        isListening = true
        listener?.onSensorStatusChanged(getInitialStatus().copy(activeMode = effectiveMode))
    }

    fun stop() {
        if (isListening) {
            systemSensorManager.unregisterListener(this)
            isListening = false
            orientationEstimator.reset()
        }
    }

    private fun determineEffectiveMode(requested: SensorMode, hasGyro: Boolean): SensorMode {
        return when (requested) {
            SensorMode.AUTOMATIC -> if (hasGyro) SensorMode.FUSED_GYRO_ACCEL else SensorMode.ACCEL_ONLY
            SensorMode.FUSED_GYRO_ACCEL -> if (hasGyro) SensorMode.FUSED_GYRO_ACCEL else SensorMode.ACCEL_ONLY
            SensorMode.ACCEL_ONLY -> SensorMode.ACCEL_ONLY
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                rawAx = event.values[0]
                rawAy = event.values[1]
                rawAz = event.values[2]

                // Process accelerometer gravity vector
                orientationEstimator.processAccelerometer(rawAx, rawAy, rawAz)
            }
            Sensor.TYPE_GYROSCOPE -> {
                rawGx = event.values[0]
                rawGy = event.values[1]
                rawGz = event.values[2]

                orientationEstimator.processGyroscope(rawGx, rawGy, rawGz, event.timestamp)
            }
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                if (event.values.size >= 4) {
                    System.arraycopy(event.values, 0, rotationVector, 0, 4)
                    orientationEstimator.processRotationVector(rotationVector)
                }
            }
        }

        val estimatedGravity = orientationEstimator.getEstimatedGravity()

        listener?.onOrientationUpdated(
            gravity = estimatedGravity,
            rawAccel = Triple(rawAx, rawAy, rawAz),
            rawGyro = Triple(rawGx, rawGy, rawGz)
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op for high frequency lean measurement
    }
}
