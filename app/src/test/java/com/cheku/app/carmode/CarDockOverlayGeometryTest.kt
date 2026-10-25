package com.cheku.app.carmode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarDockOverlayGeometryTest {
    private val density = 3.75f

    /** 本机横屏整屏 */
    private val fullWidth = 3200
    private val fullHeight = 1440

    private fun dp(value: Float): Int = (value * density).toInt()

    @Test
    fun fullScreenLandscapeRailSitsAtScreenLeftEdge() {
        val g = carDockOverlayGeometry(
            landscape = true,
            expanded = true,
            inMultiWindow = false,
            windowLeft = 0,
            windowTop = 0,
            windowWidth = fullWidth,
            windowHeight = fullHeight,
            density = density
        )
        assertEquals("横屏竖条必须贴屏幕最左（x=0）", 0, g.x)
        assertEquals("顶边与窗口齐平", 0, g.y)
        assertEquals("常态宽度 = CarDockWidth", dp(CarDockWidth.value), g.width)
        assertEquals("占满窗口高度", fullHeight, g.height)
    }

    @Test
    fun fullScreenPortraitBarSitsAtWindowBottom() {
        val g = carDockOverlayGeometry(
            landscape = false,
            expanded = true,
            inMultiWindow = false,
            windowLeft = 0,
            windowTop = 0,
            windowWidth = 1440,
            windowHeight = fullWidth,
            density = density
        )
        assertEquals("竖屏横条占满窗口宽度", 1440, g.width)
        assertEquals("贴窗口底边（= 窗口底 - CarDockHeight）", fullWidth - dp(CarDockHeight.value), g.y)
        assertEquals("高度 = CarDockHeight", dp(CarDockHeight.value), g.height)
    }

    @Test
    fun splitScreenRailFollowsTheCarWindowNotTheScreen() {
        val half = fullWidth / 2
        val g = carDockOverlayGeometry(
            landscape = true,
            expanded = true,
            inMultiWindow = true,
            windowLeft = half,
            windowTop = 0,
            windowWidth = half,
            windowHeight = fullHeight,
            density = density
        )
        assertEquals("分屏在右半屏时，dock 必须贴本窗口左边缘", half, g.x)
        assertEquals("宽度用分屏档（88dp）", dp(CarDockCompactWidth.value), g.width)
        assertEquals("高度仍是整块窗口高", fullHeight, g.height)
        assertTrue("绝不能压在左半屏那个 App 上（x 必须 > 0）", g.x > 0)
    }

    @Test
    fun splitScreenPortraitBarFollowsTheCarWindowBottom() {
        val top = 720
        val height = fullHeight - top
        val g = carDockOverlayGeometry(
            landscape = false,
            expanded = true,
            inMultiWindow = true,
            windowLeft = 0,
            windowTop = top,
            windowWidth = fullWidth,
            windowHeight = height,
            density = density
        )
        assertEquals("贴本窗口底边，不是屏幕底边", top + height - dp(CarDockHeight.value), g.y)
    }

    @Test
    fun collapsedRailKeepsLeftEdgeAndShrinksToHandle() {
        val g = carDockOverlayGeometry(
            landscape = true,
            expanded = false,
            inMultiWindow = false,
            windowLeft = 1600,
            windowTop = 0,
            windowWidth = 1600,
            windowHeight = fullHeight,
            density = density
        )
        assertEquals("收起时左边缘不动（贴边）", 1600, g.x)
        assertEquals("只留一条把手宽", dp(CarDockCollapsedWidth.value), g.width)
        assertEquals("高度仍占满（横屏收的是宽）", fullHeight, g.height)
    }

    @Test
    fun collapsedPortraitBarHugsBottomAndShrinksToHandle() {
        val g = carDockOverlayGeometry(
            landscape = false,
            expanded = false,
            inMultiWindow = false,
            windowLeft = 0,
            windowTop = 0,
            windowWidth = 1440,
            windowHeight = fullWidth,
            density = density
        )
        assertEquals("贴窗口底边", fullWidth - dp(CarDockCollapsedWidth.value), g.y)
        assertEquals("只留一条把手高", dp(CarDockCollapsedWidth.value), g.height)
    }

    @Test
    fun railNeverExceedsItsWindowWidth() {
        val g = carDockOverlayGeometry(
            landscape = true,
            expanded = true,
            inMultiWindow = true,
            windowLeft = 0,
            windowTop = 0,
            windowWidth = dp(70f),
            windowHeight = fullHeight,
            density = density
        )
        assertEquals("窗口比 dock 还窄时，dock 不得溢出窗口", dp(70f), g.width)
    }

    @Test
    fun portraitBarNeverExceedsItsWindowHeight() {
        val g = carDockOverlayGeometry(
            landscape = false,
            expanded = true,
            inMultiWindow = true,
            windowLeft = 0,
            windowTop = 0,
            windowWidth = fullWidth,
            windowHeight = dp(40f),
            density = density
        )
        assertEquals("窗口比 dock 还矮时，不得从窗口顶部冒出去", 0, g.y)
        assertTrue("高度也不得超过窗口", g.height <= dp(40f))
    }

    @Test
    fun widthsMatchTheInPageDockConstants() {
        assertEquals(
            "分屏宽度必须与页内 dock 的 CarDockCompactWidth 同值",
            dp(CarDockCompactWidth.value),
            carDockOverlayGeometry(true, true, true, 0, 0, fullWidth, fullHeight, density).width
        )
        assertEquals(
            "常态宽度必须与页内 dock 的 CarDockWidth 同值",
            dp(CarDockWidth.value),
            carDockOverlayGeometry(true, true, false, 0, 0, fullWidth, fullHeight, density).width
        )
        assertEquals(
            "竖屏高度必须与页内 dock 的 CarDockHeight 同值",
            dp(CarDockHeight.value),
            carDockOverlayGeometry(false, true, false, 0, 0, 1440, fullWidth, density).height
        )
    }
}

class CarThemeModeKeyTest {
    @Test
    fun knownKeysResolve() {
        assertEquals(CarThemeMode.Light, carThemeModeFromKey("light"))
        assertEquals(CarThemeMode.Dark, carThemeModeFromKey("dark"))
        assertEquals(CarThemeMode.System, carThemeModeFromKey("system"))
    }

    @Test
    fun unknownValuesFallBackToSystem() {
        assertEquals(CarThemeMode.System, carThemeModeFromKey(null))
        assertEquals(CarThemeMode.System, carThemeModeFromKey(""))
        assertEquals(CarThemeMode.System, carThemeModeFromKey("dark-mode"))
    }
}
