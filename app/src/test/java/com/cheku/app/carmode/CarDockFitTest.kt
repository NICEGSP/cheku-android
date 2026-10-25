package com.cheku.app.carmode

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

class CarDockFitTest {
    /** 页内 dock */
    @Test
    fun pageRailFitsAllSixLabelledItems() {
        val need = carDockRailHeight(6, CarDockMetrics.pageItemContent)
        assertTrue(
            "页内竖条需要 $need > ${CarDockMetrics.landscapeRailExtent}，会少一条",
            need <= CarDockMetrics.landscapeRailExtent,
        )
    }

    /** 浮层 dock */
    @Test
    fun overlayRailFitsSixItemsPlusCollapseBar() {
        val need = carDockRailHeight(6, CarDockMetrics.overlayItemContent) +
            CarDockMetrics.overlayCollapseBar
        assertTrue(
            "浮层竖条需要 $need > ${CarDockMetrics.landscapeRailExtent}，会少一条或盖住收起条",
            need <= CarDockMetrics.landscapeRailExtent,
        )
    }

    /** 竖屏 */
    @Test
    fun portraitBarsFitAllSixItems() {
        val page = carDockBarWidth(6)
        assertTrue(
            "竖屏页内横条需要 $page > ${CarDockMetrics.portraitBarExtent}，会少一格",
            page <= CarDockMetrics.portraitBarExtent,
        )
        val overlay = carDockBarWidth(6) + CarDockMetrics.overlayCollapseBarWidth
        assertTrue(
            "竖屏浮层横条需要 $overlay > ${CarDockMetrics.portraitBarExtent}",
            overlay <= CarDockMetrics.portraitBarExtent,
        )
    }

    /** 边界提醒 */
    @Test
    fun floatingRailHoldsSevenLabelledItemsAtMost() {
        val seven = carDockRailHeight(7, CarDockMetrics.overlayItemContent) +
            CarDockMetrics.overlayCollapseBar
        assertTrue("7 条带标签应当仍然放得下（需要 $seven）", seven <= CarDockMetrics.landscapeRailExtent)

        val eight = carDockRailHeight(8, CarDockMetrics.overlayItemContent) +
            CarDockMetrics.overlayCollapseBar
        assertTrue("第 8 条带标签时必须溢出（是 $eight）", eight > CarDockMetrics.landscapeRailExtent)

        val eightIconsOnly = carDockRailHeight(8, CarDockMetrics.iconOnlyContent) +
            CarDockMetrics.overlayCollapseBar
        assertTrue(
            "收掉标签后 8 条仍要全可见（是 $eightIconsOnly）",
            eightIconsOnly <= CarDockMetrics.landscapeRailExtent,
        )
    }

    @Test
    fun iconOnlyFallbackStillShowsEveryItem() {
        // 分屏横屏：主区高度只剩约 220dp（系统允许的最小分屏高度），此时按 26dp/格算。
        val compact = 220.dp
        val need = carDockRailHeight(6, CarDockMetrics.iconOnlyContent)
        assertTrue("纯图标 6 条需要 $need ≤ $compact（分屏可用高度）", need <= compact)
    }
}
