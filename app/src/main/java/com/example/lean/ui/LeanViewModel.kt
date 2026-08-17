package com.example.lean.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lean.calibration.CalibrationManager
import com.example.lean.data.AppDatabase
import com.example.lean.data.AppThemeMode
import com.example.lean.data.LeanState
import com.example.lean.data.RideEntity
import com.example.lean.data.RideState
import com.example.lean.data.SensorMode
import com.example.lean.data.SmoothingLevel
import com.example.lean.data.UserSettings
import com.example.lean.location.LocationData
import com.example.lean.location.RideLocationManager
import com.example.lean.orientation.Vector3D
import com.example.lean.recorder.ActiveRideSession
import com.example.lean.recorder.RideRecorder
import com.example.lean.repository.RideRepository
import com.example.lean.sensor.LeanSensorManager
import com.example.lean.sensor.SensorDataListener
import com.example.lean.sensor.SensorStatus
import com.example.lean.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

import com.example.lean.data.CornerEventEntity
import com.example.lean.data.CornerEventDao

class LeanViewModel(application: Application) : AndroidViewModel(application), SensorDataListener {

    private val sensorManager = LeanSensorManager(application)
    private val settingsRepository = SettingsRepository(application)
    private val calibrationManager = CalibrationManager()
    private val locationManager = RideLocationManager(application)
    private val rideRecorder = RideRecorder(application)

    private val database = AppDatabase.getDatabase(application)
    private val rideRepository = RideRepository(database.rideDao(), database.cornerEventDao())

    private val _uiState = MutableStateFlow(LeanState())
    val uiState: StateFlow<LeanState> = _uiState.asStateFlow()

    private val _userSettings = MutableStateFlow(UserSettings())
    val userSettings: StateFlow<UserSettings> = _userSettings.asStateFlow()

    val activeRideSession: StateFlow<ActiveRideSession> = rideRecorder.sessionState
    val locationData: StateFlow<LocationData> = locationManager.locationData

    val allRides: StateFlow<List<RideEntity>> = rideRepository.allRides
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalRideCount: StateFlow<Int> = rideRepository.totalRideCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalRideTimeMs: StateFlow<Long?> = rideRepository.totalRideTimeMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalDistanceKm: StateFlow<Float?> = rideRepository.totalDistanceKm
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val bestLeanAngle: StateFlow<Float?> = rideRepository.bestLeanAngle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val mostRecentRide: StateFlow<RideEntity?> = rideRepository.mostRecentRide
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _selectedHistoricalRide = MutableStateFlow<RideEntity?>(null)
    val selectedHistoricalRide: StateFlow<RideEntity?> = _selectedHistoricalRide.asStateFlow()

    private var latestGravity: Vector3D = Vector3D(0f, 0f, 9.81f)
    private var feedbackJob: Job? = null

    // FPS calculation variables
    private var frameCount = 0
    private var lastFpsTimestamp = System.currentTimeMillis()

    init {
        val loadedSettings = settingsRepository.getSettings()
        _userSettings.value = loadedSettings

        sensorManager.setListener(this)
        val initialStatus = sensorManager.getInitialStatus()
        _uiState.update { state ->
            state.copy(
                isGyroAvailable = initialStatus.hasGyroscope,
                isAccelAvailable = initialStatus.hasAccelerometer,
                activeMode = initialStatus.activeMode,
                feedbackMessage = initialStatus.warningMessage
            )
        }

        sensorManager.start(loadedSettings.sensorMode)
    }

    fun prepareRide() {
        if (_userSettings.value.isGpsEnabled) {
            locationManager.startListening()
        }
        rideRecorder.prepareRide()
    }

    fun centerAndStartRide() {
        // Step 1: Calibration reference capture
        calibrationManager.calibrate(latestGravity)

        _uiState.update { state ->
            state.copy(
                currentAngleDegrees = 0f,
                filteredAngleDegrees = 0f,
                isCalibrated = true,
                showCenteredFeedback = true,
                maxLeftDegrees = 0f,
                maxRightDegrees = 0f
            )
        }

        // Step 2: Reset GPS stats if enabled
        if (_userSettings.value.isGpsEnabled) {
            locationManager.startListening()
            locationManager.resetStats()
        }

        // Step 3: Start Ride Recorder
        rideRecorder.startRide(_userSettings.value)

        // Visual toast feedback
        feedbackJob?.cancel()
        feedbackJob = viewModelScope.launch {
            delay(1000)
            _uiState.update { it.copy(showCenteredFeedback = false) }
        }
    }

    fun endRide(): RideEntity {
        val loc = locationManager.locationData.value
        val finalizedRide = rideRecorder.endRide(
            currentSpeedKmh = loc.currentSpeedKmh,
            maxSpeedKmh = loc.maxSpeedKmh,
            distanceKm = loc.distanceKm
        )
        val corners = rideRecorder.lastCompletedCorners

        locationManager.stopListening()

        // Automatically save ride and corners locally to Room
        viewModelScope.launch {
            rideRepository.saveRide(finalizedRide, corners)
        }

        return finalizedRide
    }

