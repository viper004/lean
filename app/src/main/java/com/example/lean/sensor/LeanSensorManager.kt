package com.example.lean.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
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
    private val rotationVectorSensor: Sensor? = systemSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gravitySensor: Sensor? = systemSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

    private val orientationEstimator = OrientationEstimator()
    private var requestedMode: SensorMode = SensorMode.AUTOMATIC
    private var activeMode: SensorMode = SensorMode.AUTOMATIC
    private var isListening = false

    private var listener: SensorDataListener? = null

    // Cache latest raw sensor values to avoid object creation during high-rate callbacks
    private var rawAx = 0f
    private var rawAy = 0f
    private var rawAz = 0f

    private var rawGx = 0f
    private var rawGy = 0f
    private var rawGz = 0f

    private var rotationVector = FloatArray(5)

    fun setListener(listener: SensorDataListener?) {
        this.listener = listener
    }

    private fun getSensorInfo(sensor: Sensor?, typeName: String, type: Int): SensorInfo {
        return SensorInfo(
            type = type,
            typeName = typeName,
            name = sensor?.name ?: "N/A",
            vendor = sensor?.vendor ?: "N/A",
            version = sensor?.version ?: 0,
            resolution = sensor?.resolution ?: 0f,
            maximumRange = sensor?.maximumRange ?: 0f,
            isAvailable = sensor != null
        )
    }

    fun getAvailableSensorInfos(): List<SensorInfo> {
        return listOf(
            getSensorInfo(accelerometerSensor, "Accelerometer", Sensor.TYPE_ACCELEROMETER),
            getSensorInfo(gyroscopeSensor, "Gyroscope", Sensor.TYPE_GYROSCOPE),
            getSensorInfo(gameRotationSensor, "Game Rotation Vector", Sensor.TYPE_GAME_ROTATION_VECTOR),
            getSensorInfo(rotationVectorSensor, "Rotation Vector", Sensor.TYPE_ROTATION_VECTOR),
            getSensorInfo(gravitySensor, "Gravity", Sensor.TYPE_GRAVITY)
        )
    }

    fun getInitialStatus(): SensorStatus {
        val hasAccel = accelerometerSensor != null
        val hasGyro = gyroscopeSensor != null
        val hasGameRot = gameRotationSensor != null
        val hasRotVec = rotationVectorSensor != null
        val hasGrav = gravitySensor != null

        val (resolvedMode, activeName) = resolveModeAndSensor(requestedMode)

        val gyroLabel = if (hasGyro) "ACTIVE" else "UNAVAILABLE"
        val accelLabel = if (hasAccel) "ACTIVE" else "UNAVAILABLE"

        var warningMsg: String? = null
        var errorMsg: String? = null

        if (!hasGameRot && !hasRotVec && !hasGyro) {
            warningMsg = "Rotation vectors & Gyroscope unavailable — using accelerometer fallback."
        }
        if (!hasAccel) {
            errorMsg = "Accelerometer unavailable — tilt measurement cannot function!"
        }

        return SensorStatus(
            hasAccelerometer = hasAccel,
            hasGyroscope = hasGyro,
            hasGameRotationVector = hasGameRot,
            hasRotationVector = hasRotVec,
            hasGravity = hasGrav,
            availableSensors = getAvailableSensorInfos(),
            requestedMode = requestedMode,
            activeMode = resolvedMode,
            activeSensorName = activeName,
            gyroStateLabel = gyroLabel,
            accelStateLabel = accelLabel,
            warningMessage = warningMsg,
            errorMessage = errorMsg
        )
    }

    fun resolveModeAndSensor(requested: SensorMode): Pair<SensorMode, String> {
        val hasGameRot = gameRotationSensor != null
        val hasRotVec = rotationVectorSensor != null
        val hasGyro = gyroscopeSensor != null
        val hasAccel = accelerometerSensor != null

        return when (requested) {
            SensorMode.AUTOMATIC -> {
                when {
                    hasGameRot -> Pair(SensorMode.GAME_ROTATION_VECTOR, "Game Rotation Vector")
                    hasRotVec -> Pair(SensorMode.ROTATION_VECTOR, "Rotation Vector")
                    hasGyro && hasAccel -> Pair(SensorMode.FUSED_GYRO_ACCEL, "Gyroscope + Accelerometer")
                    hasAccel -> Pair(SensorMode.ACCEL_ONLY, "Accelerometer Only")
                    else -> Pair(SensorMode.ACCEL_ONLY, "Accelerometer Only")
                }
            }
            SensorMode.GAME_ROTATION_VECTOR -> {
                if (hasGameRot) Pair(SensorMode.GAME_ROTATION_VECTOR, "Game Rotation Vector")
                else resolveModeAndSensor(SensorMode.AUTOMATIC)
            }
            SensorMode.ROTATION_VECTOR -> {
                if (hasRotVec) Pair(SensorMode.ROTATION_VECTOR, "Rotation Vector")
                else resolveModeAndSensor(SensorMode.AUTOMATIC)
            }
            SensorMode.FUSED_GYRO_ACCEL -> {
                if (hasGyro && hasAccel) Pair(SensorMode.FUSED_GYRO_ACCEL, "Gyroscope + Accelerometer")
                else Pair(SensorMode.ACCEL_ONLY, "Accelerometer Only")
            }
            SensorMode.ACCEL_ONLY -> Pair(SensorMode.ACCEL_ONLY, "Accelerometer Only")
        }
    }

    fun start(mode: SensorMode) {
        if (isListening) stop()
        requestedMode = mode

        val (resolvedMode, activeName) = resolveModeAndSensor(mode)
        activeMode = resolvedMode

        orientationEstimator.reset()

        // Register ONLY required sensors
        when (resolvedMode) {
            SensorMode.GAME_ROTATION_VECTOR -> {
                if (gameRotationSensor != null) {
                    systemSensorManager.registerListener(this, gameRotationSensor, SensorManager.SENSOR_DELAY_GAME)
                    Log.d("LeanSensorManager", "Registered ONLY: Sensor.TYPE_GAME_ROTATION_VECTOR")
                }
            }
            SensorMode.ROTATION_VECTOR -> {
                if (rotationVectorSensor != null) {
                    systemSensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME)
                    Log.d("LeanSensorManager", "Registered ONLY: Sensor.TYPE_ROTATION_VECTOR")
                }
            }
            SensorMode.FUSED_GYRO_ACCEL -> {
                if (accelerometerSensor != null) {
                    systemSensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME)
                }
                if (gyroscopeSensor != null) {
                    systemSensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_GAME)
                }
                Log.d("LeanSensorManager", "Registered: Sensor.TYPE_ACCELEROMETER + Sensor.TYPE_GYROSCOPE")
            }
            SensorMode.ACCEL_ONLY, SensorMode.AUTOMATIC -> {
                if (accelerometerSensor != null) {
                    systemSensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME)
                    Log.d("LeanSensorManager", "Registered ONLY: Sensor.TYPE_ACCELEROMETER")
                }
            }
        }

        isListening = true
        listener?.onSensorStatusChanged(getInitialStatus().copy(activeMode = resolvedMode, activeSensorName = activeName))
    }

    fun stop() {
        if (isListening) {
            systemSensorManager.unregisterListener(this)
            isListening = false
            orientationEstimator.reset()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR -> {
                val len = minOf(event.values.size, 5)
                System.arraycopy(event.values, 0, rotationVector, 0, len)
                orientationEstimator.processRotationVector(rotationVector)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                rawAx = event.values[0]
                rawAy = event.values[1]
                rawAz = event.values[2]
                orientationEstimator.processAccelerometer(rawAx, rawAy, rawAz)
            }
            Sensor.TYPE_GYROSCOPE -> {
                rawGx = event.values[0]
                rawGy = event.values[1]
                rawGz = event.values[2]
                orientationEstimator.processGyroscope(rawGx, rawGy, rawGz, event.timestamp)
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


