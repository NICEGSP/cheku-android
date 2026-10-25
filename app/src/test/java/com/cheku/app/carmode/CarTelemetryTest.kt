package com.cheku.app.carmode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 车机页数值换算的单测 */
class CarTelemetryTest {
    @Test
    fun driftInsideAccuracyRadiusIsNotMovement() {
        // 室内实测：精度约 70 米，每秒漂 9 米 —— 直接相除就是 34 km/h
        assertEquals(0.0, derivedSpeedMps(9.4, 1.0, 69.6f, 69.6f)!!, 0.0)
        // 精度 5 米时，1 秒走 15 米是真实位移
        assertEquals(15.0, derivedSpeedMps(15.0, 1.0, 5f, 5f)!!, 1e-9)
        assertEquals(null, derivedSpeedMps(15.0, 0.0, 5f, 5f))
    }

    @Test
    fun stillPhoneWithPoorFixIsStationary() {
        assertTrue(isLikelyStationary(rawMps = 9.4, accuracyM = 69.6f, speedAccuracyMps = null, deviceStill = true))
        assertTrue("精度未知也不信", isLikelyStationary(9.4, 0f, null, deviceStill = true))
    }

    @Test
    fun goodFixWhileDrivingIsTrustedEvenIfSensorsAreQuiet() {
        // 平稳行驶时传感器可能误判为静止：精度好就仍然相信 GPS
        assertFalse(isLikelyStationary(rawMps = 16.7, accuracyM = 4f, speedAccuracyMps = 0.4, deviceStill = true))
        assertFalse(isLikelyStationary(rawMps = 16.7, accuracyM = 35f, speedAccuracyMps = null, deviceStill = false))
    }

    @Test
    fun speedBelowDeadbandOrItsOwnErrorIsZero() {
        assertTrue(isLikelyStationary(0.5, 4f, null, deviceStill = false))
        assertTrue("速度 2 m/s、误差 1.6 m/s：与 0 区分不开", isLikelyStationary(2.0, 4f, 1.6, deviceStill = false))
        assertFalse(isLikelyStationary(2.0, 4f, 0.5, deviceStill = false))
    }

    @Test
    fun motionSamplesAndStillWindow() {
        assertFalse("平放：只有重力", isAccelMotion(0f, 0f, 9.81f))
        val flat = floatArrayOf(0f, 0f, 9.81f)
        assertTrue("侧向晃动：和上一次采样相比变化明显", isAccelMotion(0.5f, 0f, 9.81f, flat))
        assertFalse("桌面噪声", isAccelMotion(0.02f, 0.01f, 9.80f, flat))
        assertTrue("加减速：模长偏离重力", isAccelMotion(0f, 0f, 10.3f))
        assertFalse(isGyroMotion(0.01f, 0.02f, 0.0f))
        assertTrue(isGyroMotion(0.1f, 0f, 0f))
        assertFalse(isDeviceStill(nowMs = 4_999L, lastMotionMs = 0L))
        assertTrue(isDeviceStill(nowMs = STILL_AFTER_MS, lastMotionMs = 0L))
    }

    @Test
    fun singleZeroWhileDrivingIsHeldOnce() {
        assertTrue(isSuspectZeroSpeed(rawMps = 0.0, previousMps = 16.7, alreadyHeld = false))
        assertFalse(isSuspectZeroSpeed(rawMps = 0.0, previousMps = 16.7, alreadyHeld = true))
    }

    @Test
    fun zeroAtLowSpeedOrNonZeroIsNotSuspect() {
        assertFalse(isSuspectZeroSpeed(rawMps = 0.0, previousMps = 2.0, alreadyHeld = false))
        assertFalse(isSuspectZeroSpeed(rawMps = 0.0, previousMps = null, alreadyHeld = false))
        assertFalse(isSuspectZeroSpeed(rawMps = 12.0, previousMps = 16.7, alreadyHeld = false))
    }