    fun getCornersForRide(rideId: Long): Flow<List<CornerEventEntity>> {
        return rideRepository.getCornersForRide(rideId)
    }

    fun selectHistoricalRide(ride: RideEntity?) {
        _selectedHistoricalRide.value = ride
    }

    fun deleteRide(rideId: Long) {
        viewModelScope.launch {
            rideRepository.deleteRide(rideId)
        }
    }

    fun resumeUnfinishedRide() {
        if (_userSettings.value.isGpsEnabled) {
            locationManager.startListening()
        }
        rideRecorder.resumeUnfinishedRide(_userSettings.value)
    }

    fun discardUnfinishedRide() {
        rideRecorder.discardUnfinishedRide()
        locationManager.stopListening()
    }

    fun returnToHomeFromRide() {
        rideRecorder.resetToIdle()
        locationManager.stopListening()
    }

    fun calibrateZero() {
        calibrationManager.calibrate(latestGravity)
        _uiState.update { state ->
            state.copy(
                currentAngleDegrees = 0f,
                filteredAngleDegrees = 0f,
                isCalibrated = true,
                showCenteredFeedback = true
            )
        }

        feedbackJob?.cancel()
        feedbackJob = viewModelScope.launch {
            delay(1000)
            _uiState.update { it.copy(showCenteredFeedback = false) }
        }
    }

    fun resetPeaks() {
        _uiState.update { state ->
            state.copy(
                maxLeftDegrees = 0f,
                maxRightDegrees = 0f
            )
        }
    }

    fun resetCalibration() {
        calibrationManager.reset()
        _uiState.update { state ->
            state.copy(
                currentAngleDegrees = 0f,
                filteredAngleDegrees = 0f,
                isCalibrated = false
            )
        }
    }

    fun resetSettingsToDefault() {
        val defaultSettings = settingsRepository.resetToDefaults()
        _userSettings.value = defaultSettings
        sensorManager.start(defaultSettings.sensorMode)
    }

    fun updateThemeMode(themeMode: AppThemeMode) {
        settingsRepository.saveThemeMode(themeMode)
        _userSettings.update { it.copy(themeMode = themeMode) }
    }

    fun updateSensorMode(mode: SensorMode) {
        settingsRepository.saveSensorMode(mode)
        _userSettings.update { it.copy(sensorMode = mode) }
        sensorManager.start(mode)
    }

    fun updateSmoothingLevel(level: SmoothingLevel) {
        settingsRepository.saveSmoothingLevel(level)
        _userSettings.update { it.copy(smoothingLevel = level) }
    }

    fun updateKeepScreenAwake(keepAwake: Boolean) {
        settingsRepository.saveKeepScreenAwake(keepAwake)
        _userSettings.update { it.copy(keepScreenAwake = keepAwake) }
    }

    fun updateLockOrientation(lockOrientation: Boolean) {
        settingsRepository.saveLockOrientation(lockOrientation)
        _userSettings.update { it.copy(lockOrientation = lockOrientation) }
    }

    fun updateStraightThreshold(threshold: Float) {
        settingsRepository.saveStraightThreshold(threshold)
        _userSettings.update { it.copy(straightThreshold = threshold) }
    }

    fun updateWarningThreshold(threshold: Float) {
        settingsRepository.saveWarningThreshold(threshold)
        _userSettings.update { it.copy(warningThreshold = threshold) }
    }

    fun updateCriticalThreshold(threshold: Float) {
        settingsRepository.saveCriticalThreshold(threshold)
        _userSettings.update { it.copy(criticalThreshold = threshold) }
    }

    fun updateGpsEnabled(enabled: Boolean) {
        settingsRepository.saveGpsEnabled(enabled)
        _userSettings.update { it.copy(isGpsEnabled = enabled) }
        if (!enabled) {
            locationManager.stopListening()
        }
    }

    private var lastDebugLogTimestamp = 0L
    private var previousRawLeanAngle = 0f

