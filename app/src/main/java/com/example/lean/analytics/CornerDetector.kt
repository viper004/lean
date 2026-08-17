package com.example.lean.analytics

import com.example.lean.data.CornerDirection
import com.example.lean.data.CornerEventEntity
import kotlin.math.abs

/**
 * CornerDetector manages real-time in-memory state tracking of motorcycle corners.
 * Uses strict 400ms start/end hysteresis, direction flip detection (S-curves),
 * and exact timestamp matching for max lean & speed at max lean.
 */
class CornerDetector {

    private enum class DetectorState {
        IDLE,
        PENDING_START,
        ACTIVE_CORNER,
        PENDING_END
    }

    private var state = DetectorState.IDLE
    private val completedCorners = mutableListOf<CornerEventEntity>()

    // Hysteresis timing thresholds (in milliseconds)
    private val startHysteresisMs = 400L
    private val endHysteresisMs = 400L
    private val minCornerDurationMs = 500L
    private val cornerLeanThresholdDegrees = 10.0f

    // Pending start tracking
    private var pendingStartMonotonicMs = 0L
    private var pendingStartDirection = CornerDirection.LEFT
    private var pendingStartEntrySpeed = 0f

    // Active corner tracking
    private var activeCornerNumber = 0
    private var activeDirection = CornerDirection.LEFT
    private var activeStartMonotonicMs = 0L
    private var activeRideStartOffsetMs = 0L
    private var activeEntrySpeed = 0f
    private var activeMaxLean = 0f
    private var activeSpeedAtMaxLean = 0f
    private var activeMaxSpeed = 0f
    private var activeSpeedSum = 0.0
    private var activeSampleCount = 0

    // Pending end tracking
    private var pendingEndMonotonicMs = 0L
    private var pendingEndExitSpeed = 0f

    fun reset() {
        state = DetectorState.IDLE
        completedCorners.clear()
        activeCornerNumber = 0
        pendingStartMonotonicMs = 0L
        pendingEndMonotonicMs = 0L
    }

