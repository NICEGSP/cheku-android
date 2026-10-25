package com.cheku.app.carmode

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.cheku.app.glass.LocalAppDarkLevel
import com.cheku.app.glass.glassThemeColor
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

internal data class CarTrackPoint(val lat: Double, val lon: Double)

/** 每度纬度对应的米数 */
private const val METERS_PER_DEGREE_LAT = 110_574.0

/** 赤道上每度经度对应的米数 */
private const val METERS_PER_DEGREE_LON = 111_320.0

/** 包络跨度小于它就不画线，只留起点 */
private const val MIN_TRACK_SPAN_METERS = 30.0

/** 投影时轨迹四周留的空白 */
private val TRACK_PADDING = 8.dp

internal fun projectTrackRoute(
    points: List<CarTrackPoint>,
    width: Float,
    height: Float,
    padding: Float
): List<Offset> {
    if (width <= 0f || height <= 0f) return emptyList()
    val center = Offset(width / 2f, height / 2f)

    val sane = points.filter { it.lat.isFinite() && it.lon.isFinite() && abs(it.lat) <= 90.0 }
    if (sane.isEmpty()) return emptyList()
    if (sane.size == 1) return listOf(center)

    var minLat = Double.MAX_VALUE
    var maxLat = -Double.MAX_VALUE
    var minLon = Double.MAX_VALUE
    var maxLon = -Double.MAX_VALUE
    sane.forEach { point ->
        if (point.lat < minLat) minLat = point.lat
        if (point.lat > maxLat) maxLat = point.lat
        if (point.lon < minLon) minLon = point.lon
        if (point.lon > maxLon) maxLon = point.lon
    }

    // 经度的"球面收缩"系数：取包络中点的纬度。
    val lonScale = cos(Math.toRadians((minLat + maxLat) / 2.0))
    val spanX = (maxLon - minLon) * lonScale
    val spanY = maxLat - minLat

    val spanMeters = max(
        spanX * METERS_PER_DEGREE_LON,
        spanY * METERS_PER_DEGREE_LAT
    )
    if (spanMeters < MIN_TRACK_SPAN_METERS) return listOf(center)

    val availableW = (width - padding * 2f).coerceAtLeast(1f)
    val availableH = (height - padding * 2f).coerceAtLeast(1f)
    // 某一维完全没有跨度（笔直向南开）时不能除以 0：把那一维的 scale 视为"不限制"。
    val scaleX = if (spanX > 0.0) (availableW / spanX).toFloat() else Float.MAX_VALUE
    val scaleY = if (spanY > 0.0) (availableH / spanY).toFloat() else Float.MAX_VALUE
    val scale = min(scaleX, scaleY)

    val drawnW = (spanX * scale).toFloat()
    val drawnH = (spanY * scale).toFloat()
    val originX = padding + (availableW - drawnW) / 2f
    val originY = padding + (availableH - drawnH) / 2f

    return sane.map { point ->
        val x = originX + (((point.lon - minLon) * lonScale) * scale).toFloat()
        val y = originY + (((maxLat - point.lat)) * scale).toFloat()
        Offset(x, y)
    }
}

private const val TRACK_EDGE_MARGIN_RATIO = 0.06f

/** 羽化带宽度 */
private const val TRACK_EDGE_FADE_RATIO = 0.18f

@Composable
internal fun CarTrackRoute(
    points: List<CarTrackPoint>,
    recording: Boolean,
    modifier: Modifier = Modifier
) {
    val level = LocalAppDarkLevel.current
    val routeColor = HubPrimaryBlue
    val glowColor = Color.White.copy(alpha = lerp(0.5f, 0.28f, level))
    val ringColor = glassThemeColor(Color.White, Color(0xFF0E131A))
    val density = LocalDensity.current
    val paddingPx = with(density) { TRACK_PADDING.toPx() }
    val startRingRadius = with(density) { 5.dp.toPx() }
    val startCoreRadius = with(density) { 2.6.dp.toPx() }
    val currentRingRadius = with(density) { 5.6.dp.toPx() }
    val currentCoreRadius = with(density) { 3.1.dp.toPx() }
    val glowStroke = Stroke(
        width = with(density) { 11.dp.toPx() },
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
    )
    val lineStroke = Stroke(
        width = with(density) { 4.4.dp.toPx() },
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
    )
    val pulse = rememberTrackPulse(recording)

    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas

        val projected = projectTrackRoute(points, size.width, size.height, paddingPx)
        if (projected.isEmpty()) return@Canvas

        val short = min(size.width, size.height)
        val cut = min(paddingPx + short * TRACK_EDGE_MARGIN_RATIO, short * 0.25f)
        val fade = short * TRACK_EDGE_FADE_RATIO
        val cutY = (cut / size.height).coerceIn(0f, 0.5f)
        val fadeY = (fade / size.height).coerceIn(0f, 0.5f - cutY)
        val cutX = (cut / size.width).coerceIn(0f, 0.5f)
        val fadeX = (fade / size.width).coerceIn(0f, 0.5f - cutX)

        val route = Path().apply {
            moveTo(projected[0].x, projected[0].y)
            for (index in 1 until projected.size) {
                lineTo(projected[index].x, projected[index].y)
            }
        }

        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
            drawPath(path = route, color = glowColor, style = glowStroke)
            drawPath(path = route, color = routeColor, style = lineStroke)
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    cutY to Color.Transparent,
                    (cutY + fadeY) to Color.Black,
                    (1f - cutY - fadeY) to Color.Black,
                    (1f - cutY) to Color.Transparent,
                    1f to Color.Transparent,
                    startY = 0f,
                    endY = size.height
                ),
                blendMode = BlendMode.DstIn
            )
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    cutX to Color.Transparent,
                    (cutX + fadeX) to Color.Black,
                    (1f - cutX - fadeX) to Color.Black,
                    (1f - cutX) to Color.Transparent,
                    1f to Color.Transparent,
                    startX = 0f,
                    endX = size.width
                ),
                blendMode = BlendMode.DstIn
            )
            canvas.restore()
        }

        val start = projected.first()
        val current = projected.last()
        val pulseAlpha = if (recording) pulse.value else 1f
        drawCircle(color = ringColor, radius = startRingRadius, center = start)
        drawCircle(color = HubError, radius = startCoreRadius, center = start)
        drawCircle(color = ringColor, radius = currentRingRadius, center = current)
        drawCircle(
            color = HubSuccess.copy(alpha = pulseAlpha),
            radius = currentCoreRadius,
            center = current
        )
    }
}

@Composable
private fun rememberTrackPulse(active: Boolean): State<Float> {
    if (!active) return remember { mutableStateOf(1f) }
    val transition = rememberInfiniteTransition(label = "trackPulse")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            // 620ms 单程 ≈ 1.24s 一个来回：比心跳稍慢，扫一眼就知道它在动，又不抢读数字的眼。
            animation = tween(durationMillis = 620),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trackPulseAlpha"
    )
}
