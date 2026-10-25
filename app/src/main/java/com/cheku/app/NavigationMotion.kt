package com.cheku.app

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/** 进入页位移时长 */
internal const val NAV_ENTER_MS = 340

internal const val NAV_EXIT_MS = 300

/** 进入页淡入时长 */
internal const val NAV_FADE_IN_MS = 220

/** 退出页淡出时长 */
internal const val NAV_FADE_OUT_MS = 170

internal const val NAV_SERIAL_EXIT_MS = 170
internal const val NAV_SERIAL_ENTER_MS = 170

/** 层级子页转场 */
internal fun hierarchyMotion(enteringDetail: Boolean): ContentTransform =
    ContentTransform(
        targetContentEnter =
            slideInHorizontally(tween(NAV_ENTER_MS, easing = FastOutSlowInEasing)) {
                if (enteringDetail) it / 3 else -it / 3
            } + fadeIn(tween(NAV_FADE_IN_MS)),
        initialContentExit =
            slideOutHorizontally(tween(NAV_EXIT_MS, easing = FastOutSlowInEasing)) {
                if (enteringDetail) -it / 4 else it / 4
            } + fadeOut(tween(NAV_FADE_OUT_MS)),
        sizeTransform = SizeTransform(clip = false)
    )

/** 一级 Tab 之间的横向推入 */
internal fun tabMotion(direction: Int): ContentTransform =
    ContentTransform(
        targetContentEnter =
            slideInHorizontally(tween(NAV_ENTER_MS, easing = FastOutSlowInEasing)) {
                it * direction / 3
            } + fadeIn(tween(NAV_FADE_IN_MS)),
        initialContentExit =
            slideOutHorizontally(tween(NAV_EXIT_MS, easing = FastOutSlowInEasing)) {
                -it * direction / 4
            } + fadeOut(tween(NAV_FADE_OUT_MS)),
        // 同 hierarchyMotion：关掉容器裁切，见那里的说明。
        sizeTransform = SizeTransform(clip = false)
    )

@Composable
internal fun <T : Any> SerialContentTransition(
    target: T,
    directionOf: (from: T, to: T) -> Int,
    content: @Composable (T) -> Unit
) {
    var displayed by remember { mutableStateOf(target) }
    var incoming by remember { mutableStateOf<T?>(null) }
    var direction by remember { mutableIntStateOf(1) }
    var exiting by remember { mutableStateOf(false) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(target) {
        if (target == displayed) {
            incoming = null
            if (exiting) {
                progress.animateTo(1f, tween(NAV_SERIAL_EXIT_MS, easing = FastOutSlowInEasing))
                exiting = false
            }
            return@LaunchedEffect
        }
        direction = directionOf(displayed, target)

        exiting = true
        incoming = target
        progress.snapTo(1f)
        progress.animateTo(0f, tween(NAV_SERIAL_EXIT_MS, easing = FastOutSlowInEasing))

        displayed = target
        incoming = null
        exiting = false
        progress.snapTo(0f)
        progress.animateTo(1f, tween(NAV_SERIAL_ENTER_MS, easing = FastOutSlowInEasing))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        // 第 0 个永远是正在显示的页；第 1 个（若有）是预备页。
        val pages = incoming?.takeIf { it != displayed }?.let { listOf(displayed, it) } ?: listOf(displayed)
        Layout(
            content = {
                pages.forEach { page ->
                    key(page) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = progress.value }
                                .offset {
                                    val fraction = if (exiting) 0.25f else 1f / 3f
                                    val sign = if (exiting) -1f else 1f
                                    IntOffset(
                                        (sign * direction * fraction * (1f - progress.value) * widthPx).roundToInt(),
                                        0
                                    )
                                }
                        ) {
                            content(page)
                        }
                    }
                }
            }
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.first().place(0, 0)
            }
        }
    }
}
