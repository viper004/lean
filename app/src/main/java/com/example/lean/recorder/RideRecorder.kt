package com.example.lean.recorder

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.example.lean.analytics.RideStatisticsCalculator
import com.example.lean.data.RideEntity
import com.example.lean.data.RideState
import com.example.lean.data.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ActiveRideSession(
    val startTimeMs: Long = 0L,
    val durationMs: Long = 0L,
    val currentLeanDegrees: Float = 0f,
    val maxLeftLean: Float = 0f,
    val maxRightLean: Float = 0f,
    val maxAbsoluteLean: Float = 0f,
    val currentSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val distanceKm: Float = 0f,
    val rideState: RideState = RideState.IDLE,
    val isUnfinishedRideFound: Boolean = false
)

class RideRecorder(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val calculator = RideStatisticsCalculator()

    private val _sessionState = MutableStateFlow(ActiveRideSession())
    val sessionState: StateFlow<ActiveRideSession> = _sessionState.asStateFlow()

    private var initialMonotonicTimeMs: Long = 0L
    var lastCompletedRide: RideEntity? = null
        private set

    init {
        checkUnfinishedRide()
    }

    private fun checkUnfinishedRide() {
        val isUnfinished = prefs.getBoolean(KEY_ACTIVE_RECORDING, false)
        if (isUnfinished) {
            _sessionState.update { it.copy(isUnfinishedRideFound = true) }
        }
    }

    fun prepareRide() {
        _sessionState.update {
            it.copy(
                rideState = RideState.PREPARING,
                isUnfinishedRideFound = false
            )
        }
    }

    fun startRide(settings: UserSettings) {
        val nowSysMs = System.currentTimeMillis()
        val nowMonoMs = SystemClock.elapsedRealtime()

        initialMonotonicTimeMs = nowMonoMs
        calculator.updateSettings(settings)
        calculator.start(nowSysMs, nowMonoMs)

        // Persist unfinished ride marker
        prefs.edit()
            .putBoolean(KEY_ACTIVE_RECORDING, true)
            .putLong(KEY_START_TIME_MS, nowSysMs)
            .apply()

        _sessionState.update {
            ActiveRideSession(
                startTimeMs = nowSysMs,
                durationMs = 0L,
                currentLeanDegrees = 0f,
                maxLeftLean = 0f,
                maxRightLean = 0f,
                maxAbsoluteLean = 0f,
                currentSpeedKmh = 0f,
                maxSpeedKmh = 0f,
                distanceKm = 0f,
                rideState = RideState.RECORDING,
                isUnfinishedRideFound = false
            )
        }
    }

    fun onSensorTick(
        currentLeanDegrees: Float,
        currentSpeedKmh: Float = 0f,
        maxSpeedKmh: Float = 0f,
        distanceKm: Float = 0f
    ) {
        if (_sessionState.value.rideState != RideState.RECORDING) return

        val nowMonoMs = SystemClock.elapsedRealtime()
        calculator.processTick(currentLeanDegrees, nowMonoMs)

        _sessionState.update {
            it.copy(
                durationMs = calculator.totalDurationMs,
                currentLeanDegrees = currentLeanDegrees,
                maxLeftLean = calculator.maxLeftLean,
                maxRightLean = calculator.maxRightLean,
                maxAbsoluteLean = calculator.maxAbsoluteLean,
                currentSpeedKmh = currentSpeedKmh,
                maxSpeedKmh = maxSpeedKmh,
                distanceKm = distanceKm
            )
        }
    }

    fun endRide(currentSpeedKmh: Float = 0f, maxSpeedKmh: Float = 0f, distanceKm: Float = 0f): RideEntity {
        _sessionState.update { it.copy(rideState = RideState.FINISHING) }

        val nowSysMs = System.currentTimeMillis()
        var finalizedEntity = calculator.finish(nowSysMs)

        // Attach GPS stats
        val avgSpeed = if (finalizedEntity.durationMs > 0 && distanceKm > 0) {
            (distanceKm / (finalizedEntity.durationMs / 3600000f))
        } else 0f

        finalizedEntity = finalizedEntity.copy(
            distanceKm = distanceKm,
            maxSpeedKmh = maxSpeedKmh,
            averageSpeedKmh = avgSpeed,
            isGpsEnabled = distanceKm > 0 || maxSpeedKmh > 0
        )

        lastCompletedRide = finalizedEntity

        // Clear unfinished ride marker
        prefs.edit().putBoolean(KEY_ACTIVE_RECORDING, false).apply()

        _sessionState.update {
            it.copy(
                rideState = RideState.COMPLETED,
                isUnfinishedRideFound = false
            )
        }

        return finalizedEntity
    }

    fun resumeUnfinishedRide(settings: UserSettings) {
        val savedStartTime = prefs.getLong(KEY_START_TIME_MS, System.currentTimeMillis())
        val nowMonoMs = SystemClock.elapsedRealtime()

        calculator.updateSettings(settings)
        calculator.start(savedStartTime, nowMonoMs)

        _sessionState.update {
            ActiveRideSession(
                startTimeMs = savedStartTime,
                rideState = RideState.RECORDING,
                isUnfinishedRideFound = false
            )
        }
    }

    fun discardUnfinishedRide() {
        prefs.edit().putBoolean(KEY_ACTIVE_RECORDING, false).apply()
        calculator.reset()
        _sessionState.update { ActiveRideSession(rideState = RideState.IDLE, isUnfinishedRideFound = false) }
    }

    fun resetToIdle() {
        calculator.reset()
        _sessionState.update { ActiveRideSession(rideState = RideState.IDLE) }
    }

    companion object {
        private const val PREFS_NAME = "lean_recorder_prefs"
        private const val KEY_ACTIVE_RECORDING = "is_active_recording"
        private const val KEY_START_TIME_MS = "start_time_ms"
    }
}
