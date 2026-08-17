package com.example.lean.analytics

import com.example.lean.data.RideEntity
import com.example.lean.data.UserSettings
import kotlin.math.abs
import kotlin.math.roundToInt

class RideStatisticsCalculator(
    private var settings: UserSettings = UserSettings()
) {
    var startTimeMs: Long = 0L
    var endTimeMs: Long = 0L
    var totalDurationMs: Long = 0L

    var maxLeftLean: Float = 0f
    var maxRightLean: Float = 0f
    var maxAbsoluteLean: Float = 0f

    var totalLeanTimeMs: Long = 0L
    var straightTimeMs: Long = 0L
    var leftLeanTimeMs: Long = 0L
    var rightLeanTimeMs: Long = 0L

    var preferredTimeMs: Long = 0L
    var warningTimeMs: Long = 0L
    var criticalTimeMs: Long = 0L

    private var lastTimestampMs: Long = -1L

    fun updateSettings(newSettings: UserSettings) {
        this.settings = newSettings
    }

    fun start(startTimeMillis: Long, initialMonotonicTimeMs: Long) {
        reset()
        this.startTimeMs = startTimeMillis
        this.lastTimestampMs = initialMonotonicTimeMs
    }

    fun reset() {
        startTimeMs = 0L
        endTimeMs = 0L
        totalDurationMs = 0L
        maxLeftLean = 0f
        maxRightLean = 0f
        maxAbsoluteLean = 0f
        totalLeanTimeMs = 0L
        straightTimeMs = 0L
        leftLeanTimeMs = 0L
        rightLeanTimeMs = 0L
        preferredTimeMs = 0L
        warningTimeMs = 0L
        criticalTimeMs = 0L
        lastTimestampMs = -1L
        cornerHoldDurationMs = 0L
    }

    private var cornerHoldDurationMs: Long = 0L

    fun processTick(currentLeanAngleDegrees: Float, currentMonotonicTimeMs: Long) {
        if (lastTimestampMs < 0L) {
            lastTimestampMs = currentMonotonicTimeMs
            return
        }

        val deltaMs: Long = currentMonotonicTimeMs - lastTimestampMs
        lastTimestampMs = currentMonotonicTimeMs

        // Ignore negative deltas or absurdly large jumps (> 24 hours pause recovery)
        if (deltaMs !in 1L..86400000L) return

        totalDurationMs += deltaMs

        val absAngle: Float = abs(currentLeanAngleDegrees)
        val wholeAngle: Int = abs(currentLeanAngleDegrees.roundToInt())

        // Requirement 9: Require lean angle >= 10° for at least 0.5s (500ms) before registering corner
        if (wholeAngle >= 10) {
            cornerHoldDurationMs += deltaMs
        } else {
            cornerHoldDurationMs = 0L
        }

        val isCornerRegistered: Boolean = cornerHoldDurationMs >= 500L

        // Peak tracking
        if (currentLeanAngleDegrees > 0.5f && currentLeanAngleDegrees > maxRightLean) {
            maxRightLean = currentLeanAngleDegrees
        } else if (currentLeanAngleDegrees < -0.5f && absAngle > maxLeftLean) {
            maxLeftLean = absAngle
        }
        if (absAngle > maxAbsoluteLean) {
            maxAbsoluteLean = absAngle
        }

        // Dead Zone / Straight classification
        val straightThresh: Float = settings.straightThreshold
        if (!isCornerRegistered || wholeAngle < 10) {
            straightTimeMs += deltaMs
        } else if (currentLeanAngleDegrees < -straightThresh) {
            leftLeanTimeMs += deltaMs
            totalLeanTimeMs += deltaMs
        } else if (currentLeanAngleDegrees > straightThresh) {
            rightLeanTimeMs += deltaMs
            totalLeanTimeMs += deltaMs
        }

        // Safety threshold time accumulation
        val warningThresh: Float = settings.warningThreshold
        val criticalThresh: Float = settings.criticalThreshold

        when {
            absAngle < warningThresh -> preferredTimeMs += deltaMs
            absAngle < criticalThresh -> warningTimeMs += deltaMs
            else -> criticalTimeMs += deltaMs
        }
    }

    fun finish(endTimeMillis: Long): RideEntity {
        this.endTimeMs = endTimeMillis
        val duration: Long = if (totalDurationMs > 0L) totalDurationMs else (endTimeMs - startTimeMs).coerceAtLeast(1L)
        this.totalDurationMs = duration

        val straightPct = (straightTimeMs.toDouble() / duration * 100.0).toFloat().coerceIn(0f, 100f)
        val leftPct = (leftLeanTimeMs.toDouble() / duration * 100.0).toFloat().coerceIn(0f, 100f)
        val rightPct = (rightLeanTimeMs.toDouble() / duration * 100.0).toFloat().coerceIn(0f, 100f)
        val leanPct = (totalLeanTimeMs.toDouble() / duration * 100.0).toFloat().coerceIn(0f, 100f)

        val prefPct = (preferredTimeMs.toDouble() / duration * 100.0).toFloat().coerceIn(0f, 100f)
        val warnPct = (warningTimeMs.toDouble() / duration * 100.0).toFloat().coerceIn(0f, 100f)
        val critPct = (criticalTimeMs.toDouble() / duration * 100.0).toFloat().coerceIn(0f, 100f)

        return RideEntity(
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            durationMs = duration,
            maxLeftLean = maxLeftLean,
            maxRightLean = maxRightLean,
            maxAbsoluteLean = maxAbsoluteLean,
            totalLeanTimeMs = totalLeanTimeMs,
            straightTimeMs = straightTimeMs,
            leftLeanTimeMs = leftLeanTimeMs,
            rightLeanTimeMs = rightLeanTimeMs,
            leanPercentage = leanPct,
            straightPercentage = straightPct,
            leftLeanPercentage = leftPct,
            rightLeanPercentage = rightPct,
            safetyPreferredPercentage = prefPct,
            safetyWarningPercentage = warnPct,
            safetyCriticalPercentage = critPct,
            warningTimeMs = warningTimeMs,
            criticalTimeMs = criticalTimeMs
        )
    }
}

