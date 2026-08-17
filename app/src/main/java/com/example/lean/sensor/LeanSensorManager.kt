package com.example.lean.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import com.example.lean.data.SensorMode
import com.example.lean.orientation.OrientationEstimator
import com.example.lean.orientation.Vector3D
import java.util.Locale

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
    private val magnetometerSensor: Sensor? = systemSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val linearAccelSensor: Sensor? = systemSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

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

    fun getSensorHardwareType(sensor: Sensor?): SensorHardwareType {
        if (sensor == null) return SensorHardwareType.UNAVAILABLE

        // 1. Fused / Composite sensors officially defined by Android specification
        val isFusedType = when (sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_GAME_ROTATION_VECTOR,
            Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR,
            Sensor.TYPE_GRAVITY,
            Sensor.TYPE_LINEAR_ACCELERATION -> true
            else -> false
        }
        if (isFusedType) return SensorHardwareType.FUSED

        // 2. Hardware sensors: Check physical vs software / virtual
        var isPhysical = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val method = Sensor::class.java.getMethod("isPhysicalSensor")
                val result = method.invoke(sensor) as? Boolean
                if (result != null) {
                    isPhysical = result
                }
            } catch (e: Throwable) {
                // Ignore reflection failure if method is not present
            }
        }

        val nameLower = sensor.name.lowercase(Locale.US)
        val vendorLower = sensor.vendor.lowercase(Locale.US)
        val isSoftwareName = nameLower.contains("software") ||
                nameLower.contains("virtual") ||
                nameLower.contains("emulated") ||
                nameLower.contains("synthetic") ||
                nameLower.contains("fusion") ||
                nameLower.contains("simulated") ||
                nameLower.contains("uncalibrated software") ||
                vendorLower.contains("software") ||
                vendorLower.contains("android open source project")

        if (!isPhysical || isSoftwareName) {
            return SensorHardwareType.LOGICAL
        }

        return SensorHardwareType.PHYSICAL
    }

    private fun getSensorInfo(sensor: Sensor?, typeName: String, type: Int): SensorInfo {
        val hwType = getSensorHardwareType(sensor)
        return SensorInfo(
            type = type,
            typeName = typeName,
            name = sensor?.name ?: "N/A",
            vendor = sensor?.vendor ?: "N/A",
            version = sensor?.version ?: 0,
            resolution = sensor?.resolution ?: 0f,
            maximumRange = sensor?.maximumRange ?: 0f,
            isAvailable = sensor != null,
            hardwareType = hwType
        )
    }

    fun getAvailableSensorInfos(): List<SensorInfo> {
        return listOf(
            getSensorInfo(accelerometerSensor, "Accelerometer", Sensor.TYPE_ACCELEROMETER),
            getSensorInfo(gyroscopeSensor, "Gyroscope", Sensor.TYPE_GYROSCOPE),
            getSensorInfo(gameRotationSensor, "Game Rotation Vector", Sensor.TYPE_GAME_ROTATION_VECTOR),
            getSensorInfo(rotationVectorSensor, "Rotation Vector", Sensor.TYPE_ROTATION_VECTOR),
            getSensorInfo(gravitySensor, "Gravity", Sensor.TYPE_GRAVITY),
            getSensorInfo(magnetometerSensor, "Magnetometer", Sensor.TYPE_MAGNETIC_FIELD),
            getSensorInfo(linearAccelSensor, "Linear Acceleration", Sensor.TYPE_LINEAR_ACCELERATION)
        )
    }

    fun getInitialStatus(): SensorStatus {
        val accelType = getSensorHardwareType(accelerometerSensor)
        val gyroType = getSensorHardwareType(gyroscopeSensor)
        val gameRotType = getSensorHardwareType(gameRotationSensor)
        val rotVecType = getSensorHardwareType(rotationVectorSensor)
        val gravityType = getSensorHardwareType(gravitySensor)
        val magType = getSensorHardwareType(magnetometerSensor)
        val linearType = getSensorHardwareType(linearAccelSensor)

        val hasAccel = accelType != SensorHardwareType.UNAVAILABLE
        val hasGyro = gyroType != SensorHardwareType.UNAVAILABLE
        val hasGameRot = gameRotType != SensorHardwareType.UNAVAILABLE
        val hasRotVec = rotVecType != SensorHardwareType.UNAVAILABLE
        val hasGrav = gravityType != SensorHardwareType.UNAVAILABLE
        val hasMag = magType != SensorHardwareType.UNAVAILABLE
        val hasLinear = linearType != SensorHardwareType.UNAVAILABLE

        val (resolvedMode, activeName) = resolveModeAndSensor(requestedMode)

        val gyroLabel = when (gyroType) {
            SensorHardwareType.PHYSICAL -> "ACTIVE (PHYSICAL)"
            SensorHardwareType.LOGICAL -> "LOGICAL SENSOR"
            else -> "UNAVAILABLE"
        }

        val accelLabel = when (accelType) {
            SensorHardwareType.PHYSICAL -> "ACTIVE (PHYSICAL)"
            SensorHardwareType.LOGICAL -> "LOGICAL SENSOR"
            else -> "UNAVAILABLE"
        }

        var warningMsg: String? = null
        var errorMsg: String? = null

        if (gyroType == SensorHardwareType.LOGICAL && requestedMode == SensorMode.FUSED_GYRO_ACCEL) {
            warningMsg = "⚠ Physical gyroscope unavailable. Device is using a logical software gyroscope."
        } else if (!hasGameRot && !hasRotVec && gyroType != SensorHardwareType.PHYSICAL) {
            warningMsg = "No physical gyroscope detected — using software sensor fusion."
        }

        if (!hasAccel) {
            errorMsg = "Accelerometer unavailable — tilt measurement cannot function!"
        }

        return SensorStatus(
            hasAccelerometer = hasAccel,
            accelHardwareType = accelType,

            hasGyroscope = hasGyro,
            gyroHardwareType = gyroType,

            hasGameRotationVector = hasGameRot,
            gameRotHardwareType = gameRotType,

            hasRotationVector = hasRotVec,
            rotVecHardwareType = rotVecType,

            hasGravity = hasGrav,
            gravityHardwareType = gravityType,

            hasMagnetometer = hasMag,
            magnetometerHardwareType = magType,

            hasLinearAccel = hasLinear,
            linearAccelHardwareType = linearType,

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
        val accelType = getSensorHardwareType(accelerometerSensor)
        val gyroType = getSensorHardwareType(gyroscopeSensor)
        val gameRotType = getSensorHardwareType(gameRotationSensor)
        val rotVecType = getSensorHardwareType(rotationVectorSensor)

        val hasGameRot = gameRotType != SensorHardwareType.UNAVAILABLE
        val hasRotVec = rotVecType != SensorHardwareType.UNAVAILABLE
        val hasPhysicalGyro = gyroType == SensorHardwareType.PHYSICAL
        val hasAnyGyro = gyroType != SensorHardwareType.UNAVAILABLE
        val hasAccel = accelType != SensorHardwareType.UNAVAILABLE

        return when (requested) {
            SensorMode.AUTOMATIC -> {
                when {
                    // Prefer custom complementary filter if physical accel + physical gyro exist
                    hasAccel && hasPhysicalGyro -> Pair(SensorMode.FUSED_GYRO_ACCEL, "Gyroscope + Accelerometer (Physical)")
                    // If no physical gyro, prefer Game Rotation Vector
                    hasGameRot -> Pair(SensorMode.GAME_ROTATION_VECTOR, "Game Rotation Vector (Fused)")
                    // Next best: Rotation Vector
                    hasRotVec -> Pair(SensorMode.ROTATION_VECTOR, "Rotation Vector (Fused)")
                    // Fallback to logical gyro + accel
                    hasAccel && hasAnyGyro -> Pair(SensorMode.FUSED_GYRO_ACCEL, "Gyroscope (Logical) + Accelerometer")
                    // Accelerometer fallback
                    hasAccel -> Pair(SensorMode.ACCEL_ONLY, "Accelerometer Only")
                    else -> Pair(SensorMode.ACCEL_ONLY, "Accelerometer Only")
                }
            }
            SensorMode.GAME_ROTATION_VECTOR -> {
                if (hasGameRot) Pair(SensorMode.GAME_ROTATION_VECTOR, "Game Rotation Vector (Fused)")
                else resolveModeAndSensor(SensorMode.AUTOMATIC)
            }
            SensorMode.ROTATION_VECTOR -> {
                if (hasRotVec) Pair(SensorMode.ROTATION_VECTOR, "Rotation Vector (Fused)")
                else resolveModeAndSensor(SensorMode.AUTOMATIC)
            }
            SensorMode.FUSED_GYRO_ACCEL -> {
                if (hasAccel && hasAnyGyro) {
                    val label = if (hasPhysicalGyro) "Gyroscope + Accelerometer (Physical)" else "Gyroscope (Logical) + Accelerometer"
                    Pair(SensorMode.FUSED_GYRO_ACCEL, label)
                } else Pair(SensorMode.ACCEL_ONLY, "Accelerometer Only")
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
