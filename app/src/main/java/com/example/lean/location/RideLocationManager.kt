package com.example.lean.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LocationData(
    val currentSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val distanceKm: Float = 0f,
    val isGpsActive: Boolean = false,
    val isGpsPermissionGranted: Boolean = false,
    val statusMessage: String = "GPS Off"
)

class RideLocationManager(private val context: Context) : LocationListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _locationData = MutableStateFlow(LocationData())
    val locationData: StateFlow<LocationData> = _locationData.asStateFlow()

    private var previousLocation: Location? = null
    private var totalDistanceMeters: Float = 0f
    private var maxSpeedMps: Float = 0f
    private var isListening: Boolean = false

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (isListening) return

        val isGpsProviderEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkProviderEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        val hasFine = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            _locationData.update {
                it.copy(
                    isGpsActive = false,
                    isGpsPermissionGranted = false,
                    statusMessage = "GPS Permission Denied"
                )
            }
            return
        }

        _locationData.update {
            it.copy(
                isGpsPermissionGranted = true,
                isGpsActive = isGpsProviderEnabled || isNetworkProviderEnabled,
                statusMessage = if (isGpsProviderEnabled) "GPS Active" else "Searching for GPS..."
            )
        }

        try {
            if (isGpsProviderEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L, // 1 sec
                    1f,    // 1 meter
                    this
                )
            } else if (isNetworkProviderEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    2f,
                    this
                )
            }
            isListening = true
        } catch (e: Exception) {
            _locationData.update {
                it.copy(isGpsActive = false, statusMessage = "GPS Unavailable")
            }
        }
    }

    fun stopListening() {
        if (!isListening) return
        try {
            locationManager.removeUpdates(this)
        } catch (e: Exception) {
            // ignore
        }
        isListening = false
        _locationData.update { it.copy(isGpsActive = false) }
    }

    fun resetStats() {
        previousLocation = null
        totalDistanceMeters = 0f
        maxSpeedMps = 0f
        _locationData.update {
            it.copy(
                currentSpeedKmh = 0f,
                maxSpeedKmh = 0f,
                distanceKm = 0f
            )
        }
    }

    override fun onLocationChanged(location: Location) {
        // Filter out inaccurate location points
        if (location.hasAccuracy() && location.accuracy > 50f) return

        val prevLoc = previousLocation
        if (prevLoc != null) {
            val dist = prevLoc.distanceTo(location)
            // Filter noise spikes (e.g. unrealistic speed leaps > 250 km/h)
            val timeDiffSec = (location.time - prevLoc.time) / 1000f
            if (timeDiffSec > 0) {
                val calcSpeedMps = dist / timeDiffSec
                if (calcSpeedMps < 70f) { // ~250 km/h sanity check
                    totalDistanceMeters += dist
                }
            }
        }
        previousLocation = location

        var currentSpeedMps = if (location.hasSpeed()) location.speed else 0f
        if (currentSpeedMps < 0.5f) currentSpeedMps = 0f // noise floor (~1.8 km/h)

        if (currentSpeedMps > maxSpeedMps) {
            maxSpeedMps = currentSpeedMps
        }

        val speedKmh = currentSpeedMps * 3.6f
        val maxKmh = maxSpeedMps * 3.6f
        val distKm = totalDistanceMeters / 1000f

        _locationData.update {
            it.copy(
                currentSpeedKmh = speedKmh,
                maxSpeedKmh = maxKmh,
                distanceKm = distKm,
                isGpsActive = true,
                statusMessage = "GPS Active"
            )
        }
    }

    @Deprecated("Deprecated in API 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {
        _locationData.update { it.copy(isGpsActive = true, statusMessage = "GPS Active") }
    }
    override fun onProviderDisabled(provider: String) {
        _locationData.update { it.copy(isGpsActive = false, statusMessage = "GPS Disabled") }
    }
}
