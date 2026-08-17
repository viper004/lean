package com.example.lean.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CornerEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCorners(corners: List<CornerEventEntity>)

    @Query("SELECT * FROM corner_events WHERE rideId = :rideId ORDER BY cornerNumber ASC")
    fun getCornersForRide(rideId: Long): Flow<List<CornerEventEntity>>

    @Query("SELECT * FROM corner_events WHERE rideId = :rideId ORDER BY cornerNumber ASC")
    suspend fun getCornersForRideSync(rideId: Long): List<CornerEventEntity>

    @Query("DELETE FROM corner_events WHERE rideId = :rideId")
    suspend fun deleteCornersForRide(rideId: Long)
}
