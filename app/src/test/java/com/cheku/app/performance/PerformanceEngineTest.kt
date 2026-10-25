package com.cheku.app.performance

import com.cheku.app.data.PerformanceTestType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceEngineTest {
    @Test
    fun crossingInterpolatesTimeAndDistance() {
        val crossing = PerformanceEngine.crossing(0, 0.0, 0.0, 1_000_000_000, 20.0, 10.0, 10.0)!!
        assertEquals(500_000_000L, crossing.timestampNanos)
        assertEquals(5.0, crossing.distanceMeters, 0.0001)
    }

    @Test
    fun accelerationUsesMonotonicClockAndInterpolatedFinish() {
        val processor = PerformanceSessionProcessor(PerformanceTestType.Acceleration100)
        processor.accept(frame(0, 0.0, 0.0))
        processor.accept(frame(2_100_000_000, 0.0, 0.0))
        processor.accept(frame(2_200_000_000, 1.0, 0.05, 1f))
        val result = processor.accept(frame(7_200_000_000, 30.0, 80.0, 3f)).result!!

        assertEquals(4.717, result.elapsedSeconds, 0.01)
        assertTrue(result.distanceMeters > 70.0)
    }

    @Test
    fun brakingStartsAtTargetCrossing() {
        val processor = PerformanceSessionProcessor(PerformanceTestType.Braking50)
        processor.accept(frame(0, 15.0, 0.0))
        processor.accept(frame(100_000_000, 14.5, 1.5))
        processor.accept(frame(200_000_000, 13.0, 2.8, -4f))
        val result = processor.accept(frame(2_000_000_000, 0.0, 18.0, -2f)).result!!

        assertTrue(result.elapsedSeconds > 1.0)
        assertTrue(result.distanceMeters > 10.0)
    }

    @Test
    fun qualityRequiresFiveHertzAndFiveMeterAccuracy() {
        val good = (0..10).map { frame(it * 200_000_000L, it.toDouble(), it.toDouble(), accuracy = 3f) }
        val bad = (0..3).map { frame(it * 1_000_000_000L, it.toDouble(), it.toDouble(), accuracy = 12f) }

        assertTrue(PerformanceQualityEvaluator.evaluate(good).valid)
        assertFalse(PerformanceQualityEvaluator.evaluate(bad).valid)
    }

    @Test
    fun vehicleProjectionIsIndependentOfPortraitOrLandscapeAxes() {
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        val northAcceleration = VehicleAxisProjector.project(floatArrayOf(0f, 2f, 0f), identity, 0f)
        val eastAcceleration = VehicleAxisProjector.project(floatArrayOf(2f, 0f, 0f), identity, 90f)

        assertEquals(2f, northAcceleration.longitudinalMps2, 0.001f)
        assertEquals(2f, eastAcceleration.longitudinalMps2, 0.001f)
    }

    @Test
    fun combinedTestEmitsAllFourSplitsOnce() {
        val processor = CombinedPerformanceProcessor()
        val emitted = mutableListOf<CombinedPerformanceResult>()
        emitted += processor.accept(frame(0, 0.0, 0.0))
        emitted += processor.accept(frame(2_100_000_000, 0.0, 0.0))
        emitted += processor.accept(frame(2_200_000_000, 1.0, 0.1, 1f))
        emitted += processor.accept(frame(4_000_000_000, 15.0, 20.0, 3f))
        emitted += processor.accept(frame(7_000_000_000, 28.2, 80.0, 2f))
        emitted += processor.accept(frame(7_200_000_000, 29.0, 86.0, 1f))
        emitted += processor.accept(frame(7_300_000_000, 28.8, 89.0, -2f))
        emitted += processor.accept(frame(7_500_000_000, 27.0, 94.0, -4f))
        emitted += processor.accept(frame(8_300_000_000, 13.0, 108.0, -5f))
        emitted += processor.accept(frame(10_000_000_000, 0.0, 118.0, -2f))

        assertEquals(
            setOf("0-50 km/h", "0-100 km/h", "50-0 km/h", "100-0 km/h"),
            emitted.map { it.label }.toSet()
        )
        assertEquals(4, emitted.size)
        assertTrue(processor.isComplete())
    }

    @Test
    fun combinedTestDoesNotCompleteWithoutReachingOneHundred() {
        val processor = CombinedPerformanceProcessor()
        processor.accept(frame(0, 0.0, 0.0))
        processor.accept(frame(2_100_000_000, 0.0, 0.0))
        processor.accept(frame(2_200_000_000, 1.0, 0.1, 1f))
        processor.accept(frame(5_000_000_000, 20.0, 40.0, 2f))
        processor.accept(frame(8_000_000_000, 0.0, 70.0, -3f))

        assertFalse(processor.isComplete())
    }

    @Test
    fun combinedTestCapturesHundredCrossingOnFirstBrakingFrame() {
        val processor = CombinedPerformanceProcessor()
        val emitted = mutableListOf<CombinedPerformanceResult>()
        emitted += processor.accept(frame(0, 0.0, 0.0))
        emitted += processor.accept(frame(2_100_000_000, 0.0, 0.0))
        emitted += processor.accept(frame(2_200_000_000, 1.0, 0.1, 1f))
        emitted += processor.accept(frame(4_000_000_000, 15.0, 20.0, 3f))
        emitted += processor.accept(frame(7_000_000_000, 28.2, 80.0, 2f))
        emitted += processor.accept(frame(7_200_000_000, 29.0, 86.0, 1f))
        emitted += processor.accept(frame(7_500_000_000, 27.0, 94.0, -4f))
        emitted += processor.accept(frame(8_300_000_000, 13.0, 108.0, -5f))
        emitted += processor.accept(frame(10_000_000_000, 0.0, 118.0, -2f))

        assertTrue(processor.isComplete())
        assertTrue(emitted.any { it.label == "100-0 km/h" })
        assertTrue(processor.summaryResult() != null)
    }

    private fun frame(
        nanos: Long,
        speed: Double,
        distance: Double,
        acceleration: Float = 0f,
        accuracy: Float = 3f
    ) = PerformanceFrame(nanos, nanos / 1_000_000, speed, distance, accuracy, acceleration)
}
