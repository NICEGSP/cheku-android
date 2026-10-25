package com.cheku.app.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 背景解码上限契约 */
class BackgroundDecodeTest {
    @Test
    fun sampleSizeKeepsDecodedWidthWithinLimit() {
        val max = BackgroundBlur.MaxDecodedWidth // 2048

        assertEquals("普通截图（1200）不需要降采样", 1, BackgroundBlur.sampleSizeFor(1200, max))
        assertEquals("刚好等于上限也不降", 1, BackgroundBlur.sampleSizeFor(max, max))
        assertEquals("4000px 照片应降到 2000（而不是原样 4000）", 2, BackgroundBlur.sampleSizeFor(4000, max))
        assertEquals("8000px 应降到 2000", 4, BackgroundBlur.sampleSizeFor(8000, max))

        // 通用不变式：降完之后一定能塞进上限；且再小一档就会超（即"最省的合法档位"）
        listOf(400, 1200, 2049, 3000, 4000, 6000, 12000).forEach { w ->
            val s = BackgroundBlur.sampleSizeFor(w, max)
            val decoded = w / s
            assertTrue("width=$w sample=$s decoded=$decoded 必须 ≤ $max", decoded <= max)
            if (s > 1) {
                assertTrue(
                    "width=$w 选大了：上一档 $s/2 其实也不超上限",
                    w / (s / 2) > max,
                )
            }
        }
    }

    /** 退化输入不崩 */
    @Test
    fun degenerateWidthIsSafe() {
        assertEquals(1, BackgroundBlur.sampleSizeFor(0))
        assertEquals(1, BackgroundBlur.sampleSizeFor(-100))
    }
}
