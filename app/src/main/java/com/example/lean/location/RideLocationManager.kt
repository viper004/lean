package com.example.lean.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

data class LocationData(
    val currentSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val distanceKm: Float = 0f,
    val isGpsActive: Boolean = false,
    val isGpsPermissionGranted: Boolean = false,
    val statusMessage: String = "GPS Off",
    val speedTimestampMs: Long = 0L,
    val isSpeedValid: Boolean = false,
    val movingDurationMs: Long = 0L,
    val latitude: Double? = null,
    val longitude: Double? = null
)

class RideLocationManager(private val context: Context) : LocationListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _locationData = MutableStateFlow(LocationData())
    val locationData: StateFlow<LocationData> = _locationData.asStateFlow()

    private var previousLocation: Location? = null
    private var totalDistanceMeters: Float = 0f
    private var maxSpeedMps: Float = 0f
    private var lastValidSpeedMps: Float = 0f
    private var movingDurationMs: Long = 0L
    private var lastLocationTimeMs: Long = 0L
    private var isListening: Boolean = false

    fun checkPermissionAndStatus(): Boolean {
        val hasFine = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val isGranted = hasFine || hasCoarse

        _locationData.update {
            it.copy(
                isGpsPermissionGranted = isGranted,
                statusMessage = if (isGranted) {
                    if (it.isGpsActive) "GPS Active" else "GPS Off"
                } else {
                    "GPS Permission Denied"
                }
            )
        }
        return isGranted
    }

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
                    1000L, // 1 sec update interval
                    0f,    // 0 meters min distance interval to ensure continuous time updates
                    this
                )
            } else if (isNetworkProviderEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    0f,
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
        lastValidSpeedMps = 0f
        movingDurationMs = 0L
        lastLocationTimeMs = 0L
        _locationData.update {
            it.copy(
                currentSpeedKmh = 0f,
                maxSpeedKmh = 0f,
                distanceKm = 0f,
                speedTimestampMs = 0L,
                isSpeedValid = false,
                movingDurationMs = 0L
            )
        }
    }

    override fun onLocationChanged(location: Location) {
        val nowMonoMs = SystemClock.elapsedRealtime()

        // Filter out highly inaccurate location points (> 30m accuracy)
        if (location.hasAccuracy() && location.accuracy > 30f) {
            if (location.accuracy > 50f) {
                _locationData.update {
                    it.copy(
                        isGpsActive = true,
                        statusMessage = "GPS Low Accuracy (±${location.accuracy.toInt()}m)"
                    )
                }
                return
            }
        }

        val prevLoc = previousLocation
        val timeDiffSec = if (prevLoc != null) {
            val nanosDiff = location.elapsedRealtimeNanos - prevLoc.elapsedRealtimeNanos
            if (nanosDiff > 0) nanosDiff / 1_000_000_000f else (location.time - prevLoc.time) / 1000f
        } else 0f

        // Determine Speed: Prefer Location.speed when available; Fallback to coordinate distance/time
        var rawSpeedMps = 0f
        var hasValidSpeed = false

        if (location.hasSpeed() && location.speed >= 0f) {
            rawSpeedMps = location.speed
            hasValidSpeed = true
        } else if (prevLoc != null && timeDiffSec > 0.2f && timeDiffSec < 5.0f) {
            val dist = prevLoc.distanceTo(location)
            val calcSpeed = dist / timeDiffSec
            if (calcSpeed in 0f..83.33f) { // ~300 km/h sanity ceiling
                rawSpeedMps = calcSpeed
                hasValidSpeed = true
            }
        }

        // Motorcycle Acceleration/Braking Spike Filter (Max ~15 m/s² physical change)
        if (hasValidSpeed && lastValidSpeedMps > 0f && timeDiffSec > 0f) {
            val maxSpeedDelta = (15.0f * timeDiffSec).coerceAtLeast(5.0f)
            val diff = rawSpeedMps - lastValidSpeedMps
            if (abs(diff) > maxSpeedDelta) {
                rawSpeedMps = lastValidSpeedMps + (if (diff > 0) maxSpeedDelta else -maxSpeedDelta)
            }
        }

        // Noise floor: speed < 0.833 m/s (~3 km/h) treated as stationary
        if (rawSpeedMps < 0.833f) {
            rawSpeedMps = 0f
        }

        // Light Exponential Smoothing (alpha = 0.6f preserves responsiveness for corner entry/apex speed)
        val filteredSpeedMps = if (lastValidSpeedMps == 0f || rawSpeedMps == 0f) {
            rawSpeedMps
        } else {
            0.6f * rawSpeedMps + 0.4f * lastValidSpeedMps
        }
        lastValidSpeedMps = filteredSpeedMps

        // Distance and Moving Time Accumulation
        if (prevLoc != null && timeDiffSec > 0f) {
            val dist = prevLoc.distanceTo(location)
            if ((!location.hasAccuracy() || location.accuracy <= 25f) && dist > 0.5f && (dist / timeDiffSec) < 83.33f) {
                totalDistanceMeters += dist
            }
        }

        if (filteredSpeedMps > 0f && timeDiffSec > 0f && timeDiffSec < 5.0f) {
            movingDurationMs += (timeDiffSec * 1000f).toLong()
        }

        previousLocation = location
        lastLocationTimeMs = nowMonoMs

        // Maximum Speed Validation (requires good accuracy <= 20m)
        if (filteredSpeedMps > maxSpeedMps && (!location.hasAccuracy() || location.accuracy <= 20f)) {
            maxSpeedMps = filteredSpeedMps
        }

        val speedKmh = filteredSpeedMps * 3.6f
        val maxKmh = maxSpeedMps * 3.6f
        val distKm = totalDistanceMeters / 1000f

        _locationData.update {
            it.copy(
                currentSpeedKmh = speedKmh,
                maxSpeedKmh = maxKmh,
                distanceKm = distKm,
                isGpsActive = true,
                statusMessage = "GPS Active",
                speedTimestampMs = nowMonoMs,
                isSpeedValid = hasValidSpeed,
                movingDurationMs = movingDurationMs,
                latitude = location.latitude,
                longitude = location.longitude
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

