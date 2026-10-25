package com.cheku.app.glass.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cheku.app.NAV_ENTER_MS
import com.cheku.app.NAV_EXIT_MS
import com.cheku.app.NAV_FADE_IN_MS
import com.cheku.app.NAV_FADE_OUT_MS

fun glassDialogEnter(): EnterTransition =
    fadeIn(tween(NAV_FADE_IN_MS)) + scaleIn(
        initialScale = 0.94f,
        animationSpec = tween(NAV_ENTER_MS, easing = FastOutSlowInEasing)
    )

fun glassDialogExit(): ExitTransition =
    fadeOut(tween(NAV_FADE_OUT_MS, easing = FastOutSlowInEasing)) + scaleOut(
        targetScale = 0.97f,
        animationSpec = tween(NAV_EXIT_MS, easing = FastOutSlowInEasing)
    )

/** 弹窗内容宿主 */
@Composable
fun GlassDialogMotionHost(
    shown: Boolean,
    onExited: () -> Unit,
    content: @Composable () -> Unit
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = shown
    AnimatedVisibility(
        visibleState = state,
        enter = glassDialogEnter(),
        exit = glassDialogExit()
    ) {
        content()
    }
    var everShown by remember { mutableStateOf(false) }
    LaunchedEffect(shown) { if (shown) everShown = true }
    val exited = everShown && !shown && !state.currentState && state.isIdle
    val notified = remember { mutableStateOf(false) }
    SideEffect {
        if (exited && !notified.value) {
            notified.value = true
            onExited()
        }
    }
}
