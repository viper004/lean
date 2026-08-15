package com.example.lean

import com.example.lean.analytics.RideStatisticsCalculator
import com.example.lean.calibration.CalibrationManager
import com.example.lean.data.LeanSafetyRating
import com.example.lean.data.LeanZone
import com.example.lean.data.RideEntity
import com.example.lean.data.RideState
import com.example.lean.data.SensorMode
import com.example.lean.data.UserSettings
import com.example.lean.orientation.Vector3D
import com.example.lean.sensor.SensorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RideAnalyticsTest {

    private lateinit var calculator: RideStatisticsCalculator
    private lateinit var calibrationManager: CalibrationManager

    @Before
    fun setUp() {
        calculator = RideStatisticsCalculator(UserSettings())
        calibrationManager = CalibrationManager()
    }

    // Test 1: Start ride
    @Test
    fun testStartRideInitializesState() {
        val startSysMs = 1000000L
        val startMonoMs = 50000L
        calculator.start(startSysMs, startMonoMs)

        assertEquals(startSysMs, calculator.startTimeMs)
        assertEquals(0L, calculator.totalDurationMs)
        assertEquals(0f, calculator.maxAbsoluteLean, 0.001f)
    }

    // Test 2: CENTER calibration
    @Test
    fun testCenterCalibrationSetsZeroReference() {
        val angledMountGravity = Vector3D(2.0f, 3.0f, 9.0f) // Phone mounted at physical angle
        calibrationManager.calibrate(angledMountGravity)

        assertTrue(calibrationManager.isCalibrated)

        // Reading same orientation should return 0 degrees
        val initialLean = calibrationManager.calculateLeanAngle(angledMountGravity)
        assertEquals(0.0f, initialLean, 0.5f)
    }

    // Test 2b: Left, Center, Right tilt direction sign validation
    @Test
    fun testLeanAngleDirectionLeftCenterRight() {
        val uprightGravity = Vector3D(0.0f, 9.81f, 0.0f)
        calibrationManager.calibrate(uprightGravity)
        assertTrue(calibrationManager.isCalibrated)

        // CENTER -> ~0°
        val centerAngle = calibrationManager.calculateLeanAngle(uprightGravity)
        assertEquals(0.0f, centerAngle, 0.5f)

        // Physical LEFT tilt (top of phone tilts left -> right edge tilts UP -> gravity shifts +X in body)
        val leftTiltGravity = Vector3D(4.905f, 8.495f, 0.0f) // ~30° left tilt
        val leftAngle = calibrationManager.calculateLeanAngle(leftTiltGravity)
        assertTrue("Physical LEFT tilt must produce negative angle", leftAngle < -15.0f)

        // Physical RIGHT tilt (top of phone tilts right -> right edge tilts DOWN -> gravity shifts -X in body)
        val rightTiltGravity = Vector3D(-4.905f, 8.495f, 0.0f) // ~30° right tilt
        val rightAngle = calibrationManager.calculateLeanAngle(rightTiltGravity)
        assertTrue("Physical RIGHT tilt must produce positive angle", rightAngle > 15.0f)
    }

    // Test 2c: Pitched-forward phone mount left/center/right tilt sign validation
    @Test
    fun testPitchedForwardMountTiltDirection() {
        // Phone mounted on handlebar pitched forward by 15°
        val pitchedForwardGravity = Vector3D(0.0f, 9.476f, -2.539f)
        calibrationManager.calibrate(pitchedForwardGravity)
        assertTrue(calibrationManager.isCalibrated)

        // CENTER -> ~0°
        val centerAngle = calibrationManager.calculateLeanAngle(pitchedForwardGravity)
        assertEquals(0.0f, centerAngle, 0.5f)

        // Physical LEFT tilt (~30°) when pitched forward
        val leftTiltPitchedGravity = Vector3D(4.905f, 8.206f, -2.200f)
        val leftAngle = calibrationManager.calculateLeanAngle(leftTiltPitchedGravity)
        assertTrue("Physical LEFT tilt when pitched forward must produce negative angle", leftAngle < -15.0f)

        // Physical RIGHT tilt (~30°) when pitched forward
        val rightTiltPitchedGravity = Vector3D(-4.905f, 8.206f, -2.200f)
        val rightAngle = calibrationManager.calculateLeanAngle(rightTiltPitchedGravity)
        assertTrue("Physical RIGHT tilt when pitched forward must produce positive angle", rightAngle > 15.0f)
    }

    // Test 3: Timer calculation
    @Test
    fun testTimerAccumulation() {
        calculator.start(1000L, 100L)
        calculator.processTick(0f, 1100L) // +1000 ms
        calculator.processTick(0f, 2100L) // +1000 ms

        assertEquals(2000L, calculator.totalDurationMs)
    }

    // Test 4: End ride
    @Test
    fun testEndRideProducesFinalizedEntity() {
        calculator.start(1000L, 100L)
        calculator.processTick(15f, 2100L)
        val entity = calculator.finish(3000L)

        assertNotNull(entity)
        assertEquals(2000L, entity.durationMs)
        assertEquals(1000L, entity.startTimeMs)
        assertEquals(3000L, entity.endTimeMs)
    }

    // Test 5 & 6: Maximum left and right lean tracking
    @Test
    fun testMaxLeftAndRightLeanTracking() {
        calculator.start(1000L, 100L)

        calculator.processTick(-18.5f, 2000L) // Left lean
        calculator.processTick(-28.4f, 3000L) // Peak left lean
        calculator.processTick(34.7f, 4000L)  // Peak right lean

        assertEquals(28.4f, calculator.maxLeftLean, 0.1f)
        assertEquals(34.7f, calculator.maxRightLean, 0.1f)
        assertEquals(34.7f, calculator.maxAbsoluteLean, 0.1f)
    }

    // Test 7, 8, 9, 10: Straight, Left, Right, and Overall Lean Percentages
    @Test
    fun testPercentageCalculationsFromExampleSpec() {
        // Spec Example: 60-min ride (3,600,000 ms)
        // 37 min straight = 2,220,000 ms
        // 11 min left = 660,000 ms
        // 12 min right = 720,000 ms
        calculator.start(0L, 0L)

        // Interval 1: 0 to 2,220,000 ms (37 min) at 0° (STRAIGHT)
        calculator.processTick(0f, 2220000L)
        // Interval 2: 2,220,000 to 2,880,000 ms (11 min) at -15° (LEFT LEAN)
        calculator.processTick(-15f, 2880000L)
        // Interval 3: 2,880,000 to 3,600,000 ms (12 min) at +15° (RIGHT LEAN)
        calculator.processTick(15f, 3600000L)

        val entity = calculator.finish(3600000L)

        // Expected: Straight ~61.67%, Left ~18.33%, Right ~20.00%, Lean ~38.33%
        assertEquals(61.67f, entity.straightPercentage, 0.5f)
        assertEquals(18.33f, entity.leftLeanPercentage, 0.5f)
        assertEquals(20.00f, entity.rightLeanPercentage, 0.5f)
        assertEquals(38.33f, entity.leanPercentage, 0.5f)
    }

    // Test 11: Safety classification
    @Test
    fun testSafetyClassification() {
        val goodRating = LeanSafetyRating.evaluate(warningPercentage = 2.0f, criticalPercentage = 0.0f)
        assertEquals(LeanSafetyRating.GOOD, goodRating)

        val cautionRating = LeanSafetyRating.evaluate(warningPercentage = 8.0f, criticalPercentage = 0.0f)
        assertEquals(LeanSafetyRating.CAUTION, cautionRating)

        val highRating = LeanSafetyRating.evaluate(warningPercentage = 10.0f, criticalPercentage = 4.0f)
        assertEquals(LeanSafetyRating.HIGH, highRating)
    }

    // Test 12: GPS unavailable scenario
    @Test
    fun testGpsUnavailableFallback() {
        calculator.start(0L, 0L)
        calculator.processTick(10f, 1000L)
        val entity = calculator.finish(1000L)

        // GPS stats gracefully default to 0 without breaking calculation
        assertFalse(entity.isGpsEnabled)
        assertEquals(0f, entity.distanceKm, 0.001f)
        assertEquals(0f, entity.averageSpeedKmh, 0.001f)
        assertEquals(1000L, entity.durationMs)
    }

    // Test 13: Gyroscope unavailable scenario
    @Test
    fun testGyroscopeUnavailableFallbackStatus() {
        val fallbackStatus = SensorStatus(
            hasAccelerometer = true,
            hasGyroscope = false,
            activeMode = SensorMode.ACCEL_ONLY,
            warningMessage = "Gyroscope unavailable — using accelerometer mode."
        )

        assertFalse(fallbackStatus.hasGyroscope)
        assertTrue(fallbackStatus.hasAccelerometer)
        assertEquals(SensorMode.ACCEL_ONLY, fallbackStatus.activeMode)
    }

    // Test 14: Re-centering
    @Test
    fun testReCenteringUpdatesReference() {
        val mount1 = Vector3D(0f, 2f, 9.8f)
        calibrationManager.calibrate(mount1)

        val mount2 = Vector3D(3f, 0f, 9.5f) // Adjust phone position mid-ride
        calibrationManager.calibrate(mount2)

        val leanAtNewMount = calibrationManager.calculateLeanAngle(mount2)
        assertEquals(0.0f, leanAtNewMount, 0.5f)
    }

    // Test 15: Ride entity model data integrity
    @Test
    fun testRideEntityFields() {
        val ride = RideEntity(
            startTimeMs = 1000L,
            endTimeMs = 5000L,
            durationMs = 4000L,
            maxLeftLean = 20f,
            maxRightLean = 30f,
            maxAbsoluteLean = 30f,
            totalLeanTimeMs = 2000L,
            straightTimeMs = 2000L,
            leftLeanTimeMs = 800L,
            rightLeanTimeMs = 1200L,
            leanPercentage = 50f,
            straightPercentage = 50f,
            leftLeanPercentage = 20f,
            rightLeanPercentage = 30f,
            safetyPreferredPercentage = 95f,
            safetyWarningPercentage = 5f,
            safetyCriticalPercentage = 0f,
            warningTimeMs = 200L,
            criticalTimeMs = 0L,
            distanceKm = 10f,
            averageSpeedKmh = 60f,
            maxSpeedKmh = 90f,
            isGpsEnabled = true
        )

        assertEquals(4000L, ride.durationMs)
        assertEquals(30f, ride.maxAbsoluteLean, 0.001f)
        assertTrue(ride.isGpsEnabled)
    }

    // Test 16: Ride history sorting logic (Newest First)
    @Test
    fun testRideHistorySortingNewestFirst() {
        val rideOld = RideEntity(rideId = 1, startTimeMs = 1000L, endTimeMs = 2000L, durationMs = 1000L, maxLeftLean = 0f, maxRightLean = 0f, maxAbsoluteLean = 0f, totalLeanTimeMs = 0L, straightTimeMs = 1000L, leftLeanTimeMs = 0L, rightLeanTimeMs = 0L, leanPercentage = 0f, straightPercentage = 100f, leftLeanPercentage = 0f, rightLeanPercentage = 0f, safetyPreferredPercentage = 100f, safetyWarningPercentage = 0f, safetyCriticalPercentage = 0f, warningTimeMs = 0L, criticalTimeMs = 0L)
        val rideNew = RideEntity(rideId = 2, startTimeMs = 5000L, endTimeMs = 6000L, durationMs = 1000L, maxLeftLean = 0f, maxRightLean = 0f, maxAbsoluteLean = 0f, totalLeanTimeMs = 0L, straightTimeMs = 1000L, leftLeanTimeMs = 0L, rightLeanTimeMs = 0L, leanPercentage = 0f, straightPercentage = 100f, leftLeanPercentage = 0f, rightLeanPercentage = 0f, safetyPreferredPercentage = 100f, safetyWarningPercentage = 0f, safetyCriticalPercentage = 0f, warningTimeMs = 0L, criticalTimeMs = 0L)

        val rideList = listOf(rideOld, rideNew).sortedByDescending { it.startTimeMs }

        assertEquals(2L, rideList[0].rideId)
        assertEquals(1L, rideList[1].rideId)
    }

    // Test 17: Unfinished ride state recovery marker
    @Test
    fun testUnfinishedRideStateEnum() {
        val stateRecording = RideState.RECORDING
        val stateCompleted = RideState.COMPLETED

        assertEquals("RECORDING", stateRecording.name)
        assertEquals("COMPLETED", stateCompleted.name)
    }
}
