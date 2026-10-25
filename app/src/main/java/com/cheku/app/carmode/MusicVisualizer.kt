package com.cheku.app.carmode

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import android.media.audiofx.Visualizer
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

@Composable
internal fun MusicVisualizer(
    /** 此刻是否在播放 */
    playing: Boolean,
    /** 主色 */
    tint: Color,
    /** 副色 */
    accent: Color,
    /** 两端渐隐强度 */
    fade: Float = 0.70f,
    /** 跟随平滑 */
    smoothing: Float = 0.70f,
    /** 增益 */
    gain: Float = 1.30f,
    /** 整体透明度 */
    alpha: Float = 1f,
    /** 20 款形态之一 */
    style: VisualizerStyle = VisualizerStyle.CURVE,
    /** 三色渐变调色板 */
    palette: VisPalette? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val samples = rememberMixCurve(
        context, playing, SAMPLES, smoothing, PlaybackCapture.state
    )

    // 模拟动画的相位：只在没有真实数据时驱动。
    val transition = rememberInfiniteTransition(label = "music-visualizer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (playing) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "visualizer-phase"
    )
    val simPhase = phase
    val simA = remember(phase) { sin(phase * 2f * PI.toFloat()) }
    val simB = remember(phase) { sin(phase * 2f * PI.toFloat() * 1.42f + 1.1f) }
    val simC = remember(phase) { sin(phase * 2f * PI.toFloat() * 0.74f + 2.3f) }

    Canvas(
        modifier = modifier.semantics {
            contentDescription = when {
                !playing -> "${style.label}（已暂停）"
                samples != null -> "${style.label}（真实音频频谱）"
                else -> "${style.label}（模拟动画）"
            }
        }
    ) {
        if (size.minDimension <= 0f) return@Canvas
        // 整体透明度为 0 时直接不画：省掉整块绘制开销。
        if (alpha <= 0.01f) return@Canvas

        val raw = FloatArray(SAMPLES) { i ->
            val v = samples?.get(i)
                ?: simulatedEnergy(i / (SAMPLES - 1f), simA, simB, simC, playing)
            v * (gain * styleBaseGain(style))
        }
        // 3 点滑动平均（真实数据才做；模拟数据本身连续正弦，再平滑会变肉）。
        val amp = if (samples != null) {
            val out = FloatArray(SAMPLES)
            for (i in 0 until SAMPLES) out[i] = centerSmooth(raw, i)
            out
        } else {
            raw
        }
        val signedE = FloatArray(SAMPLES) { i ->
            ((amp[i].coerceIn(0f, 1f) - 0.42f) / 0.58f).coerceIn(-1f, 1f)
        }

        val f0 = (fade * styleBaseFade(style)).coerceIn(0f, 1f)
        val axisBrush = if (palette != null) {
            // 配色模式下，线身直接用三色渐变（这才是"三色渐变"的完整体现）。
            // 三色（或 1~2 色）按非线性分布铺到整条线上。
            Brush.horizontalGradient(
                0.00f to palette.at(0.00f).copy(alpha = alpha),
                0.25f to palette.at(0.25f).copy(alpha = alpha),
                0.50f to palette.at(0.50f).copy(alpha = alpha),
                0.75f to palette.at(0.75f).copy(alpha = alpha),
                1.00f to palette.at(1.00f).copy(alpha = alpha),
                startX = 0f, endX = size.width
            )
        } else {
            Brush.horizontalGradient(
                0f to accent.copy(alpha = alpha),
                0.5f to tint.copy(alpha = alpha),
                1f to accent.copy(alpha = alpha),
                startX = 0f, endX = size.width
            )
        }
        val stops = (0..32).map { i ->
            val x = i / 32f
            x to Color.White.copy(alpha = fadeVisibilityAt(x, f0))
        }
        val maskBrush = Brush.horizontalGradient(
            colorStops = stops.toTypedArray(),
            startX = 0f,
            endX = size.width
        )
        drawContext.canvas.saveLayer(
            androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height),
            Paint()
        )
        drawStyle(
            style = style, amp = amp, signedE = signedE,
            w = size.width, h = size.height, axis = axisBrush,
            tint = tint, accent = accent, alpha = alpha,
            stroke = 3.dp.toPx(), simPhase = simPhase, palette = palette
        )
        drawRect(
            brush = maskBrush,
            topLeft = Offset.Zero,
            size = size,
            blendMode = BlendMode.DstIn
        )
        drawContext.canvas.restore()
    }
}

