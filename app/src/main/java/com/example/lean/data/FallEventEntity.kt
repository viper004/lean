package com.example.lean.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fall_events")
data class FallEventEntity(
    @PrimaryKey(autoGenerate = true)
    val fallId: Long = 0,
    val timestampMs: Long,
    val maxLeanAngle: Float,
    val speedKmh: Float,
    val latitude: Double,
    val longitude: Double
)
