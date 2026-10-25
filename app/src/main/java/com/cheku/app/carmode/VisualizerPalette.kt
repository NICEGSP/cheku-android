package com.cheku.app.carmode

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal enum class VisPaletteMode(val key: String, val label: String) {
    Theme("theme", "默认"),
    Artwork("artwork", "封面取色"),
    Custom("custom", "自定义");

    companion object {
        fun fromKey(k: String?): VisPaletteMode =
            entries.firstOrNull { it.key == k } ?: Theme
    }
}

internal data class VisPalette(val colors: List<Color>) {
    init {
        require(colors.isNotEmpty()) { "调色板至少要有一种颜色" }
    }

    /** 按位置 t取色 */
    fun at(t: Float): Color {
        val x = t.coerceIn(0f, 1f)
        if (colors.size == 1) return colors[0]
        val e = x * x * (3f - 2f * x)                  // smoothstep
        val seg = colors.size - 1
        val f = e * seg
        val i = f.toInt().coerceIn(0, seg - 1)
        return lerpColor(colors[i], colors[i + 1], f - i)
    }

    companion object {
        val Fallback = VisPalette(listOf(Color(0xFF4D9CFF), Color(0xFF3ECF8E), Color(0xFFF0A94C)))
    }
}

/** 两个颜色按 f线性插值 */
internal fun lerpColor(a: Color, b: Color, f: Float): Color {
    val x = f.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * x,
        green = a.green + (b.green - a.green) * x,
        blue = a.blue + (b.blue - a.blue) * x,
        alpha = a.alpha + (b.alpha - a.alpha) * x
    )
}

internal fun extractVisPalette(artwork: Bitmap): VisPalette {
    val size = 24
    val small = runCatching {
        Bitmap.createScaledBitmap(artwork, size, size, true)
    }.getOrNull() ?: return VisPalette.Fallback

    val hist = HashMap<Int, IntArray>()
    val px = IntArray(size * size)
    runCatching { small.getPixels(px, 0, size, 0, 0, size, size) }
    for (p in px) {
        val a = (p ushr 24) and 0xFF
        if (a < 128) continue                       // 半透明像素不算数
        val r = (p ushr 16) and 0xFF
        val g = (p ushr 8) and 0xFF
        val b = p and 0xFF
        val key = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
        val e = hist.getOrPut(key) { IntArray(4) }
        e[0]++; e[1] += r; e[2] += g; e[3] += b
    }
    if (hist.isEmpty()) return VisPalette.Fallback

    val candidates = hist.values
        .sortedByDescending { it[0] }
        .map { e ->
            val n = e[0].coerceAtLeast(1)
            Color(
                red = (e[1] / n) / 255f,
                green = (e[2] / n) / 255f,
                blue = (e[3] / n) / 255f
            )
        }

    val picked = ArrayList<Color>(3)
    for (c in candidates) {
        if (picked.size >= 3) break
        if (luminance(c) < 0.20f) continue                       // 不要黑
        if (picked.any { !isDistinct(it, c) }) continue           // 要"不同"
        picked.add(c)
    }

    var base = picked.firstOrNull() ?: Color(0xFF4D9CFF)
    if (luminance(base) < 0.30f) base = brighten(base, 0.42f)
    var i = 0
    while (picked.size < 3) {
        i++
        val shifted = rotateHue(base, 120f * i)
        val c = if (luminance(shifted) < 0.22f) brighten(shifted, 0.35f) else shifted
        if (picked.none { !isDistinct(it, c) }) picked.add(c)
        else picked.add(brighten(c, 0.16f * i))
        if (i > 6) break                                          // 保险
    }
    while (picked.size < 3) picked.add(brighten(picked.lastOrNull() ?: base, 0.2f))

    val sorted = picked.sortedBy { luminance(it) }
    return VisPalette(listOf(sorted[0], sorted[1], sorted[2]))
}

/** 感知亮度 */
private fun luminance(c: Color): Float =
    0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

/** 提亮 */
private fun brighten(c: Color, amount: Float): Color = Color(
    red = c.red + (1f - c.red) * amount,
    green = c.green + (1f - c.green) * amount,
    blue = c.blue + (1f - c.blue) * amount
)

/** 色相距离 */
private fun hueDistance(a: Color, b: Color): Float {
    val ha = hueOf(a); val hb = hueOf(b)
    val d = abs(ha - hb)
    return min(d, 360f - d)
}

private fun isDistinct(a: Color, b: Color): Boolean =
    hueDistance(a, b) >= 28f || abs(luminance(a) - luminance(b)) >= 0.12f

/** 取色相 */
private fun hueOf(c: Color): Float {
    val r = c.red; val g = c.green; val bl = c.blue
    val mx = max(r, max(g, bl)); val mn = min(r, min(g, bl))
    val d = mx - mn
    if (d < 1e-6f) return 0f
    val h = when (mx) {
        r -> ((g - bl) / d) % 6f
        g -> ((bl - r) / d) + 2f
        else -> ((r - g) / d) + 4f
    }
    return ((h * 60f) + 360f) % 360f
}

private fun rotateHue(c: Color, deg: Float): Color {
    val h = (hueOf(c) + deg) % 360f
    val mx = max(c.red, max(c.green, c.blue))
    val mn = min(c.red, min(c.green, c.blue))
    val v = mx
    val s = if (mx <= 0f) 0f else (mx - mn) / mx
    val hh = h / 60f
    val i = hh.toInt()
    val f = hh - i
    val p = v * (1f - s)
    val q = v * (1f - s * f)
    val t = v * (1f - s * (1f - f))
    val (r, g, b) = when (i % 6) {
        0 -> Triple(v, t, p)
        1 -> Triple(q, v, p)
        2 -> Triple(p, v, t)
        3 -> Triple(p, q, v)
        4 -> Triple(t, p, v)
        else -> Triple(v, p, q)
    }
    return Color(r, g, b)
}

internal fun customPalette(hues: List<Float>): VisPalette {
    val sats = listOf(0.72f, 0.66f, 0.62f)
    val vals = listOf(1.00f, 0.96f, 0.98f)
    return VisPalette(
        hues.take(3).mapIndexed { i, h ->
            hsv(h, sats.getOrElse(i) { 0.66f }, vals.getOrElse(i) { 0.96f })
        }
    )
}

internal fun hsv(hDeg: Float, s: Float, v: Float): Color {
    val h = ((hDeg % 360f) + 360f) % 360f / 60f
    val i = h.toInt()
    val f = h - i
    val p = v * (1f - s)
    val q = v * (1f - s * f)
    val t = v * (1f - s * (1f - f))
    val (r, g, b) = when (i % 6) {
        0 -> Triple(v, t, p)
        1 -> Triple(q, v, p)
        2 -> Triple(p, v, t)
        3 -> Triple(p, q, v)
        4 -> Triple(t, p, v)
        else -> Triple(v, p, q)
    }
    return Color(r, g, b)
}
