package com.cheku.app.carmode

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.cheku.app.glass.LocalAppDarkLevel
import com.cheku.app.glass.glassThemeColor
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** 环半径 */
private const val RING_RADIUS = 126f

/** 四个汉字离圆心的距离 */
private const val CARDINAL_RADIUS = 155f

private const val CARDINAL_GLYPH_HALF = 9f

private const val COMPASS_DESIGN_DP = (CARDINAL_RADIUS + CARDINAL_GLYPH_HALF) * 2f

/** 前方灰三角 */
private const val AHEAD_TIP_RADIUS = 112f
private const val AHEAD_BASE_RADIUS = 86f
private const val AHEAD_HALF_WIDTH = 16f

/** 北向红三角 */
private const val NORTH_TIP_RADIUS = CARDINAL_RADIUS - 12f
private const val NORTH_BASE_RADIUS = CARDINAL_RADIUS - 26f
private const val NORTH_HALF_WIDTH = 7.5f

/** 四个方位的基准角 */
private const val BEARING_NORTH = 0f
private const val BEARING_EAST = 90f
private const val BEARING_SOUTH = 180f
private const val BEARING_WEST = 270f

/** 降级时汉字/三角/前方三角的不透明度 */
private const val DEGRADED_MARK_ALPHA = 0.75f
private const val DEGRADED_AHEAD_ALPHA = 0.30f

@Composable
internal fun CarCompass(
    speedText: String,
    hasFix: Boolean,
    modifier: Modifier = Modifier
) {
    val heading = rememberCarHeading()
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // 可用空间的短板决定缩放：高度取不到（约束无界）时退到宽度。
        val available = if (maxHeight.value.isFinite()) {
            min(maxWidth.value, maxHeight.value)
        } else {
            maxWidth.value
        }
        val unit = (available / COMPASS_DESIGN_DP).coerceIn(0.25f, 1f)
        val block = (COMPASS_DESIGN_DP * unit).dp
        val density = LocalDensity.current
        val u = density.density * unit        // 1 设计 dp = u px

        val level = LocalAppDarkLevel.current
        val ringColor = HubTextPrimary.copy(alpha = lerp(0.16f, 0.22f, level))
        val neutralMark = glassThemeColor(COLOR_MARK_LIGHT, COLOR_MARK_DARK)
        val northMark = glassThemeColor(COLOR_NORTH_LIGHT, COLOR_NORTH_DARK)

        val roseAzimuth = if (heading.reliable) heading.azimuth else null
        val active = roseAzimuth != null
        val roseRotation = roseAzimuth?.let { -it } ?: 0f
        val northColorNow = if (active) northMark else neutralMark.copy(alpha = DEGRADED_MARK_ALPHA)
        val otherColorNow = if (active) neutralMark else neutralMark.copy(alpha = DEGRADED_MARK_ALPHA)
        val aheadColorNow = neutralMark.copy(
            alpha = if (active) lerp(0.70f, 0.80f, level) else DEGRADED_AHEAD_ALPHA
        )

        Box(modifier = Modifier.size(block), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f

                drawCircle(
                    color = ringColor,
                    radius = RING_RADIUS * u,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.6f * u)
                )

                drawPath(
                    path = trianglePath(
                        apex = Offset(cx, cy - AHEAD_TIP_RADIUS * u),
                        left = Offset(cx - AHEAD_HALF_WIDTH * u, cy - AHEAD_BASE_RADIUS * u),
                        right = Offset(cx + AHEAD_HALF_WIDTH * u, cy - AHEAD_BASE_RADIUS * u)
                    ),
                    color = aheadColorNow
                )

                rotate(degrees = roseRotation, pivot = Offset(cx, cy)) {
                    drawPath(
                        path = trianglePath(
                            apex = Offset(cx, cy - NORTH_TIP_RADIUS * u),
                            left = Offset(cx - NORTH_HALF_WIDTH * u, cy - NORTH_BASE_RADIUS * u),
                            right = Offset(cx + NORTH_HALF_WIDTH * u, cy - NORTH_BASE_RADIUS * u)
                        ),
                        color = northColorNow
                    )
                }
            }

            CardinalLabel("北", BEARING_NORTH, roseRotation, CARDINAL_RADIUS * u, (18f * unit).sp,
                FontWeight.Bold, northColorNow)
            CardinalLabel("东", BEARING_EAST, roseRotation, CARDINAL_RADIUS * u, (15f * unit).sp,
                FontWeight.SemiBold, otherColorNow)
            CardinalLabel("南", BEARING_SOUTH, roseRotation, CARDINAL_RADIUS * u, (15f * unit).sp,
                FontWeight.SemiBold, otherColorNow)
            CardinalLabel("西", BEARING_WEST, roseRotation, CARDINAL_RADIUS * u, (15f * unit).sp,
                FontWeight.SemiBold, otherColorNow)

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(0, (-4f * u).roundToInt()) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    speedText,
                    color = HubTextPrimary,
                    fontSize = (96f * unit).sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-2f * unit).sp,
                    maxLines = 1
                )
                Spacer(Modifier.height((2f * unit).dp))
                Text(
                    if (hasFix) "km/h" else "等待定位",
                    color = HubTextSecondary,
                    fontSize = (19f * unit).sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

/** 环外一个方位汉字 */
@Composable
private fun CardinalLabel(
    text: String,
    baseDegrees: Float,
    rotationDegrees: Float,
    radiusPx: Float,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    color: Color
) {
    val radians = Math.toRadians(((baseDegrees + rotationDegrees) % 360f).toDouble())
    val dx = (radiusPx * sin(radians)).roundToInt()
    val dy = (-radiusPx * cos(radians)).roundToInt()
    Text(
        text,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        maxLines = 1,
        modifier = Modifier.offset { IntOffset(dx, dy) }
    )
}

/** 由三点构成一个闭合三角形路径 */
private fun trianglePath(apex: Offset, left: Offset, right: Offset): Path = Path().apply {
    moveTo(apex.x, apex.y)
    lineTo(left.x, left.y)
    lineTo(right.x, right.y)
    close()
}

private val COLOR_NORTH_LIGHT = Color(0xFFFF3B30)
private val COLOR_NORTH_DARK = Color(0xFFFF453A)
private val COLOR_MARK_LIGHT = Color(0xFF98A0AC)
private val COLOR_MARK_DARK = Color(0xFF8FA0B4)
