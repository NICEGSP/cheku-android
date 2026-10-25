package com.cheku.app.glass

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 背景模糊核心算法契约 */
class BackgroundBlurTest {
    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun radiusZeroIsNoOp() {
        val pixels = IntArray(16) { argb(255, it * 10, 0, 0) }
        val expected = pixels.copyOf()
        BackgroundBlur.blur(pixels, 4, 4, 0)
        assertArrayEquals(expected, pixels)
    }

    @Test
    fun blurSpreadsImpulseSymmetric() {
        val w = 9
        val h = 9
        val pixels = IntArray(w * h) { argb(255, 0, 0, 0) }
        pixels[4 * w + 4] = argb(255, 255, 255, 255)
        val before = pixels.sumOf { it and 0xFF }

        BackgroundBlur.blur(pixels, w, h, 2)

        assertTrue("中心的黑像素应被邻域拉亮", (pixels[4 * w + 4] and 0xFF) > 0)
        assertTrue("对角邻域也应被影响", (pixels[3 * w + 3] and 0xFF) > 0)
        assertEquals("水平方向应对称", pixels[3 * w + 4], pixels[5 * w + 4])
        assertEquals("垂直方向应对称", pixels[4 * w + 3], pixels[4 * w + 5])

        val after = pixels.sumOf { it and 0xFF }
        assertTrue(
            "盒式模糊（边缘复制）总亮度应守恒在 ±20%：before=$before after=$after",
            after > before * 0.8 && after < before * 1.2,
        )
    }

    /** 纯色图模糊后仍是纯色 */
    @Test
    fun flatImageStaysFlat() {
        val flat = argb(255, 120, 90, 60)
        val pixels = IntArray(25) { flat }
        // 半径大于尺寸：应被夹取而不是越界
        BackgroundBlur.blur(pixels, 5, 5, 6)
        pixels.forEach { assertEquals(flat, it) }
    }

    /** Alpha 通道同样被平均，且退化输入不崩 */
    @Test
    fun alphaPreservedAndDegenerateInputSafe() {
        val opaque = IntArray(36) { argb(255, 200, 100, 50) }
        BackgroundBlur.blur(opaque, 6, 6, 1)
        opaque.forEach { assertEquals(255, it ushr 24 and 0xFF) }

        BackgroundBlur.blur(IntArray(0), 0, 0, 4)
        BackgroundBlur.blur(IntArray(4), 2, 2, 100)
        BackgroundBlur.blur(IntArray(4), -1, 2, 1)
    }
}