    @Test
    fun speedConversionUsesExactFactor() {
        assertEquals(0.0, mpsToKmh(0.0), 0.0)
        assertEquals(3.6, mpsToKmh(1.0), 1e-9)
        assertEquals(90.0, mpsToKmh(25.0), 1e-9)
    }

    @Test
    fun speedConversionRejectsNonUsableInput() {
        // `Location` 用 -1 表示"本次定位没有速度"；NaN/Inf 来自上游脏数据。
        assertEquals(0.0, mpsToKmh(-1.0), 0.0)
        assertEquals(0.0, mpsToKmh(Double.NaN), 0.0)
        assertEquals(0.0, mpsToKmh(Double.POSITIVE_INFINITY), 0.0)
        assertEquals(0.0, mpsToKmh(Double.NEGATIVE_INFINITY), 0.0)
    }

    @Test
    fun identicalPointsHaveZeroDistance() {
        assertEquals(0.0, haversineMeters(39.9, 116.4, 39.9, 116.4), 1e-9)
    }

    @Test
    fun oneThousandthDegreeMatchesGreatCircleArc() {
        val expected = 111.19493
        assertEquals(expected, haversineMeters(39.900, 116.4, 39.901, 116.4), 0.01)
        // 赤道上 0.001° 经度弧长与 0.001° 纬度弧长相同。
        assertEquals(expected, haversineMeters(0.0, 0.0, 0.0, 0.001), 0.01)
    }

    @Test
    fun longitudeArcShrinksWithLatitude() {
        val atEquator = haversineMeters(0.0, 0.0, 0.0, 0.001)
        val atSixty = haversineMeters(60.0, 0.0, 60.0, 0.001)
        assertEquals(atEquator / 2.0, atSixty, 0.05)
    }

    @Test
    fun invalidCoordinatesYieldZeroInsteadOfGarbage() {
        assertEquals(0.0, haversineMeters(Double.NaN, 116.4, 39.9, 116.4), 0.0)
        assertEquals(0.0, haversineMeters(39.9, 116.4, 39.9, Double.POSITIVE_INFINITY), 0.0)
        // 纬度越界（>90）说明数据已经坏了，不能拿去算距离。
        assertEquals(0.0, haversineMeters(91.0, 0.0, 0.0, 0.0), 0.0)
        assertEquals(0.0, haversineMeters(0.0, 0.0, -90.5, 0.0), 0.0)
    }

    @Test
    fun normalDrivingSegmentIsAccumulated() {
        assertTrue(shouldAccumulateSegment(meters = 20.0, dtSeconds = 1.0, accuracyMeters = 5f))
    }

    @Test
    fun jitterGateRejectsStaticNoise() {
        // 静止时 GPS 抖几米是常态；不拦下来，停一晚上里程表能涨好几公里。
        assertFalse(shouldAccumulateSegment(meters = 5.0, dtSeconds = 1.0, accuracyMeters = 5f))
        // 门限本身是闭区间：恰好等于 8 m 通过。
        assertTrue(shouldAccumulateSegment(meters = 8.0, dtSeconds = 1.0, accuracyMeters = 5f))
    }

    @Test
    fun timeGateRejectsNonPositiveDelta() {
        assertFalse(shouldAccumulateSegment(meters = 20.0, dtSeconds = 0.0, accuracyMeters = 5f))
        assertFalse(shouldAccumulateSegment(meters = 20.0, dtSeconds = -1.0, accuracyMeters = 5f))
        assertFalse(shouldAccumulateSegment(meters = 20.0, dtSeconds = Double.NaN, accuracyMeters = 5f))
    }

    @Test
    fun accuracyGateRejectsPoorFixes() {
        assertFalse(shouldAccumulateSegment(meters = 20.0, dtSeconds = 1.0, accuracyMeters = 60f))
        assertTrue(shouldAccumulateSegment(meters = 20.0, dtSeconds = 1.0, accuracyMeters = 50f))
    }

    @Test
    fun unknownAccuracyDoesNotBlockAccumulation() {
        assertTrue(shouldAccumulateSegment(meters = 20.0, dtSeconds = 1.0, accuracyMeters = 0f))
    }