    fun processTick(
        filteredLeanDegrees: Float,
        currentSpeedKmh: Float,
        nowMonotonicMs: Long,
        rideStartMonotonicMs: Long
    ) {
        val absLean = abs(filteredLeanDegrees)
        val currentDirection = if (filteredLeanDegrees < 0f) CornerDirection.LEFT else CornerDirection.RIGHT

        when (state) {
            DetectorState.IDLE -> {
                if (absLean >= cornerLeanThresholdDegrees) {
                    state = DetectorState.PENDING_START
                    pendingStartMonotonicMs = nowMonotonicMs
                    pendingStartDirection = currentDirection
                    pendingStartEntrySpeed = currentSpeedKmh
                }
            }

            DetectorState.PENDING_START -> {
                if (absLean >= cornerLeanThresholdDegrees && currentDirection == pendingStartDirection) {
                    if (nowMonotonicMs - pendingStartMonotonicMs >= startHysteresisMs) {
                        // Confirm active corner start!
                        state = DetectorState.ACTIVE_CORNER
                        activeCornerNumber++
                        activeDirection = pendingStartDirection
                        activeStartMonotonicMs = pendingStartMonotonicMs
                        activeRideStartOffsetMs = (pendingStartMonotonicMs - rideStartMonotonicMs).coerceAtLeast(0L)
                        activeEntrySpeed = pendingStartEntrySpeed
                        activeMaxLean = absLean
                        activeSpeedAtMaxLean = currentSpeedKmh
                        activeMaxSpeed = currentSpeedKmh
                        activeSpeedSum = currentSpeedKmh.toDouble()
                        activeSampleCount = 1
                    }
                } else {
                    // Sensor spike or direction flip before hysteresis elapsed -> reset to IDLE
                    state = DetectorState.IDLE
                }
            }

            DetectorState.ACTIVE_CORNER -> {
                // Check for S-curve transition (direction flipped while remaining >= 10°)
                if (absLean >= cornerLeanThresholdDegrees && currentDirection != activeDirection) {
                    // Finalize current corner immediately and switch to new direction
                    finalizeActiveCorner(exitSpeed = currentSpeedKmh, endMonotonicMs = nowMonotonicMs)
                    state = DetectorState.PENDING_START
                    pendingStartMonotonicMs = nowMonotonicMs
                    pendingStartDirection = currentDirection
                    pendingStartEntrySpeed = currentSpeedKmh
                    return
                }

                if (absLean >= cornerLeanThresholdDegrees) {
                    // Update corner metrics
                    if (absLean > activeMaxLean) {
                        activeMaxLean = absLean
                        activeSpeedAtMaxLean = currentSpeedKmh
                    }
                    if (currentSpeedKmh > activeMaxSpeed) {
                        activeMaxSpeed = currentSpeedKmh
                    }
                    activeSpeedSum += currentSpeedKmh
                    activeSampleCount++
                } else {
                    // Lean dropped below 10° -> enter PENDING_END
                    state = DetectorState.PENDING_END
                    pendingEndMonotonicMs = nowMonotonicMs
                    pendingEndExitSpeed = currentSpeedKmh
                }
            }

            DetectorState.PENDING_END -> {
                if (absLean < cornerLeanThresholdDegrees) {
                    if (nowMonotonicMs - pendingEndMonotonicMs >= endHysteresisMs) {
                        // Confirm corner end!
                        finalizeActiveCorner(exitSpeed = pendingEndExitSpeed, endMonotonicMs = pendingEndMonotonicMs)
                        state = DetectorState.IDLE
                    }
                } else if (currentDirection == activeDirection) {
                    // Returned to cornering in same direction -> stay in ACTIVE_CORNER
                    state = DetectorState.ACTIVE_CORNER
                    if (absLean > activeMaxLean) {
                        activeMaxLean = absLean
                        activeSpeedAtMaxLean = currentSpeedKmh
                    }
                    if (currentSpeedKmh > activeMaxSpeed) {
                        activeMaxSpeed = currentSpeedKmh
                    }
                    activeSpeedSum += currentSpeedKmh
                    activeSampleCount++
                } else {
                    // Flipped direction during pending end -> finalize previous corner and start new
                    finalizeActiveCorner(exitSpeed = currentSpeedKmh, endMonotonicMs = nowMonotonicMs)
                    state = DetectorState.PENDING_START
                    pendingStartMonotonicMs = nowMonotonicMs
                    pendingStartDirection = currentDirection
                    pendingStartEntrySpeed = currentSpeedKmh
                }
            }
        }
    }

    fun finishRide(nowMonotonicMs: Long): List<CornerEventEntity> {
        if (state == DetectorState.ACTIVE_CORNER || state == DetectorState.PENDING_END) {
            finalizeActiveCorner(
                exitSpeed = if (state == DetectorState.PENDING_END) pendingEndExitSpeed else 0f,
                endMonotonicMs = nowMonotonicMs
            )
        }
        state = DetectorState.IDLE
        return completedCorners.toList()
    }

    private fun finalizeActiveCorner(exitSpeed: Float, endMonotonicMs: Long) {
        val durationMs = (endMonotonicMs - activeStartMonotonicMs).coerceAtLeast(0L)
        if (durationMs >= minCornerDurationMs) {
            val avgSpeed = if (activeSampleCount > 0) (activeSpeedSum / activeSampleCount).toFloat() else activeEntrySpeed
            val event = CornerEventEntity(
                cornerNumber = activeCornerNumber,
                direction = activeDirection,
                maxLeanDegrees = activeMaxLean,
                speedAtMaxLeanKmh = activeSpeedAtMaxLean,
                entrySpeedKmh = activeEntrySpeed,
                exitSpeedKmh = exitSpeed,
                maxSpeedKmh = activeMaxSpeed,
                averageSpeedKmh = avgSpeed,
                durationMs = durationMs,
                timestampOffsetMs = activeRideStartOffsetMs
            )
            completedCorners.add(event)
        } else {
            // Rollback corner number if discarded as too short
            if (activeCornerNumber > 0) {
                activeCornerNumber--
            }
        }
    }

    fun getCompletedCorners(): List<CornerEventEntity> = completedCorners.toList()
}
