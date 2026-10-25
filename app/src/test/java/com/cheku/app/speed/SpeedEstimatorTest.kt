package com.cheku.app.speed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedEstimatorTest {
    @Test
    fun smoothsNoisyLocationSamples() {
        val result = SpeedEstimator.update(
            rawSpeedMps = 20.0,
            previousSpeedMps = 10.0,
            dtSeconds = 1.0,
            speedAccuracyMps = 2.0,
            locationAccuracyM = 20f
        )

        assertEquals(18.2, result.speedMps, 0.0001)
        assertFalse(result.outlier)
    }

    @Test
    fun rejectsLargeJumpFromPoorLocationFix() {
        val result = SpeedEstimator.update(
            rawSpeedMps = 30.0,
            previousSpeedMps = 2.0,
            dtSeconds = 0.2,
            locationAccuracyM = 30f
        )

        assertEquals(2.0, result.speedMps, 0.0001)
        assertTrue(result.outlier)
    }

    @Test
    fun nonPositiveDtKeepsPreviousInsteadOfPassingRawThrough() {
        val zeroDt = SpeedEstimator.update(rawSpeedMps = 0.0, previousSpeedMps = 16.7, dtSeconds = 0.0)
        val negativeDt = SpeedEstimator.update(rawSpeedMps = 0.0, previousSpeedMps = 16.7, dtSeconds = -1.5)

        assertEquals(16.7, zeroDt.speedMps, 0.0001)
        assertEquals(16.7, negativeDt.speedMps, 0.0001)
        assertTrue(zeroDt.outlier)
    }
}
