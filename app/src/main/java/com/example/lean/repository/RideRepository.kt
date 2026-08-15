package com.example.lean.repository

import com.example.lean.data.RideDao
import com.example.lean.data.RideEntity
import kotlinx.coroutines.flow.Flow

class RideRepository(private val rideDao: RideDao) {

    val allRides: Flow<List<RideEntity>> = rideDao.getAllRides()
    val totalRideCount: Flow<Int> = rideDao.getTotalRideCount()
    val totalRideTimeMs: Flow<Long?> = rideDao.getTotalRideTimeMs()
    val totalDistanceKm: Flow<Float?> = rideDao.getTotalDistanceKm()
    val bestLeanAngle: Flow<Float?> = rideDao.getBestLeanAngle()
    val mostRecentRide: Flow<RideEntity?> = rideDao.getMostRecentRide()

    suspend fun saveRide(ride: RideEntity): Long {
        return rideDao.insertRide(ride)
    }

    suspend fun getRideById(rideId: Long): RideEntity? {
        return rideDao.getRideById(rideId)
    }

    suspend fun deleteRide(rideId: Long) {
        rideDao.deleteRide(rideId)
    }
}
