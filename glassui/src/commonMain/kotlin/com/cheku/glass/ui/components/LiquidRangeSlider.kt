/*
 * Derived from Kyant0/AndroidLiquidGlass (https://github.com/Kyant0/AndroidLiquidGlass),
 * licensed under the Apache License, Version 2.0. Modified for CheKu.
 */
package com.cheku.glass.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.cheku.glass.ui.utils.DampedDragAnimation
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

@Composable
fun LiquidRangeSlider(
    value: () -> ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor =
        if (isLightTheme) Color(0xFF0088FF)
        else Color(0xFF0091FF)
    val trackColor =
        if (isLightTheme) Color(0xFF787878).copy(0.2f)
        else Color(0xFF787880).copy(0.36f)

    val trackBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        val span = (valueRange.endInclusive - valueRange.start).takeIf { it != 0f } ?: 1f

        var didDragStart by remember { mutableStateOf(false) }
        var didDragEnd by remember { mutableStateOf(false) }

        // 两个把手各自持有一个 DampedDragAnimation，参数与单滑块版相同。
        val startAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value().start,
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {},
                onDragStopped = {
                    if (didDragStart) {
                        onValueChange(targetValue..value().endInclusive)
                    }
                },
                onDrag = { _, dragAmount ->
                    if (!didDragStart) {
                        didDragStart = dragAmount.x != 0f
                    }
                    val delta = span * (dragAmount.x / trackWidth)
                    val raw = if (isLtr) targetValue + delta else targetValue - delta
                    // 夹紧：起点不得越过终点（保证两端拇指头不交叉）。
                    val next = raw.coerceIn(valueRange.start, value().endInclusive)
                    onValueChange(next..value().endInclusive)
                }
            )
        }
        val endAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value().endInclusive,
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {},
                onDragStopped = {
                    if (didDragEnd) {
                        onValueChange(value().start..targetValue)
                    }
                },
                onDrag = { _, dragAmount ->
                    if (!didDragEnd) {
                        didDragEnd = dragAmount.x != 0f
                    }
                    val delta = span * (dragAmount.x / trackWidth)
                    val raw = if (isLtr) targetValue + delta else targetValue - delta
                    // 夹紧：终点不得越过起点。
                    val next = raw.coerceIn(value().start, valueRange.endInclusive)
                    onValueChange(value().start..next)
                }
            )
        }
        LaunchedEffect(startAnimation) {
            snapshotFlow { value().start }
                .collectLatest { v ->
                    if (startAnimation.targetValue != v) {
                        startAnimation.updateValue(v)
                    }
                }
        }
        LaunchedEffect(endAnimation) {
            snapshotFlow { value().endInclusive }
                .collectLatest { v ->
                    if (endAnimation.targetValue != v) {
                        endAnimation.updateValue(v)
                    }
                }
        }

        Box(Modifier.layerBackdrop(trackBackdrop)) {
            // 底轨
            Box(
                Modifier
                    .clip(Capsule())
                    .background(trackColor)
                    .pointerInput(animationScope) {
                        detectTapGestures { position ->
                            val fraction = (position.x / trackWidth).fastCoerceIn(0f, 1f)
                            val clicked =
                                if (isLtr) valueRange.start + span * fraction
                                else valueRange.endInclusive - span * fraction
                            val current = value()
                            // 点击后由更近的一端跟上（源单拇指版是整条轨道跳转）。
                            if (abs(clicked - current.start) <= abs(clicked - current.endInclusive)) {
                                val next = clicked.coerceIn(valueRange.start, current.endInclusive)
                                startAnimation.animateToValue(next)
                                onValueChange(next..current.endInclusive)
                            } else {
                                val next = clicked.coerceIn(current.start, valueRange.endInclusive)
                                endAnimation.animateToValue(next)
                                onValueChange(current.start..next)
                            }
                        }
                    }
                    .height(6f.dp)
                    .fillMaxWidth()
            )

            // 区间填充段：从起点拇指到终点拇指
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = trackWidth * startAnimation.progress * if (isLtr) 1f else -1f
                    }
                    .clip(Capsule())
                    .background(accentColor)
                    .height(6f.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val left = constraints.maxWidth * startAnimation.progress
                        val right = constraints.maxWidth * endAnimation.progress
                        val width = (right - left).fastRoundToInt().coerceAtLeast(0)
                        layout(width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
            )
        }

        // 起点拇指头
        SliderThumb(
            isLtr = isLtr,
            trackWidth = trackWidth,
            progress = { startAnimation.progress },
            animation = startAnimation,
            trackBackdrop = trackBackdrop,
            backdrop = backdrop
        )

        // 终点拇指头
        SliderThumb(
            isLtr = isLtr,
            trackWidth = trackWidth,
            progress = { endAnimation.progress },
            animation = endAnimation,
            trackBackdrop = trackBackdrop,
            backdrop = backdrop
        )
    }
}

@Composable
private fun SliderThumb(
    isLtr: Boolean,
    trackWidth: Int,
    progress: () -> Float,
    animation: DampedDragAnimation,
    trackBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    backdrop: Backdrop
) {
    Box(
        Modifier
            .graphicsLayer {
                translationX =
                    (-size.width / 2f + trackWidth * progress())
                        .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) * if (isLtr) 1f else -1f
            }
            .then(animation.modifier)
            .drawBackdrop(
                backdrop = rememberCombinedBackdrop(
                    backdrop,
                    rememberBackdrop(trackBackdrop) { drawBackdrop ->
                        val press = animation.pressProgress
                        val scaleX = lerp(2f / 3f, 1f, press)
                        val scaleY = lerp(0f, 1f, press)
                        scale(scaleX, scaleY) {
                            drawBackdrop()
                        }
                    }
                ),
                shape = { Capsule() },
                effects = {
                    val press = animation.pressProgress
                    blur(12f.dp.toPx() * (1f - press))
                    lens(
                        10f.dp.toPx() * press,
                        14f.dp.toPx() * press,
                        chromaticAberration = true
                    )
                },
                highlight = {
                    val press = animation.pressProgress
                    Highlight.Ambient.copy(
                        width = Highlight.Ambient.width / 1.5f,
                        blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                        alpha = press
                    )
                },
                shadow = {
                    Shadow(
                        radius = 4f.dp,
                        color = Color.Black.copy(alpha = 0.05f)
                    )
                },
                innerShadow = {
                    val press = animation.pressProgress
                    InnerShadow(
                        radius = 4f.dp * press,
                        alpha = press
                    )
                },
                layerBlock = {
                    scaleX = animation.scaleX
                    scaleY = animation.scaleY
                    val velocity = animation.velocity / 10f
                    scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                    scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                },
                onDrawSurface = {
                    val press = animation.pressProgress
                    drawRect(Color.White.copy(alpha = 1f - press))
                }
            )
            .size(40f.dp, 24f.dp)
    )
}
