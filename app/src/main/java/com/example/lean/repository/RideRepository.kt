package com.example.lean.repository

import com.example.lean.data.CornerEventDao
import com.example.lean.data.CornerEventEntity
import com.example.lean.data.RideDao
import com.example.lean.data.RideEntity
import kotlinx.coroutines.flow.Flow

class RideRepository(
    private val rideDao: RideDao,
    private val cornerEventDao: CornerEventDao
) {

    val allRides: Flow<List<RideEntity>> = rideDao.getAllRides()
    val totalRideCount: Flow<Int> = rideDao.getTotalRideCount()
    val totalRideTimeMs: Flow<Long?> = rideDao.getTotalRideTimeMs()
    val totalDistanceKm: Flow<Float?> = rideDao.getTotalDistanceKm()
    val bestLeanAngle: Flow<Float?> = rideDao.getBestLeanAngle()
    val mostRecentRide: Flow<RideEntity?> = rideDao.getMostRecentRide()

    suspend fun saveRide(ride: RideEntity, corners: List<CornerEventEntity> = emptyList()): Long {
        val rideId = rideDao.insertRide(ride)
        if (corners.isNotEmpty()) {
            val updatedCorners = corners.map { it.copy(rideId = rideId) }
            cornerEventDao.insertCorners(updatedCorners)
        }
        return rideId
    }

    suspend fun getRideById(rideId: Long): RideEntity? {
        return rideDao.getRideById(rideId)
    }

    fun getCornersForRide(rideId: Long): Flow<List<CornerEventEntity>> {
        return cornerEventDao.getCornersForRide(rideId)
    }

    suspend fun getCornersForRideSync(rideId: Long): List<CornerEventEntity> {
        return cornerEventDao.getCornersForRideSync(rideId)
    }

    suspend fun deleteRide(rideId: Long) {
        cornerEventDao.deleteCornersForRide(rideId)
        rideDao.deleteRide(rideId)
    }
}
