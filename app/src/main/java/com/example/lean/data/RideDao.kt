package com.example.lean.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRide(ride: RideEntity): Long

    @Query("SELECT * FROM rides ORDER BY startTimeMs DESC")
    fun getAllRides(): Flow<List<RideEntity>>

    @Query("SELECT * FROM rides WHERE rideId = :rideId")
    suspend fun getRideById(rideId: Long): RideEntity?

    @Query("DELETE FROM rides WHERE rideId = :rideId")
    suspend fun deleteRide(rideId: Long)

    @Query("SELECT COUNT(*) FROM rides")
    fun getTotalRideCount(): Flow<Int>

    @Query("SELECT SUM(durationMs) FROM rides")
    fun getTotalRideTimeMs(): Flow<Long?>

    @Query("SELECT SUM(distanceKm) FROM rides")
    fun getTotalDistanceKm(): Flow<Float?>

    @Query("SELECT MAX(maxAbsoluteLean) FROM rides")
    fun getBestLeanAngle(): Flow<Float?>

    @Query("SELECT * FROM rides ORDER BY startTimeMs DESC LIMIT 1")
    fun getMostRecentRide(): Flow<RideEntity?>
}