private fun styleBaseGain(style: VisualizerStyle): Float = 1.0f

private fun styleBaseFade(style: VisualizerStyle): Float = 1.0f

private fun bandIdx(f: Float, bands: Int): Int =
    (bands * ln(f / 20f) / ln(600f)).toInt().coerceIn(0, bands - 1)

private fun bandMean(amp: FloatArray, f0: Float, f1: Float): Float {
    if (amp.isEmpty()) return 0f
    val a = bandIdx(f0, amp.size)
    val b = bandIdx(f1, amp.size).coerceAtLeast(a)
    var sum = 0f
    var cnt = 0
    for (i in a..b) { sum += amp[i]; cnt++ }
    return if (cnt > 0) sum / cnt else 0f
}

private fun rndAt(i: Float, k: Float = 12.9898f): Float {
    val v = sin(i * k) * 43758.5453f
    return v - kotlin.math.floor(v)
}

private fun hsl(hDeg: Float, sPct: Float, lPct: Float, a: Float = 1f): Color {
    val hh = (((hDeg % 360f) + 360f) % 360f) / 360f
    val ss = (sPct / 100f).coerceIn(0f, 1f)
    val ll = (lPct / 100f).coerceIn(0f, 1f)
    val q = if (ll < 0.5f) ll * (1f + ss) else ll + ss - ll * ss
    val pp = 2f * ll - q
    fun ch(t0: Float): Float {
        var t = t0
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> pp + (q - pp) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> pp + (q - pp) * (2f / 3f - t) * 6f
            else -> pp
        }
    }
    return Color(ch(hh + 1f / 3f), ch(hh), ch(hh - 1f / 3f), a.coerceIn(0f, 1f))
}

private fun fadeVisibilityAt(x: Float, fadeAmount: Float): Float {
    val f = fadeAmount.coerceIn(0f, 1f)
    val edge = 1f - f            // 两端的 alpha
    val near = edge + f * 0.35f  // 渐隐区内的 alpha
    return when {
        x <= 0f -> edge
        x < 0.14f -> edge + (near - edge) * (x / 0.14f)
        x < 0.50f -> near + (1f - near) * ((x - 0.14f) / 0.36f)
        x < 0.86f -> 1f - (1f - near) * ((x - 0.50f) / 0.36f)
        x < 1f -> near + (edge - near) * ((x - 0.86f) / 0.14f)
        else -> edge
    }
}