    override fun onOrientationUpdated(
        gravity: Vector3D,
        rawAccel: Triple<Float, Float, Float>,
        rawGyro: Triple<Float, Float, Float>
    ) {
        latestGravity = gravity

        if (!calibrationManager.isCalibrated) {
            calibrationManager.calibrate(gravity)
        }

        val rawLeanAngle = calibrationManager.calculateLeanAngle(gravity)

        // Prevent isolated single-frame electrical sensor spikes (> 45° in 20ms)
        val clampedRawAngle = if (kotlin.math.abs(rawLeanAngle - previousRawLeanAngle) > 45.0f) {
            previousRawLeanAngle + (if (rawLeanAngle > previousRawLeanAngle) 45.0f else -45.0f)
        } else {
            rawLeanAngle
        }
        previousRawLeanAngle = clampedRawAngle

        // Fast smoothing filter: filteredAngle = alpha * previousFilteredAngle + (1 - alpha) * currentAngle
        val alpha = _userSettings.value.smoothingLevel.alpha
        val currentFiltered = _uiState.value.filteredAngleDegrees
        val newFiltered = alpha * currentFiltered + (1f - alpha) * clampedRawAngle

        val deadZone = _uiState.value.deadZoneThresholdDegrees
        val displayAngle = if (abs(newFiltered) <= deadZone) 0 else newFiltered.roundToInt()

        // Peak tracking based on filtered whole degrees
        var newMaxLeft = _uiState.value.maxLeftDegrees
        var newMaxRight = _uiState.value.maxRightDegrees

        val absDisplay = abs(displayAngle).toFloat()
        if (displayAngle > 0 && absDisplay > newMaxRight) {
            newMaxRight = absDisplay
        } else if (displayAngle < 0 && absDisplay > newMaxLeft) {
            newMaxLeft = absDisplay
        }

        // Requirement 14: Throttled Logcat debugging (once per second)
        val now = System.currentTimeMillis()
        if (now - lastDebugLogTimestamp >= 1000L) {
            lastDebugLogTimestamp = now
            val accelMag = kotlin.math.sqrt(rawAccel.first * rawAccel.first + rawAccel.second * rawAccel.second + rawAccel.third * rawAccel.third)
            android.util.Log.d(
                "LeanDebug",
                "Mode: ${_uiState.value.activeMode.displayName}, RawRoll: %.2f°, CalibratedRoll: %.2f°, FilteredRoll: %.2f°, DisplayAngle: %d°, AccelMag: %.2f m/s²"
                    .format(java.util.Locale.US, rawLeanAngle, clampedRawAngle, newFiltered, displayAngle, accelMag)
            )
        }

        // FPS calculation
        frameCount++
        var currentFps = _uiState.value.sensorFps
        if (now - lastFpsTimestamp >= 1000) {
            currentFps = frameCount
            frameCount = 0
            lastFpsTimestamp = now
        }

        // Tick processing for active ride recording
        if (activeRideSession.value.rideState == RideState.RECORDING) {
            val loc = locationManager.locationData.value
            rideRecorder.onSensorTick(
                currentLeanDegrees = newFiltered,
                currentSpeedKmh = loc.currentSpeedKmh,
                maxSpeedKmh = loc.maxSpeedKmh,
                distanceKm = loc.distanceKm
            )
        }

        _uiState.update { state ->
            state.copy(
                currentAngleDegrees = rawLeanAngle,
                filteredAngleDegrees = newFiltered,
                maxLeftDegrees = newMaxLeft,
                maxRightDegrees = newMaxRight,
                isCalibrated = calibrationManager.isCalibrated,
                rawAccel = rawAccel,
                rawGyro = rawGyro,
                sensorFps = currentFps
            )
        }
    }

    override fun onSensorStatusChanged(status: SensorStatus) {
        _uiState.update { state ->
            state.copy(
                isGyroAvailable = status.hasGyroscope,
                isAccelAvailable = status.hasAccelerometer,
                hasGameRotationVector = status.hasGameRotationVector,
                hasRotationVector = status.hasRotationVector,
                hasGravity = status.hasGravity,
                hasMagnetometer = status.hasMagnetometer,
                hasLinearAccel = status.hasLinearAccel,
                isGyroPhysical = status.gyroHardwareType == com.example.lean.sensor.SensorHardwareType.PHYSICAL,
                isAccelPhysical = status.accelHardwareType == com.example.lean.sensor.SensorHardwareType.PHYSICAL,
                gyroHardwareType = status.gyroHardwareType,
                accelHardwareType = status.accelHardwareType,
                gameRotHardwareType = status.gameRotHardwareType,
                rotVecHardwareType = status.rotVecHardwareType,
                gravityHardwareType = status.gravityHardwareType,
                magnetometerHardwareType = status.magnetometerHardwareType,
                linearAccelHardwareType = status.linearAccelHardwareType,
                availableSensors = status.availableSensors,
                activeMode = status.activeMode,
                activeSensorName = status.activeSensorName,
                feedbackMessage = status.warningMessage
            )
        }
    }

    fun checkLocationPermissionStatus(): Boolean {
        return locationManager.checkPermissionAndStatus()
    }

    fun onLocationPermissionGranted() {
        locationManager.checkPermissionAndStatus()
        if (_userSettings.value.isGpsEnabled && activeRideSession.value.rideState != RideState.IDLE) {
            locationManager.startListening()
        }
    }

    fun onResume() {
        sensorManager.start(_userSettings.value.sensorMode)
        locationManager.checkPermissionAndStatus()
        if (_userSettings.value.isGpsEnabled && activeRideSession.value.rideState == RideState.RECORDING) {
            locationManager.startListening()
        }
    }

    fun onPause() {
        sensorManager.stop()
        if (activeRideSession.value.rideState != RideState.RECORDING) {
            locationManager.stopListening()
        }
    }

    override fun onCleared() {
        super.onCleared()
        sensorManager.stop()
        locationManager.stopListening()
    }
}
