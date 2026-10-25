package com.cheku.app.carmode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarModeSettingsTest {
    @Test
    fun knownKeysRoundTripToTheirEnum() {
        CarModeOrientation.entries.forEach { orientation ->
            assertEquals(
                "key=${orientation.key} 必须能解析回自己",
                orientation,
                carModeOrientationFromKey(orientation.key)
            )
        }
    }

    @Test
    fun missingOrBlankKeyFallsBackToAuto() {
        assertEquals(CarModeOrientation.Auto, carModeOrientationFromKey(null))
        assertEquals(CarModeOrientation.Auto, carModeOrientationFromKey(""))
        assertEquals(CarModeOrientation.Auto, carModeOrientationFromKey("   "))
    }

    @Test
    fun unknownOrCorruptedKeyFallsBackToAutoInsteadOfThrowing() {
        assertEquals(CarModeOrientation.Auto, carModeOrientationFromKey("sensor"))
        assertEquals(CarModeOrientation.Auto, carModeOrientationFromKey("0"))
        assertEquals(CarModeOrientation.Auto, carModeOrientationFromKey("null"))
        // 大小写敏感：`Landscape` 不是合法 key（写错就该当未知处理，而不是"顺便也认"）。
        assertEquals(CarModeOrientation.Auto, carModeOrientationFromKey("Landscape"))
    }

    /** key 唯一性契约 */
    @Test
    fun orientationKeysAreUnique() {
        val keys = CarModeOrientation.entries.map { it.key }
        assertEquals("方向 key 不允许重复", keys.size, keys.toSet().size)
    }

    /** 标签唯一性 */
    @Test
    fun orientationLabelsAreUniqueAndNonBlank() {
        val labels = CarModeOrientation.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
        assertTrue(labels.none { it.isBlank() })
    }

    @Test
    fun minimumTripThresholdStaysInSaneRange() {
        val threshold = CarModeSettings.MIN_TRIP_KM_FOR_RECORD
        assertTrue("门限必须为正，否则 GPS 抖动也会写库", threshold > 0.0)
        assertTrue("门限超过 1km 会让短途行程永远记不上", threshold < 1.0)
    }
}