/** 把一份逐频段能量按所选样式画出来 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStyle(
    style: VisualizerStyle,
    amp: FloatArray,
    signedE: FloatArray,
    w: Float,
    h: Float,
    axis: Brush,
    tint: Color,
    accent: Color,
    alpha: Float,
    stroke: Float,
    simPhase: Float,
    /** 三色渐变调色板 */
    palette: VisPalette?
) {
    val n = amp.size
    val mid = h / 2f
    /** 取色 */
    fun col(t: Float, fallback: Color, a: Float = 1f): Color =
        (palette?.at(t.coerceIn(0f, 1f)) ?: fallback).copy(alpha = a)

    fun pal(a: Float, fallback: Color): Color = palette?.at(a.coerceIn(0f, 1f)) ?: fallback
    fun specAt(t: Float): Float =
        amp[(t * (n - 1)).toInt().coerceIn(0, n - 1)].coerceIn(0f, 1f)

    val bLow = bandMean(amp, 20f, 250f)
    val bMid = bandMean(amp, 250f, 2000f)
    val bHigh = bandMean(amp, 2000f, 12000f)

    val ph = (System.currentTimeMillis() % 3_600_000L) / 1000f

    // 常用描边宽度（各款照预览页各自的 lineWidth）
    fun sw(dp: Float) = dp.dp.toPx()

    when (style) {
        VisualizerStyle.CURVE -> {
            val ampY = h * 0.40f
            val pth = Path()
            val N = 96
            for (i in 0..N) {
                val t = i / N.toFloat()
                val e = specAt(t)
                val xx = ((e - 0.32f) / 0.48f).coerceIn(-1f, 1f)
                val shaped = Math.signum(xx) *
                    Math.pow(kotlin.math.abs(xx).toDouble(), 1.5).toFloat()
                val y = mid - shaped * ampY
                if (i == 0) pth.moveTo(0f, y) else pth.lineTo(w * t, y)
            }
            drawPath(
                pth, axis,
                style = Stroke(width = sw(2.6f), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawLine(
                pal(0.5f, Color.White).copy(alpha = 0.08f * alpha),
                Offset(0f, mid), Offset(w, mid), sw(1f)
            )
        }

        VisualizerStyle.MIRROR_BARS -> {
            val N = 60
            val gap = w / N
            val bw = maxOf(sw(2f), gap * 0.5f)
            for (i in 0 until N) {
                val t = i / N.toFloat()
                val e = specAt(t)
                val len = (e * 1.35f).coerceIn(0f, 1.3f) * h * 0.40f
                val x = gap * i + (gap - bw) / 2f
                drawRoundRect(
                    color = col(t, hsl(200f + t * 60f, 80f, 48f + e * 20f), alpha),
                    topLeft = Offset(x, mid - len),
                    size = Size(bw, len * 2f),
                    cornerRadius = CornerRadius(bw / 2f, bw / 2f)
                )
            }
        }

        VisualizerStyle.SYMMETRIC_WAVE -> {
            val ampY = h * 0.42f
            val pth = Path()
            val N = 100
            for (i in 0..N) {
                val t = i / N.toFloat()
                val e = (bLow * sin(t * 6.3f) +
                    bMid * sin(t * 11f + 1f) +
                    bHigh * sin(t * 17f + 2f)) * 1.5f
                val y = mid - e * ampY
                if (i == 0) pth.moveTo(0f, y) else pth.lineTo(w * t, y)
            }
            drawPath(
                pth, col(0.5f, Color(0xFF4D9CFF), 0.95f * alpha),
                style = Stroke(width = sw(2.4f), join = StrokeJoin.Round)
            )
            drawLine(
                pal(0.5f, Color(0xFF4D9CFF)).copy(alpha = 0.15f * alpha),
                Offset(0f, mid), Offset(w, mid), sw(1f)
            )
        }

        VisualizerStyle.BARS -> {
            val N = 48
            val gap = w / N
            val bw = gap * 0.68f
            for (i in 0 until N) {
                val t = i / N.toFloat()
                val e = specAt(t)
                val len = (e * 1.3f).coerceIn(0f, 1f) * h * 0.82f
                drawRoundRect(
                    color = col(t, hsl(195f + t * 65f, 78f, 48f + e * 20f), alpha),
                    topLeft = Offset(gap * i + (gap - bw) / 2f, h - len - sw(8f)),
                    size = Size(bw, len),
                    cornerRadius = CornerRadius(bw / 2f, bw / 2f)
                )
            }
        }

        VisualizerStyle.RADIAL_BARS -> {
            val cx = w / 2f; val cy = h / 2f
            val R = minOf(w, h) * 0.33f
            for (i in 0 until 72) {
                val t = i / 72f
                val a = t * 2f * PI.toFloat() - PI.toFloat() / 2f
                val e = bLow * (1f - t) * 1.5f + bMid * sin(t * 9f + 1f) + bHigh * sin(t * 21f + 2f)
                val len = sw(5f) + e.coerceIn(0f, 1.3f) * h * 0.28f
                drawLine(
                    color = col(t, hsl(205f + t * 55f, 82f, 52f + e * 16f), alpha),
                    start = Offset(cx + cos(a) * R, cy + sin(a) * R),
                    end = Offset(cx + cos(a) * (R + len), cy + sin(a) * (R + len)),
                    strokeWidth = sw(3f), cap = StrokeCap.Round
                )
            }
        }

        VisualizerStyle.SMOOTH_WAVE -> {
            val ampY = h * 0.38f
            val pth = Path()
            val N = 110
            for (i in 0..N) {
                val t = i / N.toFloat()
                val e = sin(t * 13f + ph * 11.1f) * bLow * 0.8f +
                    sin(t * 27f + 2f) * bHigh * 0.9f +
                    sin(t * 5f) * bMid * 0.5f
                val y = mid - e * ampY
                if (i == 0) pth.moveTo(0f, y) else pth.lineTo(w * t, y)
            }
            drawPath(
                pth, col(0.5f, Color(0xFF3ECF8E), 0.92f * alpha),
                style = Stroke(width = sw(2.2f), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        VisualizerStyle.FILL_WAVE -> {
            val ampY = h * 0.38f
            val pth = Path()
            val N = 100
            val xs = ArrayList<Pair<Float, Float>>(N + 1)
            for (i in 0..N) {
                val t = i / N.toFloat()
                val e = sin(t * 11f + ph * 10f) * bLow +
                    sin(t * 23f + 2f) * bHigh * 0.85f +
                    sin(t * 6f) * bMid * 0.55f
                xs.add((w * t) to (mid - e * ampY))
                if (i == 0) pth.moveTo(0f, mid - e * ampY) else pth.lineTo(w * t, mid - e * ampY)
            }
            // 填充：从曲线闭合到中线
            val fill = Path().apply {
                moveTo(0f, mid)
                xs.forEach { lineTo(it.first, it.second) }
                lineTo(w, mid)
                close()
            }
            val fillBrush = if (palette != null) {
                Brush.horizontalGradient(
                    listOf(
                        palette.at(0f).copy(alpha = 0.42f * alpha),
                        palette.at(0.5f).copy(alpha = 0.34f * alpha),
                        palette.at(1f).copy(alpha = 0.44f * alpha)
                    ),
                    startX = 0f, endX = w
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF4D9CFF).copy(alpha = 0.55f * alpha),
                        Color(0xFF4D9CFF).copy(alpha = 0f)
                    ),
                    startY = mid - ampY, endY = mid + ampY
                )
            }
            drawPath(fill, fillBrush)
            drawPath(
                pth, col(0.5f, Color(0xFF4D9CFF), 0.9f * alpha),
                style = Stroke(width = sw(2f))
            )
        }

        VisualizerStyle.MIRROR_SPECTRUM -> {
            val N = 26
            val gap = (w / 2f) / N
            for (side in 0..1) {
                for (i in 0 until N) {
                    val t = i / N.toFloat()
                    val e = specAt(t)
                    val len = (e * 1.3f).coerceIn(0f, 1f) * h * 0.40f
                    val bw = gap * 0.66f
                    val x = if (side == 1) midX(w) + gap * i + (gap - bw) / 2f
                    else midX(w) - gap * (i + 1) + (gap - bw) / 2f
                    drawRoundRect(
                        color = col(t, hsl(190f + t * 70f, 80f, 48f + e * 20f), alpha),
                        topLeft = Offset(x, h / 2f - len),
                        size = Size(bw, len * 2f),
                        cornerRadius = CornerRadius(bw / 2f, bw / 2f)
                    )
                }
            }
        }

        VisualizerStyle.BLOCKS -> {
            val N = 56
            val gap = w / N
            val bw = gap * 0.6f
            for (i in 0 until N) {
                val rnd = rndAt(i.toFloat())
                val e = (bLow * 0.5f + bMid * 0.35f + bHigh * 0.25f) * (0.45f + rnd * 0.85f)
                val len = e.coerceIn(0.03f, 1f) * h * 0.40f
                drawRoundRect(
                    color = col(rnd, Color(0xFFF0A94C), (0.35f + rnd * 0.5f) * alpha),
                    topLeft = Offset(gap * i + (gap - bw) / 2f, h / 2f - len),
                    size = Size(bw, len * 2f),
                    cornerRadius = CornerRadius(sw(2f), sw(2f))
                )
            }
        }

        VisualizerStyle.DOTS -> {
            val N = 40
            val gap = w / N
            for (i in 0 until N) {
                val t = i / N.toFloat()
                val e = specAt(t)
                val r = sw(1.2f) + (e * 1.3f).coerceIn(0f, 1f) * (h * 0.13f)
                val y = mid - (e.coerceIn(0f, 1f) - 0.4f) * h * 0.30f
                drawCircle(
                    color = col(t, Color(0xFF78DCFF), (0.30f + e * 0.65f) * alpha),
                    radius = r, center = Offset(gap * i + gap / 2f, y)
                )
            }
        }

        VisualizerStyle.RING_WAVE -> {
            val cx = w / 2f; val cy = h / 2f
            val R = minOf(w, h) * 0.34f
            val pth = Path()
            val N = 120
            for (i in 0..N) {
                val t = i / N.toFloat()
                val e = specAt(t)
                val a = t * 2f * PI.toFloat() - PI.toFloat() / 2f
                val rr = R + (e - 0.4f) * R * 0.75f
                val x = cx + cos(a) * rr; val y = cy + sin(a) * rr
                if (i == 0) pth.moveTo(x, y) else pth.lineTo(x, y)
            }
            pth.close()
            drawPath(
                pth, col(0.5f, Color(0xFF3ECF8E), 0.9f * alpha),
                style = Stroke(width = sw(2f), join = StrokeJoin.Round)
            )
        }

        VisualizerStyle.RING_DOTS -> {
            val cx = w / 2f; val cy = h / 2f
            val R = minOf(w, h) * 0.36f
            for (i in 0 until 90) {
                val t = i / 90f
                val e = specAt(t)
                val a = t * 2f * PI.toFloat() - PI.toFloat() / 2f
                val r = sw(1.4f) + (e * 1.3f).coerceIn(0f, 1f) * (minOf(w, h) * 0.13f)
                drawCircle(
                    color = col(t, hsl(195f + t * 70f, 82f, 52f + e * 16f), (0.35f + e * 0.6f) * alpha),
                    radius = r,
                    center = Offset(cx + cos(a) * R, cy + sin(a) * R)
                )
            }
        }

        VisualizerStyle.BLOB -> {
            val cx = w / 2f; val cy = h / 2f
            val base = minOf(w, h) * 0.24f
            val pth = Path()
            val N = 72
            for (i in 0..N) {
                val a = (i / N.toFloat()) * 2f * PI.toFloat()
                val wob = 1f +
                    bLow * 0.35f * sin(a * 3f + ph * 5f) +
                    bMid * 0.22f * sin(a * 5f - ph * 4f) +
                    bHigh * 0.14f * sin(a * 8f + ph * 7f)
                val rr = base * wob
                val x = cx + cos(a) * rr; val y = cy + sin(a) * rr
                if (i == 0) pth.moveTo(x, y) else pth.lineTo(x, y)
            }
            pth.close()
            drawPath(
                pth,
                Brush.radialGradient(
                    listOf(
                        pal(0.4f, Color(0xFF4D9CFF)).copy(alpha = 0.55f * alpha),
                        pal(0.9f, Color(0xFF785AFF)).copy(alpha = 0.12f * alpha)
                    ),
                    center = Offset(cx, cy), radius = base * 1.6f
                )
            )
            drawPath(
                pth, col(0.5f, Color(0xFF8CBEFF), 0.7f * alpha),
                style = Stroke(width = sw(2f))
            )
        }

        VisualizerStyle.BLAST -> {
            val cx = w / 2f; val cy = h / 2f
            val maxL = minOf(w, h) * 0.45f
            for (i in 0 until 54) {
                val rnd = rndAt(i.toFloat(), 78.233f)
                val a = (i / 54f) * 2f * PI.toFloat()
                val e = specAt(rnd)
                val len = sw(4f) + (e * 1.4f).coerceIn(0f, 1f) * maxL
                drawLine(
                    color = col(rnd, hsl(30f + rnd * 40f, 90f, 55f + e * 15f), (0.30f + e * 0.6f) * alpha),
                    start = Offset(cx, cy),
                    end = Offset(cx + cos(a) * len, cy + sin(a) * len),
                    strokeWidth = sw(1.5f) + e * sw(3f), cap = StrokeCap.Round
                )
            }
        }

        VisualizerStyle.DUAL_RING -> {
            val cx = w / 2f; val cy = h / 2f
            fun ring(R: Float, getter: (Float) -> Float, dir: Float, col: Color) {
                val pth = Path()
                val N = 90
                for (i in 0..N) {
                    val t = i / N.toFloat()
                    val a = dir * (t * 2f * PI.toFloat()) - PI.toFloat() / 2f
                    val rr = R + (getter(t) - 0.4f) * R * 0.42f
                    val x = cx + cos(a) * rr; val y = cy + sin(a) * rr
                    if (i == 0) pth.moveTo(x, y) else pth.lineTo(x, y)
                }
                drawPath(
                    pth, col.copy(alpha = col.alpha * alpha),
                    style = Stroke(width = sw(2.4f), join = StrokeJoin.Round)
                )
            }
            ring(minOf(w, h) * 0.26f, { t -> specAt(t) }, 1f, col(0.25f, Color(0xFF3ECF8E), 0.85f))
            ring(minOf(w, h) * 0.40f, { t -> specAt(1f - t) }, -1f, col(0.75f, Color(0xFFF0A94C), 0.80f))
        }

        VisualizerStyle.SQUARE_BARS -> {
            val N = 40
            val gap = w / N
            val bw = gap * 0.72f
            for (i in 0 until N) {
                val t = i / N.toFloat()
                val e = (specAt(t) * 1.35f).coerceIn(0f, 1f)
                val len = e * h * 0.82f
                drawRect(
                    color = col(t, hsl(200f + t * 65f, 78f, 46f + e * 22f), 0.92f * alpha),
                    topLeft = Offset(gap * i + (gap - bw) / 2f, h - len - sw(6f)),
                    size = Size(bw, len)
                )
            }
        }

        VisualizerStyle.THIN_BARS -> {
            val N = 80
            val gap = w / N
            val bw = maxOf(sw(1f), gap * 0.4f)
            for (i in 0 until N) {
                val t = i / N.toFloat()
                val e = (specAt(t) * 1.3f).coerceIn(0f, 1f)
                val len = e * h * 0.86f
                drawRect(
                    color = col(t, hsl(198f + t * 70f, 82f, 48f + e * 20f), 0.95f * alpha),
                    topLeft = Offset(gap * i, h - len - sw(5f)),
                    size = Size(bw, len)
                )
            }
        }

        VisualizerStyle.STEPS -> {
            val N = 32
            val gap = w / N
            val bw = gap * 0.66f
            val steps = 5
            val unit = (h * 0.86f) / steps
            for (i in 0 until N) {
                val t = i / N.toFloat()
                val e = (specAt(t) * 1.3f).coerceIn(0f, 1f)
                val lv = maxOf(1, Math.round(e * steps))
                for (k in 0 until lv) {
                    drawRect(
                        color = col(t + k * 0.02f, hsl(205f + t * 50f, 78f, 44f + (k / steps.toFloat()) * 26f), 0.9f * alpha),
                        topLeft = Offset(gap * i + (gap - bw) / 2f, h - (k + 1) * unit - sw(4f)),
                        size = Size(bw, unit - sw(2f))
                    )
                }
            }
        }

        VisualizerStyle.RADAR -> {
            val ampY = h * 0.38f
            val pth = Path()
            val N = 64
            for (i in 0..N) {
                val t = i / N.toFloat()
                val e = specAt(t)
                val y = mid - (e - 0.4f) * h * 0.8f
                if (i == 0) pth.moveTo(0f, y) else pth.lineTo(w * t, y)
            }
            drawPath(
                pth, col(0.5f, Color(0xFF78E6B4), 0.92f * alpha),
                style = Stroke(width = sw(2.2f), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            // 扫描线：位置由时间相位驱动（预览页 `fallbackPhase*0.35 % 1`）
            val sx = (ph * 0.35f % 1f) * w
            val bandW = w * 0.10f
            val scanBase = pal(0.8f, Color(0xFF78E6B4))
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        scanBase.copy(alpha = 0f),
                        scanBase.copy(alpha = 0.35f * alpha)
                    ),
                    startX = sx - bandW, endX = sx
                ),
                topLeft = Offset((sx - bandW).coerceAtLeast(0f), 0f),
                size = Size(bandW.coerceAtMost(w), h)
            )
        }

        VisualizerStyle.HEATMAP -> {
            val cols = 48
            val rows = 8
            val cw = w / cols
            val ch = h / rows
            for (x in 0 until cols) {
                for (y in 0 until rows) {
                    val t = x / cols.toFloat()
                    val tt = (t + y * 0.012f + ph * 0.08f) % 1f
                    val e = (specAt(tt) * 1.3f).coerceIn(0f, 1f)
                    drawRect(
                        color = col(t, hsl(195f + t * 65f, 82f, 20f + e * 55f), (0.18f + e * 0.75f) * alpha),
                        topLeft = Offset(x * cw, y * ch),
                        size = Size(cw - sw(1f), ch - sw(1f))
                    )
                }
            }
        }
    }
}

/** 画布水平中线 */
private fun midX(w: Float) = w / 2f

/** 曲线的取样点数 */
private const val TAG = "MusicVisualizer"

/** 只在拿到第一帧真实数据时打一次日志 */
private var loggedFirstData = false

private var frameCounter = 0

private const val SAMPLES = 96

private const val SIGNAL_EPSILON = 0.02f

/** 无真实数据时的模拟能量 */
private fun simulatedEnergy(t: Float, a: Float, b: Float, c: Float, playing: Boolean): Float {
    if (!playing) return 0f
    // 让三段的"重心"分别落在 0.15 / 0.5 / 0.85 附近，且各自有独立的相位偏移。
    val wLow = kotlin.math.exp(-((t - 0.15f) * (t - 0.15f)) / 0.07f)
    val wMid = kotlin.math.exp(-((t - 0.5f) * (t - 0.5f)) / 0.10f)
    val wHigh = kotlin.math.exp(-((t - 0.85f) * (t - 0.85f)) / 0.07f)
    return (a * wLow + b * wMid + c * wHigh).coerceIn(-1f, 1f)
}

private fun centerSmooth(energy: FloatArray, i: Int): Float {
    val prev = energy[(i - 1).coerceIn(0, energy.lastIndex)]
    val cur = energy[i]
    val next = energy[(i + 1).coerceIn(0, energy.lastIndex)]
    return (prev + cur + next) / 3f
}

@Composable
private fun rememberMixCurve(
    context: Context,
    playing: Boolean,
    samples: Int,
    /** 跟随平滑 */
    smoothing: Float,
    captureState: PlaybackCapture.State
): FloatArray? {
    var curve by remember { mutableStateOf<FloatArray?>(null) }

    var hasSignal by remember { mutableStateOf(false) }

    val smoothState = rememberUpdatedState(smoothing)
    val smoothBuf = remember { arrayOfNulls<FloatArray>(1) }

    DisposableEffect(playing, samples, captureState) {
        fun reset() {
            curve = null
            hasSignal = false
        }
        if (!playing) {
            reset()
            return@DisposableEffect onDispose {}
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "未授予 RECORD_AUDIO，频谱退回模拟动画")
            reset()
            return@DisposableEffect onDispose {}
        }

        val source: AutoCloseable? = SpectrumSourceFactory.open(
            frameSize = 1_024,
            bands = samples,
            smoothing = smoothing,
            onSpectrum = { frame ->
                val s = smoothState.value.coerceIn(0f, 0.9f)
                val prev = smoothBuf[0]
                val smoothed: FloatArray = if (prev == null || prev.size != frame.size) {
                    frame.copyOf().also { smoothBuf[0] = it }
                } else {
                    for (i in frame.indices) prev[i] = prev[i] * s + frame[i] * (1f - s)
                    prev
                }
                if (!hasSignal && frame.max() > SIGNAL_EPSILON) {
                    hasSignal = true
                    Log.i(TAG, "✅ 绘制切换到【真实频谱】hasSignal=true peak=${"%.3f".format(frame.max())}")
                }
                curve = if (hasSignal) smoothed.copyOf() else null
                frameCounter++
                if (frameCounter % 20 == 0) {
                    var mn = 1f; var mx = 0f; var sum = 0f
                    for (v in frame) { if (v < mn) mn = v; if (v > mx) mx = v; sum += v }
                    Log.i(
                        TAG,
                        "FFT#$frameCounter min=${"%.3f".format(mn)} max=${"%.3f".format(mx)} " +
                            "avg=${"%.3f".format(sum / frame.size)} hasSignal=$hasSignal"
                    )
                }
                if (!loggedFirstData) {
                    loggedFirstData = true
                    Log.i(TAG, "频谱已接入真实音频: 峰值=${"%.3f".format(frame.max())}")
                }
            }
        )
        if (source == null) {
            Log.w(TAG, "两条通路都不可用（Visualizer 与 REMOTE_SUBMIX），本次退回模拟动画")
            reset()
        }

        onDispose {
            runCatching { source?.close() }
            reset()
        }
    }
    return curve
}

private fun resampleCurve(fft: ByteArray, samplingRate: Int, samples: Int): FloatArray {
    val n = fft.size
    val out = FloatArray(samples)
    if (n < 8 || samplingRate <= 0) return out
    val binHz = (samplingRate / 2f) / (n / 2f)
    val half = n / 2

    val fMin = 20f
    val fMax = 12_000f
    val ratio = fMax / fMin
    for (i in 0 until samples) {
        // 对数分界：第 i 段的 [lo, hi) 频率。
        val lo = fMin * Math.pow(ratio.toDouble(), i / samples.toDouble()).toFloat()
        val hi = fMin * Math.pow(ratio.toDouble(), (i + 1) / samples.toDouble()).toFloat()
        var from = (lo / binHz).toInt().coerceIn(0, half - 1)
        var to = (hi / binHz).toInt().coerceIn(from + 1, half)
        if (to <= from) to = (from + 1).coerceAtMost(half)
        var sum = 0f
        var count = 0
        for (k in from until to) {
            val v = fft[k].toInt()
            sum += v * v
            count++
        }
        if (count == 0) continue
        val rms = Math.sqrt((sum / count).toDouble()).toFloat()
        out[i] = (rms / 128f).coerceIn(0f, 1f)
    }
    return out
}
