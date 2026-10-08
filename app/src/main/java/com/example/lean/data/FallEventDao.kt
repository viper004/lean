package com.example.lean.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FallEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFallEvent(event: FallEventEntity): Long

    @Query("SELECT * FROM fall_events ORDER BY timestampMs DESC")
    fun getAllFallEvents(): Flow<List<FallEventEntity>>
}
