package com.cheku.app.replay

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplaySeriesTest {
    @Test
    fun movingAverageKeepsSampleCountAndSmoothsSpikes() {
        val result = ReplaySeries.movingAverage(listOf(0.0, 0.0, 10.0, 0.0, 0.0), radius = 1)

        assertEquals(5, result.size)
        assertEquals(10.0 / 3.0, result[2], 0.001)
    }

    @Test
    fun emptyInputIsStable() {
        assertEquals(emptyList<Double>(), ReplaySeries.movingAverage(emptyList()))
    }
}
