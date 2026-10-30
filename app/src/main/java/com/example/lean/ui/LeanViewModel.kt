package com.example.lean.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lean.bluetooth.BleCalibrationStatus
import com.example.lean.bluetooth.BleConnectionState
import com.example.lean.bluetooth.BlePacket
import com.example.lean.bluetooth.LeanBleManager
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
    private val rideRepository = RideRepository(database.rideDao(), database.cornerEventDao(), database.fallEventDao())
    private val bleManager = LeanBleManager(application)

    val bleConnectionState: StateFlow<BleConnectionState> = bleManager.connectionState
    val latestBlePacket: StateFlow<BlePacket?> = bleManager.latestPacket
    val bleCalibrationStatus: StateFlow<BleCalibrationStatus> = bleManager.calibrationStatus

    private var latestBleAngleRaw: Float = 0f
    private var bleCalibrationJob: Job? = null

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

    private var fallDetectionStartTimeMs: Long? = null
    private var currentFallMaxAngle: Float = 0f
    private var smsCountdownJob: Job? = null

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

        // Observe BLE connection state
        viewModelScope.launch {
            bleManager.connectionState.collect { connState ->
                val isConnected = (connState == BleConnectionState.CONNECTED)
                _uiState.update { state ->
                    val feedback = if (state.activeMode == SensorMode.EXTERNAL_BLE && !isConnected) {
                        when (connState) {
                            BleConnectionState.SCANNING -> "Scanning for LEAN_IOT..."
                            BleConnectionState.CONNECTING -> "Connecting to LEAN_IOT..."
                            BleConnectionState.DISCONNECTED -> "ESP32 disconnected"
                            else -> state.feedbackMessage
                        }
                    } else state.feedbackMessage

                    state.copy(
                        bleConnectionState = connState,
                        isExternalSensorConnected = isConnected,
                        feedbackMessage = feedback
                    )
                }
            }
        }

        // Observe BLE device metadata
        viewModelScope.launch {
            bleManager.connectedDeviceName.collect { name ->
                _uiState.update { it.copy(bleDeviceName = name) }
            }
        }
        viewModelScope.launch {
            bleManager.connectedDeviceAddress.collect { address ->
                _uiState.update { it.copy(bleDeviceAddress = address) }
            }
        }
        viewModelScope.launch {
            bleManager.lastErrorMessage.collect { errorMsg ->
                _uiState.update { it.copy(bleErrorMessage = errorMsg) }
            }
        }

        // Observe BLE calibration status from ESP32
        viewModelScope.launch {
            bleManager.calibrationStatus.collect { calStatus ->
                _uiState.update { state ->
                    val feedback = when (calStatus) {
                        BleCalibrationStatus.CALIBRATING ->
                            "Calibrating ESP32 sensor... Please keep stationary."
                        BleCalibrationStatus.SUCCESS ->
                            "Sensor calibrated! 0.0° reference established."
                        BleCalibrationStatus.FAILED ->
                            "Calibration failed: vehicle moved or sensor unstable. Retry."
                        else -> state.feedbackMessage
                    }
                    state.copy(
                        bleCalibrationStatus = calStatus,
                        feedbackMessage = feedback
                    )
                }
            }
        }

        // Observe incoming BLE sensor packets
        viewModelScope.launch {
            bleManager.packetStream.collect { packet ->
                onBlePacketReceived(packet)
            }
        }

        applySensorMode(loadedSettings.sensorMode)
    }

    fun prepareRide() {
        if (_userSettings.value.isGpsEnabled) {
            locationManager.startListening()
        }
        rideRecorder.prepareRide()
    }

    fun isBleDataFresh(maxAgeMs: Long = 3000L): Boolean {
        val packet = _uiState.value.bleLastPacket ?: return false
        val isConnected = _uiState.value.isExternalSensorConnected
        val age = System.currentTimeMillis() - packet.timestampMs
        return isConnected && age <= maxAgeMs
    }

    /**
     * Workflow for starting a ride:
     * - In internal phone sensor mode: performs instant zero calibration and starts ride recording.
     * - In external BLE mode: dispatches calibration command 0x01 to ESP32, awaits calibration
     *   success acknowledgement, and ONLY begins recording after calibration successfully finishes.
     */
    fun startRideWithCalibration(
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (_userSettings.value.sensorMode != SensorMode.EXTERNAL_BLE) {
            calibrationManager.calibrate(latestGravity)
            _uiState.update { state ->
                state.copy(
                    currentAngleDegrees = 0f,
                    filteredAngleDegrees = 0f,
                    isCalibrated = true,
                    showCenteredFeedback = true,
                    maxLeftDegrees = 0f,
                    maxRightDegrees = 0f,
                    possibleFallDetected = false
                )
            }

            fallDetectionStartTimeMs = null
            currentFallMaxAngle = 0f

            if (_userSettings.value.isGpsEnabled) {
                locationManager.startListening()
                locationManager.resetStats()
            }

            rideRecorder.startRide(_userSettings.value)

            feedbackJob?.cancel()
            feedbackJob = viewModelScope.launch {
                delay(1000)
                _uiState.update { it.copy(showCenteredFeedback = false) }
            }
            onSuccess()
            return
        }

        // External BLE Mode: ensure ESP32 is actively connected and receiving fresh telemetry
        if (!_uiState.value.isExternalSensorConnected || !isBleDataFresh()) {
            val errorMsg = "Cannot start ride: ESP32 sensor is not connected or telemetry is stale."
            _uiState.update {
                it.copy(
                    feedbackMessage = errorMsg,
                    bleErrorMessage = errorMsg
                )
            }
            onError(errorMsg)
            return
        }

        bleCalibrationJob?.cancel()
        bleManager.resetCalibrationState()

        val requested = bleManager.requestCalibration()
        if (!requested) {
            val errorMsg = "Failed to send calibration command to ESP32."
            _uiState.update {
                it.copy(
                    feedbackMessage = errorMsg,
                    bleErrorMessage = errorMsg
                )
            }
            onError(errorMsg)
            return
        }

        _uiState.update {
            it.copy(feedbackMessage = "Calibrating ESP32 sensor... Please keep stationary.")
        }

        bleCalibrationJob = viewModelScope.launch {
            bleManager.calibrationStatus.collect { status ->
                when (status) {
                    BleCalibrationStatus.SUCCESS -> {
                        Log.d("LeanViewModel", "ESP32 calibration SUCCESS acknowledged. Starting ride recording.")
                        fallDetectionStartTimeMs = null
                        currentFallMaxAngle = 0f

                        if (_userSettings.value.isGpsEnabled) {
                            locationManager.startListening()
                            locationManager.resetStats()
                        }

                        // Ride recording starts ONLY after successful calibration!
                        rideRecorder.startRide(_userSettings.value)

                        _uiState.update { state ->
                            state.copy(
                                currentAngleDegrees = 0f,
                                filteredAngleDegrees = 0f,
                                isCalibrated = true,
                                showCenteredFeedback = true,
                                maxLeftDegrees = 0f,
                                maxRightDegrees = 0f,
                                possibleFallDetected = false,
                                feedbackMessage = "Calibrated! Starting ride..."
                            )
                        }

                        feedbackJob?.cancel()
                        feedbackJob = viewModelScope.launch {
                            delay(1000)
                            _uiState.update { it.copy(showCenteredFeedback = false) }
                        }

                        onSuccess()
                        bleCalibrationJob?.cancel()
                    }
                    BleCalibrationStatus.FAILED -> {
                        Log.w("LeanViewModel", "ESP32 calibration failed.")
                        val errorMsg = "Calibration failed: vehicle moved or sensor unstable. Please keep stationary and retry."
                        _uiState.update {
                            it.copy(
                                feedbackMessage = errorMsg,
                                bleErrorMessage = errorMsg,
                                isCalibrated = false
                            )
                        }
                        onError(errorMsg)
                        bleCalibrationJob?.cancel()
                    }
                    BleCalibrationStatus.CALIBRATING -> {
                        _uiState.update {
                            it.copy(feedbackMessage = "Calibrating ESP32 sensor... Please keep stationary.")
                        }
                    }
                    BleCalibrationStatus.IDLE -> {
                        // Awaiting response
                    }
                }
            }
        }
    }

    fun centerAndStartRide(): Boolean {
        var started = false
        startRideWithCalibration(
            onSuccess = { started = true },
            onError = { started = false }
        )
        return started
    }

    fun endRide(): RideEntity {
        val loc = locationManager.locationData.value
        val finalizedRide = rideRecorder.endRide(
            currentSpeedKmh = loc.currentSpeedKmh,
            maxSpeedKmh = loc.maxSpeedKmh,
            distanceKm = loc.distanceKm,
            movingDurationMs = loc.movingDurationMs
        )
        val corners = rideRecorder.lastCompletedCorners

        locationManager.stopListening()
        fallDetectionStartTimeMs = null
        bleCalibrationJob?.cancel()
        bleManager.resetCalibrationState()

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
        bleCalibrationJob?.cancel()
        bleManager.resetCalibrationState()
    }

    fun returnToHomeFromRide() {
        rideRecorder.resetToIdle()
        locationManager.stopListening()
        fallDetectionStartTimeMs = null
        bleCalibrationJob?.cancel()
        bleManager.resetCalibrationState()
        _uiState.update { it.copy(possibleFallDetected = false) }
    }

    fun calibrateZero(): Boolean {
        if (_userSettings.value.sensorMode == SensorMode.EXTERNAL_BLE) {
            if (!_uiState.value.isExternalSensorConnected || !isBleDataFresh()) {
                _uiState.update {
                    it.copy(feedbackMessage = "Cannot re-center: ESP32 sensor is not connected.")
                }
                return false
            }
            return bleManager.requestCalibration()
        } else {
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
            return true
        }
    }

    private fun startSmsCountdown(event: com.example.lean.data.FallEventEntity) {
        smsCountdownJob?.cancel()
        smsCountdownJob = viewModelScope.launch {
            for (i in 10 downTo 1) {
                _uiState.update { it.copy(fallDetectionSmsCountdown = i) }
                delay(1000)
            }
            _uiState.update { it.copy(fallDetectionSmsCountdown = 0) }
            
            val smsManager = try {
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    getApplication<Application>().getSystemService(android.telephony.SmsManager::class.java)
                } else {
                    android.telephony.SmsManager.getDefault()
                }
            } catch (e: Exception) {
                null
            }

            if (smsManager != null) {
                try {
                    val timeString = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.US).format(java.util.Date(event.timestampMs))
                    var message = "LEAN Emergency Alert:\nPossible motorcycle fall detected.\n\nLean angle: ${event.maxLeanAngle.toInt()}°\nSpeed: ${event.speedKmh.toInt()} km/h\nTime: $timeString\n\nLocation:\n"
                    if (event.latitude != 0.0 && event.longitude != 0.0) {
                        message += "https://maps.google.com/?q=${event.latitude},${event.longitude}\n\nPlease check on the rider."
                    } else {
                        message += "Unavailable.\n\nPlease check on the rider."
                    }
                    val parts = smsManager.divideMessage(message)
                    smsManager.sendMultipartTextMessage(_userSettings.value.emergencyPhoneNumber, null, parts, null, null)
                    rideRepository.saveFallEvent(event.copy(smsSent = true))
                } catch (e: Exception) {
                    // Ignore, prevent crash
                }
            }
        }
    }

    fun dismissFallDetection() {
        smsCountdownJob?.cancel()
        val currentEventId = _uiState.value.currentFallEventId
        if (currentEventId != null) {
            viewModelScope.launch {
                rideRepository.markFallEventAcknowledged(currentEventId)
            }
        }
        _uiState.update { it.copy(possibleFallDetected = false, fallDetectionSmsCountdown = null) }
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
        bleCalibrationJob?.cancel()
        bleManager.resetCalibrationState()
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
        applySensorMode(defaultSettings.sensorMode)
    }

    fun updateThemeMode(themeMode: AppThemeMode) {
        settingsRepository.saveThemeMode(themeMode)
        _userSettings.update { it.copy(themeMode = themeMode) }
    }

    fun updateSensorMode(mode: SensorMode) {
        settingsRepository.saveSensorMode(mode)
        _userSettings.update { it.copy(sensorMode = mode) }
        applySensorMode(mode)
    }

    private fun applySensorMode(mode: SensorMode) {
        if (mode == SensorMode.EXTERNAL_BLE) {
            // Mutual exclusion: stop phone sensors completely to avoid concurrent updates
            sensorManager.stop()
            _uiState.update { state ->
                state.copy(
                    activeMode = SensorMode.EXTERNAL_BLE,
                    activeSensorName = "ESP32 IoT Sensor (BLE)",
                    activeSensorLabel = "Wireless BLE",
                    isBleSensorActive = true
                )
            }
            if (bleManager.hasRequiredPermissions() && bleManager.isBluetoothEnabled()) {
                bleManager.startScan()
            } else {
                val msg = if (!bleManager.isBluetoothEnabled()) "Bluetooth is turned off" else "Bluetooth permission required"
                _uiState.update { it.copy(feedbackMessage = msg) }
            }
        } else {
            // Mutual exclusion: stop BLE operations completely and restore phone sensor pipeline
            bleManager.stopScan()
            bleManager.disconnect()
            _uiState.update { state ->
                state.copy(
                    isBleSensorActive = false
                )
            }
            sensorManager.start(mode)
        }
    }

    fun startBleScan() {
        bleManager.startScan()
    }

    fun stopBleScan() {
        bleManager.stopScan()
    }

    fun connectBleDevice(deviceAddress: String) {
        bleManager.connect(deviceAddress)
    }

    fun disconnectBle() {
        bleManager.disconnect()
    }

    fun isBluetoothEnabled(): Boolean = bleManager.isBluetoothEnabled()

    fun hasBlePermissions(): Boolean = bleManager.hasRequiredPermissions()

    fun clearBleError() {
        _uiState.update { it.copy(bleErrorMessage = null) }
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

    fun updateFallDetectionEnabled(enabled: Boolean) {
        settingsRepository.saveFallDetectionEnabled(enabled)
        _userSettings.update { it.copy(isFallDetectionEnabled = enabled) }
        if (!enabled) {
            _uiState.update { it.copy(possibleFallDetected = false) }
            fallDetectionStartTimeMs = null
        }
    }

    fun updateFallDetectionCriticalAngle(angle: Float) {
        settingsRepository.saveFallDetectionCriticalAngle(angle)
        _userSettings.update { it.copy(fallDetectionCriticalAngle = angle) }
    }

    fun updateEmergencySmsEnabled(enabled: Boolean) {
        settingsRepository.saveEmergencySmsEnabled(enabled)
        _userSettings.update { it.copy(isEmergencySmsEnabled = enabled) }
    }

    fun updateEmergencyPhoneNumber(number: String) {
        val cleanNumber = number.replace(Regex("[^+\\d]"), "")
        settingsRepository.saveEmergencyPhoneNumber(cleanNumber)
        _userSettings.update { it.copy(emergencyPhoneNumber = cleanNumber) }
    }

    fun testEmergencySms(onResult: (Boolean, String) -> Unit) {
        val number = _userSettings.value.emergencyPhoneNumber
        if (number.isEmpty()) {
            onResult(false, "Emergency phone number is empty.")
            return
        }
        val smsManager = try {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                getApplication<Application>().getSystemService(android.telephony.SmsManager::class.java)
            } else {
                android.telephony.SmsManager.getDefault()
            }
        } catch (e: Exception) {
            null
        }

        if (smsManager != null) {
            try {
                val message = "LEAN Test Alert:\nThis is a test message from LEAN emergency assistance.\nNo crash has been detected.\n\nThis is only for testing the emergency SMS configuration."
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(number, null, parts, null, null)
                onResult(true, "Test SMS sent.")
            } catch (e: Exception) {
                onResult(false, "Failed to send SMS. Check permissions.")
            }
        } else {
            onResult(false, "SMS Manager not available.")
        }
    }

    private var lastDebugLogTimestamp = 0L
    private var previousRawLeanAngle = 0f

    private fun onBlePacketReceived(packet: BlePacket) {
        if (_userSettings.value.sensorMode != SensorMode.EXTERNAL_BLE) return

        latestBleAngleRaw = packet.leanAngleDegrees

        // The ESP32 is the single source of truth for the calibrated angle.
        // Android directly displays and records the ESP32 calibrated angle without secondary offset.
        val calibratedAngle = packet.leanAngleDegrees

        _uiState.update {
            it.copy(
                bleLastPacket = packet,
                isCalibrated = packet.isValidReading,
                rawGyro = Triple(packet.gyroRollRateDps, 0f, 0f)
            )
        }

        processLeanAngleSample(
            rawLeanAngle = calibratedAngle,
            filteredLeanAngle = calibratedAngle,
            rawAccel = Triple(0f, 0f, 0f),
            rawGyro = Triple(packet.gyroRollRateDps, 0f, 0f)
        )
    }

    override fun onOrientationUpdated(
        gravity: Vector3D,
        rawAccel: Triple<Float, Float, Float>,
        rawGyro: Triple<Float, Float, Float>
    ) {
        if (_userSettings.value.sensorMode == SensorMode.EXTERNAL_BLE) {
            return // Guard against concurrent phone sensor updates when BLE mode is active
        }

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

        processLeanAngleSample(
            rawLeanAngle = rawLeanAngle,
            filteredLeanAngle = newFiltered,
            rawAccel = rawAccel,
            rawGyro = rawGyro
        )
    }

    private fun processLeanAngleSample(
        rawLeanAngle: Float,
        filteredLeanAngle: Float,
        rawAccel: Triple<Float, Float, Float>,
        rawGyro: Triple<Float, Float, Float>
    ) {
        val deadZone = _uiState.value.deadZoneThresholdDegrees
        val displayAngle = if (abs(filteredLeanAngle) <= deadZone) 0 else filteredLeanAngle.roundToInt()

        // Peak tracking based on filtered whole degrees
        var newMaxLeft = _uiState.value.maxLeftDegrees
        var newMaxRight = _uiState.value.maxRightDegrees

        val absDisplay = abs(displayAngle).toFloat()
        if (displayAngle > 0 && absDisplay > newMaxRight) {
            newMaxRight = absDisplay
        } else if (displayAngle < 0 && absDisplay > newMaxLeft) {
            newMaxLeft = absDisplay
        }

        // Throttled Logcat debugging (once per second)
        val now = System.currentTimeMillis()
        if (now - lastDebugLogTimestamp >= 1000L) {
            lastDebugLogTimestamp = now
            val accelMag = kotlin.math.sqrt(rawAccel.first * rawAccel.first + rawAccel.second * rawAccel.second + rawAccel.third * rawAccel.third)
            android.util.Log.d(
                "LeanDebug",
                "Mode: ${_uiState.value.activeMode.displayName}, RawRoll: %.2f°, FilteredRoll: %.2f°, DisplayAngle: %d°, AccelMag: %.2f m/s²"
                    .format(java.util.Locale.US, rawLeanAngle, filteredLeanAngle, displayAngle, accelMag)
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
            val nowMonoMs = android.os.SystemClock.elapsedRealtime()
            val isFresh = loc.isSpeedValid && (nowMonoMs - loc.speedTimestampMs <= 3000L)
            val effectiveSpeedKmh = if (isFresh) loc.currentSpeedKmh else 0f

            rideRecorder.onSensorTick(
                currentLeanDegrees = filteredLeanAngle,
                currentSpeedKmh = effectiveSpeedKmh,
                maxSpeedKmh = loc.maxSpeedKmh,
                distanceKm = loc.distanceKm
            )

            // Fall Detection Logic
            if (_userSettings.value.isFallDetectionEnabled && !_uiState.value.possibleFallDetected) {
                val criticalThreshold = _userSettings.value.fallDetectionCriticalAngle
                if (abs(filteredLeanAngle) >= criticalThreshold) {
                    if (fallDetectionStartTimeMs == null) {
                        fallDetectionStartTimeMs = nowMonoMs
                        currentFallMaxAngle = abs(filteredLeanAngle)
                    } else {
                        if (abs(filteredLeanAngle) > currentFallMaxAngle) {
                            currentFallMaxAngle = abs(filteredLeanAngle)
                        }
                        if (nowMonoMs - fallDetectionStartTimeMs!! >= 10000L) {
                            // Trigger Fall Detected
                            _uiState.update { state ->
                                state.copy(
                                    possibleFallDetected = true,
                                    fallDetectionMaxAngle = currentFallMaxAngle,
                                    fallDetectionTimeMs = now,
                                    fallDetectionSpeedKmh = effectiveSpeedKmh,
                                    fallDetectionLat = if (loc.isGpsActive) loc.latitude else null,
                                    fallDetectionLng = if (loc.isGpsActive) loc.longitude else null,
                                    fallDetectionSmsCountdown = if (_userSettings.value.isEmergencySmsEnabled && _userSettings.value.emergencyPhoneNumber.isNotEmpty()) 10 else null,
                                    currentFallEventId = null
                                )
                            }
                            val finalMaxAngle = currentFallMaxAngle
                            val smsEnabled = _userSettings.value.isEmergencySmsEnabled && _userSettings.value.emergencyPhoneNumber.isNotEmpty()
                            fallDetectionStartTimeMs = null
                            currentFallMaxAngle = 0f

                            viewModelScope.launch {
                                val event = com.example.lean.data.FallEventEntity(
                                    timestampMs = now,
                                    maxLeanAngle = finalMaxAngle,
                                    speedKmh = effectiveSpeedKmh,
                                    latitude = if (loc.isGpsActive) loc.latitude ?: 0.0 else 0.0,
                                    longitude = if (loc.isGpsActive) loc.longitude ?: 0.0 else 0.0,
                                    smsEnabled = smsEnabled
                                )
                                val eventId = rideRepository.saveFallEvent(event)
                                _uiState.update { it.copy(currentFallEventId = eventId) }

                                if (smsEnabled) {
                                    startSmsCountdown(event.copy(fallId = eventId))
                                }
                            }
                        }
                    }
                } else {
                    fallDetectionStartTimeMs = null
                    currentFallMaxAngle = 0f
                }
            } else if (!_userSettings.value.isFallDetectionEnabled) {
                fallDetectionStartTimeMs = null
                currentFallMaxAngle = 0f
            }
        }

        _uiState.update { state ->
            state.copy(
                currentAngleDegrees = rawLeanAngle,
                filteredAngleDegrees = filteredLeanAngle,
                maxLeftDegrees = newMaxLeft,
                maxRightDegrees = newMaxRight,
                isCalibrated = if (state.activeMode == SensorMode.EXTERNAL_BLE) true else calibrationManager.isCalibrated,
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
        if (_userSettings.value.sensorMode == SensorMode.EXTERNAL_BLE) {
            if (bleManager.connectionState.value == BleConnectionState.DISCONNECTED &&
                bleManager.hasRequiredPermissions() && bleManager.isBluetoothEnabled()
            ) {
                bleManager.startScan()
            }
        } else {
            sensorManager.start(_userSettings.value.sensorMode)
        }
        locationManager.checkPermissionAndStatus()
        if (_userSettings.value.isGpsEnabled && activeRideSession.value.rideState == RideState.RECORDING) {
            locationManager.startListening()
        }
    }

    fun onPause() {
        if (_userSettings.value.sensorMode == SensorMode.EXTERNAL_BLE) {
            if (activeRideSession.value.rideState != RideState.RECORDING) {
                bleManager.stopScan()
            }
        } else {
            sensorManager.stop()
        }
        if (activeRideSession.value.rideState != RideState.RECORDING) {
            locationManager.stopListening()
        }
    }

    override fun onCleared() {
        super.onCleared()
        sensorManager.stop()
        bleManager.cleanup()
        locationManager.stopListening()
    }
}
