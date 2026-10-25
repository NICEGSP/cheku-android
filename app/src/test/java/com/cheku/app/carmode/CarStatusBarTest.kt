package com.cheku.app.carmode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 顶部状态栏里纯换算函数的单测 */
class CarStatusBarTest {
    @Test
    fun batteryPercentScalesToHundred() {
        assertEquals(50, batteryLevelToPercent(50, 100))
        assertEquals(85, batteryLevelToPercent(85, 100))
        // 非百进制的 scale（部分 ROM 用 255）。
        assertEquals(100, batteryLevelToPercent(255, 255))
        assertEquals(50, batteryLevelToPercent(128, 256))
    }

    @Test
    fun batteryPercentRejectsUnknownValues() {
        // `EXTRA_LEVEL`/`EXTRA_SCALE` 缺失时是 -1；scale<=0 会除零。
        assertNull(batteryLevelToPercent(-1, 100))
        assertNull(batteryLevelToPercent(50, 0))
        assertNull(batteryLevelToPercent(50, -1))
    }

    @Test
    fun batteryPercentIsClamped() {
        // 脏数据（level > scale）也不许溢出成 120%。
        assertEquals(100, batteryLevelToPercent(120, 100))
    }

    @Test
    fun wifiBarsAreMonotonicAndClamped() {
        assertEquals(4, wifiRssiToBars(-40))
        assertEquals(4, wifiRssiToBars(-55))
        assertEquals(3, wifiRssiToBars(-56))
        assertEquals(0, wifiRssiToBars(-90))
        assertEquals(0, wifiRssiToBars(-200))
    }

    @Test
    fun cellularBarsAreMonotonicAndClamped() {
        assertEquals(4, cellularDbmToBars(-80))
        assertEquals(4, cellularDbmToBars(-85))
        assertEquals(3, cellularDbmToBars(-86))
        assertEquals(0, cellularDbmToBars(-120))
        assertEquals(0, cellularDbmToBars(Int.MIN_VALUE))
    }

    @Test
    fun gpsBarsRequireBothSatellitesAndSignal() {
        assertEquals(0, gnssToBars(0, 40f))
        assertEquals(1, gnssToBars(6, 10f))
        assertEquals(2, gnssToBars(6, 22f))
        assertEquals(3, gnssToBars(6, 30f))
        assertEquals(4, gnssToBars(6, 40f))
    }

    @Test
    fun barCountMatchesSignalMax() {
        // UI 侧固定画 [CAR_SIGNAL_MAX_BARS] 格，换算结果的满分必须与它一致。
        assertEquals(CAR_SIGNAL_MAX_BARS, wifiRssiToBars(-40))
        assertEquals(CAR_SIGNAL_MAX_BARS, cellularDbmToBars(-80))
        assertEquals(CAR_SIGNAL_MAX_BARS, gnssToBars(8, 45f))
    }
}
