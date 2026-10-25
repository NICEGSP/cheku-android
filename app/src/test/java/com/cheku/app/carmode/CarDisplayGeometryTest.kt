package com.cheku.app.carmode

import android.hardware.SensorManager
import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CarDisplayGeometryTest {
    @Test
    fun angleLerpTakesTheShortWayAcrossZero() {
        val half = lerpAngleDegrees(359f, 1f, 0.5f)
        assertTrue("359→1 只差 2°，半步应落在 0/360 附近而不是 180°，实际=$half", abs(half - 360f) < 0.001f)
        assertEquals(0f, normalizeDegrees(half), 0.001f)
        val back = lerpAngleDegrees(1f, 359f, 0.5f)
        assertEquals(0f, normalizeDegrees(back), 0.001f)
    }

    @Test
    fun angleLerpMovesTowardTheTarget() {
        assertEquals(45f, lerpAngleDegrees(0f, 90f, 0.5f), 0.001f)
        // 权重 0 = 完全不动（用来验证"静止时不碎抖"的极端）
        assertEquals(30f, lerpAngleDegrees(30f, 200f, 0f), 0.001f)
    }

    @Test
    fun normalizeDegreesWrapsAndSurvivesBadInput() {
        assertEquals(350f, normalizeDegrees(-10f), 0.001f)
        assertEquals(10f, normalizeDegrees(370f), 0.001f)
        assertEquals(0f, normalizeDegrees(360f), 0.001f)
        assertEquals(0f, normalizeDegrees(Float.NaN), 0.001f)
        assertEquals(0f, normalizeDegrees(Float.POSITIVE_INFINITY), 0.001f)
    }

    @Test
    fun emptyOrSinglePointDoesNotFakeALine() {
        assertEquals(0, projectTrackRoute(emptyList(), 200f, 200f, 20f).size)
        val single = projectTrackRoute(listOf(CarTrackPoint(31.2, 121.5)), 200f, 200f, 20f)
        assertEquals(1, single.size)
        assertEquals(100f, single.first().x, 0.001f)
        assertEquals(100f, single.first().y, 0.001f)
    }

    @Test
    fun tinyMovementCollapsesToASingleCenterPoint() {
        val jitter = listOf(
            CarTrackPoint(31.20000, 121.50000),
            CarTrackPoint(31.20005, 121.50005)
        )
        assertEquals(1, projectTrackRoute(jitter, 200f, 200f, 20f).size)
    }

    @Test
    fun projectionKeepsAspectRatioInsteadOfStretchingToFill() {
        val square = listOf(
            CarTrackPoint(0.0, 0.0),
            CarTrackPoint(0.0, 0.001),
            CarTrackPoint(0.001, 0.001)
        )
        // 画布刻意做成扁的：若两轴各自拉满，宽高比会跟着画布变。
        val projected = projectTrackRoute(square, 400f, 120f, 10f)
        assertEquals(3, projected.size)
        val width = projected.maxOf { it.x } - projected.minOf { it.x }
        val height = projected.maxOf { it.y } - projected.minOf { it.y }
        assertEquals("正方形轨迹在任何画布上都该是正方形", width, height, 0.5f)
    }

    @Test
    fun northIsUpAndWestIsLeft() {
        val track = listOf(
            CarTrackPoint(0.0, 0.0),        // 起点：南、西
            CarTrackPoint(0.001, 0.001)     // 终点：北、东
        )
        val projected = projectTrackRoute(track, 200f, 200f, 20f)
        val start = projected.first()
        val end = projected.last()
        assertEquals(20f, start.x, 0.001f)
        assertEquals(180f, start.y, 0.001f)
        assertEquals(180f, end.x, 0.001f)
        assertEquals(20f, end.y, 0.001f)
    }

    @Test
    fun straightNorthTrackIsCenteredHorizontally() {
        val track = listOf(CarTrackPoint(0.0, 0.5), CarTrackPoint(0.001, 0.5))
        val projected = projectTrackRoute(track, 200f, 200f, 20f)
        projected.forEach { assertEquals("竖直轨迹应水平居中", 100f, it.x, 0.001f) }
        assertEquals(180f, projected.first().y, 0.001f)
        assertEquals(20f, projected.last().y, 0.001f)
    }

    @Test
    fun illegalPointsAreDroppedInsteadOfBreakingTheCanvas() {
        val mixed = listOf(
            CarTrackPoint(Double.NaN, 121.5),
            CarTrackPoint(31.2, 121.5),
            CarTrackPoint(95.0, 121.5),      // 纬度越界
            CarTrackPoint(31.201, 121.501)
        )
        val projected = projectTrackRoute(mixed, 200f, 200f, 20f)
        assertEquals("非法点被丢掉后只剩两个合法点", 2, projected.size)
        projected.forEach {
            assertTrue("所有点都要落在留白之内", it.x in 20f..180f && it.y in 20f..180f)
        }
    }

    @Test
    fun displayRotationMapsSensorAxesLikeTheOfficialTable() {
        assertEquals(
            "竖屏（自然方向）：直接就是设备的 X/Y 轴",
            SensorManager.AXIS_X to SensorManager.AXIS_Y,
            displayRemapAxes(Surface.ROTATION_0)
        )
        assertEquals(
            "逆时针 90°（横屏一侧）：屏幕上方 = 设备的 −X",
            SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X,
            displayRemapAxes(Surface.ROTATION_90)
        )
        assertEquals(
            "倒过来 180°：屏幕上方 = 设备的 −Y",
            SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y,
            displayRemapAxes(Surface.ROTATION_180)
        )
        assertEquals(
            "逆时针 270°（横屏另一侧）：屏幕上方 = 设备的 +X",
            SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X,
            displayRemapAxes(Surface.ROTATION_270)
        )
        // 未知取值（个别 ROM/虚拟屏会给 −1）必须退化成"不换"，绝不能抛异常。
        assertEquals(
            SensorManager.AXIS_X to SensorManager.AXIS_Y,
            displayRemapAxes(-1)
        )
    }
}