    @Test
    fun nonFiniteDistanceIsRejected() {
        assertFalse(shouldAccumulateSegment(meters = Double.NaN, dtSeconds = 1.0, accuracyMeters = 5f))
        assertFalse(shouldAccumulateSegment(meters = Double.POSITIVE_INFINITY, dtSeconds = 1.0, accuracyMeters = 5f))
    }

    @Test
    fun averageSpeedIntegratesDistanceOverTime() {
        // 1000 m / 100 s = 10 m/s = 36 km/h
        assertEquals(36.0, tripAverageSpeedKmh(1000.0, 100.0), 1e-9)
    }

    @Test
    fun averageSpeedIsZeroBeforeAnyElapsedTime() {
        // 刚进页面时 seconds 可能是 0：不能除出 Inf。
        assertEquals(0.0, tripAverageSpeedKmh(1000.0, 0.0), 0.0)
        assertEquals(0.0, tripAverageSpeedKmh(1000.0, -3.0), 0.0)
        assertEquals(0.0, tripAverageSpeedKmh(1000.0, Double.NaN), 0.0)
        assertEquals(0.0, tripAverageSpeedKmh(Double.NaN, 100.0), 0.0)
    }

    @Test
    fun durationUsesMmSsBelowOneHour() {
        assertEquals("00:00", formatTripDuration(0.0))
        assertEquals("00:05", formatTripDuration(5.0))
        // 向下取整（不是四舍五入）：显示"已行驶多久"时宁可少报一秒。
        assertEquals("01:05", formatTripDuration(65.9))
        assertEquals("59:59", formatTripDuration(3599.0))
    }

    @Test
    fun durationGrowsAnHourFieldAtOneHour() {
        assertEquals("1:00:00", formatTripDuration(3600.0))
        assertEquals("1:02:05", formatTripDuration(3725.0))
        assertEquals("23:59:59", formatTripDuration(86399.0))
    }

    @Test
    fun negativeOrInvalidDurationClampsToZero() {
        assertEquals("00:00", formatTripDuration(-5.0))
        assertEquals("00:00", formatTripDuration(Double.NaN))
        assertEquals("00:00", formatTripDuration(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun clockIsZeroPadded24Hour() {
        assertEquals("09:41", formatClock(9, 41))
        assertEquals("00:00", formatClock(0, 0))
        assertEquals("23:59", formatClock(23, 59))
    }

    @Test
    fun outOfRangeClockWrapsInsteadOfCrashing() {
        assertEquals("01:10", formatClock(25, 70))
        assertEquals("23:59", formatClock(-1, -1))
    }

    @Test
    fun missingFixRendersAsDashes() {
        assertEquals("--", formatSpeedKmh(null))
        assertEquals("--", formatSpeedKmh(Double.NaN))
        assertEquals("--", formatSpeedKmh(Double.POSITIVE_INFINITY))
        assertEquals("--", formatSpeedKmh(-1.0))
    }

    @Test
    fun speedIsRoundedToWholeKmh() {
        assertEquals("0", formatSpeedKmh(0.0))
        assertEquals("88", formatSpeedKmh(88.4))
        assertEquals("89", formatSpeedKmh(88.5))
    }

    @Test
    fun tripDistanceIsTwoDecimalKm() {
        // 两位小数是这一条的重点：一位小数会把 1.24 和 1.19 显示成同一个数。
        assertEquals("1.24 km", formatTripDistanceKm(1_240.0))
        assertEquals("0.00 km", formatTripDistanceKm(0.0))
        assertEquals("12.35 km", formatTripDistanceKm(12_345.0))
    }

    @Test
    fun invalidTripDistanceClampsToZero() {
        // 仪表位置上宁可显示 0.00 km，也不能出现 NaN / Infinity。
        assertEquals("0.00 km", formatTripDistanceKm(Double.NaN))
        assertEquals("0.00 km", formatTripDistanceKm(Double.POSITIVE_INFINITY))
        assertEquals("0.00 km", formatTripDistanceKm(-5.0))
    }
}
