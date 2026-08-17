package com.example.lean.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CornerDirection {
    LEFT, RIGHT
}

@Entity(
    tableName = "corner_events",
    foreignKeys = [
        ForeignKey(
            entity = RideEntity::class,
            parentColumns = ["rideId"],
            childColumns = ["rideId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["rideId"])]
)
data class CornerEventEntity(
    @PrimaryKey(autoGenerate = true)
    val cornerId: Long = 0,
    val rideId: Long = 0,
    val cornerNumber: Int,
    val direction: CornerDirection,
    val maxLeanDegrees: Float,
    val speedAtMaxLeanKmh: Float,
    val entrySpeedKmh: Float,
    val exitSpeedKmh: Float,
    val maxSpeedKmh: Float,
    val averageSpeedKmh: Float,
    val durationMs: Long,
    val timestampOffsetMs: Long
)
