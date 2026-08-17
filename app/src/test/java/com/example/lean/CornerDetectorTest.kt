package com.example.lean

import com.example.lean.analytics.CornerDetector
import com.example.lean.data.CornerDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CornerDetectorTest {

    private lateinit var detector: CornerDetector

    @Before
    fun setUp() {
        detector = CornerDetector()
    }

    @Test
    fun testNoCornersDetectedOnStraightRoad() {
        val rideStartMonoMs = 1000L
        for (i in 0..10) {
            detector.processTick(
                filteredLeanDegrees = 5.0f,
                currentSpeedKmh = 60.0f,
                nowMonotonicMs = rideStartMonoMs + i * 100L,
                rideStartMonotonicMs = rideStartMonoMs
            )
        }

        val corners = detector.finishRide(rideStartMonoMs + 1000L)
        assertTrue(corners.isEmpty())
    }

    @Test
    fun testSingleSustainedCornerDetectedWithExactSpeedAtMaxLean() {
        val rideStartMonoMs = 1000L

        // Tick 0 to 4: 0ms to 400ms at -15° (PENDING_START -> ACTIVE_CORNER)
        detector.processTick(-15.0f, 50.0f, rideStartMonoMs, rideStartMonoMs)
        detector.processTick(-15.0f, 52.0f, rideStartMonoMs + 200L, rideStartMonoMs)
        detector.processTick(-15.0f, 54.0f, rideStartMonoMs + 400L, rideStartMonoMs) // Corner becomes ACTIVE

        // Tick 5: Peak lean -28° at speed 58 km/h
        detector.processTick(-28.0f, 58.0f, rideStartMonoMs + 1000L, rideStartMonoMs)

        // Tick 6: Ease out -12° at speed 50 km/h
        detector.processTick(-12.0f, 50.0f, rideStartMonoMs + 2000L, rideStartMonoMs)

        // Lean drops below 10° for 500ms (PENDING_END -> Finalize)
        detector.processTick(0.0f, 45.0f, rideStartMonoMs + 2500L, rideStartMonoMs)
        detector.processTick(0.0f, 40.0f, rideStartMonoMs + 3000L, rideStartMonoMs)

        val corners = detector.getCompletedCorners()
        assertEquals(1, corners.size)

        val corner = corners[0]
        assertEquals(1, corner.cornerNumber)
        assertEquals(CornerDirection.LEFT, corner.direction)
        assertEquals(28.0f, corner.maxLeanDegrees, 0.1f)
        assertEquals(58.0f, corner.speedAtMaxLeanKmh, 0.1f)
        assertEquals(50.0f, corner.entrySpeedKmh, 0.1f)
        assertEquals(45.0f, corner.exitSpeedKmh, 0.1f)
        assertTrue("Corner duration should be >= 2000ms", corner.durationMs >= 2000L)
    }

    @Test
    fun testShortSpikeFilteredByHysteresis() {
        val rideStartMonoMs = 1000L

        // Lean spikes to -25° for only 200ms (< 400ms hysteresis requirement)
        detector.processTick(-25.0f, 60.0f, rideStartMonoMs, rideStartMonoMs)
        detector.processTick(-25.0f, 60.0f, rideStartMonoMs + 200L, rideStartMonoMs)
        detector.processTick(0.0f, 60.0f, rideStartMonoMs + 300L, rideStartMonoMs)

        val corners = detector.finishRide(rideStartMonoMs + 1000L)
        assertTrue("Transient lean spike < 400ms must not trigger a corner event", corners.isEmpty())
    }

    @Test
    fun testSCurveTransitionsDirectlyToNewCorner() {
        val rideStartMonoMs = 1000L

        // LEFT corner (-20°) for 1000ms
        for (i in 0..10) {
            detector.processTick(-20.0f, 50.0f, rideStartMonoMs + i * 100L, rideStartMonoMs)
        }

        // Instant flip to RIGHT corner (+20°) for 1000ms
        for (i in 11..21) {
            detector.processTick(20.0f, 55.0f, rideStartMonoMs + i * 100L, rideStartMonoMs)
        }

        // Return to straight
        detector.processTick(0.0f, 40.0f, rideStartMonoMs + 2600L, rideStartMonoMs)
        detector.processTick(0.0f, 40.0f, rideStartMonoMs + 3100L, rideStartMonoMs)

        val corners = detector.getCompletedCorners()
        assertEquals(2, corners.size)

        assertEquals(CornerDirection.LEFT, corners[0].direction)
        assertEquals(20.0f, corners[0].maxLeanDegrees, 0.1f)

        assertEquals(CornerDirection.RIGHT, corners[1].direction)
        assertEquals(20.0f, corners[1].maxLeanDegrees, 0.1f)
    }

    @Test
    fun testFinishRideFinalizesActiveCorner() {
        val rideStartMonoMs = 1000L

        // Corner active for 1500ms when ride ends
        for (i in 0..15) {
            detector.processTick(-18.0f, 45.0f, rideStartMonoMs + i * 100L, rideStartMonoMs)
        }

        val corners = detector.finishRide(rideStartMonoMs + 1600L)
        assertEquals(1, corners.size)
        assertEquals(CornerDirection.LEFT, corners[0].direction)
    }
}
