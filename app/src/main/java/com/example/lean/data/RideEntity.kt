package com.example.lean.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey(autoGenerate = true)
    val rideId: Long = 0,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationMs: Long,
    val maxLeftLean: Float,
    val maxRightLean: Float,
    val maxAbsoluteLean: Float,
    val totalLeanTimeMs: Long,
    val straightTimeMs: Long,
    val leftLeanTimeMs: Long,
    val rightLeanTimeMs: Long,
    val leanPercentage: Float,
    val straightPercentage: Float,
    val leftLeanPercentage: Float,
    val rightLeanPercentage: Float,
    val safetyPreferredPercentage: Float,
    val safetyWarningPercentage: Float,
    val safetyCriticalPercentage: Float,
    val warningTimeMs: Long,
    val criticalTimeMs: Long,
    val distanceKm: Float = 0f,
    val averageSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val isGpsEnabled: Boolean = false
)
