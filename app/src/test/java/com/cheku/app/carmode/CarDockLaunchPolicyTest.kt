package com.cheku.app.carmode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarDockLaunchPolicyTest {
    @Test
    fun knownKeysRoundTripToTheirEnum() {
        CarDockLaunchMode.entries.forEach { mode ->
            assertEquals(
                "key=${mode.key} 必须能解析回自己",
                mode,
                carDockLaunchModeFromKey(mode.key)
            )
        }
    }

    @Test
    fun missingOrBlankKeyFallsBackToAuto() {
        assertEquals(CarDockLaunchMode.Auto, carDockLaunchModeFromKey(null))
        assertEquals(CarDockLaunchMode.Auto, carDockLaunchModeFromKey(""))
        assertEquals(CarDockLaunchMode.Auto, carDockLaunchModeFromKey("   "))
    }

    @Test
    fun unknownOrCorruptedKeyFallsBackToAutoInsteadOfThrowing() {
        assertEquals(CarDockLaunchMode.Auto, carDockLaunchModeFromKey("floating"))
        assertEquals(CarDockLaunchMode.Auto, carDockLaunchModeFromKey("0"))
        assertEquals(CarDockLaunchMode.Auto, carDockLaunchModeFromKey("null"))
        // 大小写敏感：`Split` 不是合法 key（写错就该当未知处理，而不是"顺便也认"）。
        assertEquals(CarDockLaunchMode.Auto, carDockLaunchModeFromKey("Split"))
        assertEquals(CarDockLaunchMode.Auto, carDockLaunchModeFromKey("自动识别"))
    }

    /** key 唯一性 */
    @Test
    fun launchModeKeysAreUnique() {
        val keys = CarDockLaunchMode.entries.map { it.key }
        assertEquals("打开方式 key 不允许重复", keys.size, keys.toSet().size)
    }

    @Test
    fun launchModeLabelsAreUniqueAndNonBlank() {
        val labels = CarDockLaunchMode.entries.map { it.label }
        assertEquals("标签必须唯一（分段控件靠下标对齐）", labels.size, labels.toSet().size)
        assertTrue("标签不允许为空", labels.none { it.isBlank() })
    }

    /** 选项顺序即 UI 顺序 */
    @Test
    fun autoIsTheDefaultAndComesFirst() {
        assertEquals(CarDockLaunchMode.Auto, CarDockLaunchMode.entries.first())
        assertEquals(CarDockLaunchMode.Auto, carDockLaunchModeFromKey(null))
    }

    @Test
    fun explicitChoiceIsNeverOverridden() {
        val explicit = CarDockLaunchMode.entries.filter { it != CarDockLaunchMode.Auto }
        for (setting in explicit) {
            for (deviceLandscape in listOf(true, false)) {
                for (overlayAvailable in listOf(true, false)) {
                    val actual = decideDockLaunchMode(setting, deviceLandscape, overlayAvailable)
                    assertEquals(
                        "显式选择 $setting 被（deviceLandscape=$deviceLandscape, " +
                            "overlayAvailable=$overlayAvailable）改成了 $actual",
                        setting,
                        actual
                    )
                }
            }
        }
    }

    @Test
    fun autoOnLandscapeDeviceAlwaysGoesSplit() {
        for (overlayAvailable in listOf(true, false)) {
            assertEquals(
                "横屏设备上一律分屏（overlayAvailable=$overlayAvailable）",
                CarDockLaunchMode.Split,
                decideDockLaunchMode(CarDockLaunchMode.Auto, deviceLandscape = true, overlayAvailable = overlayAvailable)
            )
        }
    }

    @Test
    fun autoOnPortraitDeviceGoesOverlayWhenDockIsAvailable() {
        assertEquals(
            CarDockLaunchMode.Overlay,
            decideDockLaunchMode(CarDockLaunchMode.Auto, deviceLandscape = false, overlayAvailable = true)
        )
    }

    @Test
    fun autoFallsBackToSplitWhenDockIsNotAvailable() {
        assertEquals(
            CarDockLaunchMode.Split,
            decideDockLaunchMode(CarDockLaunchMode.Auto, deviceLandscape = false, overlayAvailable = false)
        )
    }

    /** 兜底不变量 */
    @Test
    fun autoNeverResolvesToItselfOrPlain() {
        val resolvable = setOf(CarDockLaunchMode.Split, CarDockLaunchMode.Overlay)
        for (deviceLandscape in listOf(true, false)) {
            for (overlayAvailable in listOf(true, false)) {
                val actual = decideDockLaunchMode(CarDockLaunchMode.Auto, deviceLandscape, overlayAvailable)
                assertTrue(
                    "Auto（deviceLandscape=$deviceLandscape, overlayAvailable=$overlayAvailable）" +
                        "解析成了 $actual，但它必须是 $resolvable 里的一个",
                    actual in resolvable
                )
                assertNotEquals(CarDockLaunchMode.Auto, actual)
                assertNotEquals(CarDockLaunchMode.Plain, actual)
            }
        }
    }
}
