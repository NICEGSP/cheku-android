package com.cheku.app.glass

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Press-progress source for every glass surface */
@Stable
class GlassPressState internal constructor(
    private val scope: CoroutineScope,
    private val animationsEnabled: Boolean
) {
    private val press = Animatable(0f)

    val pressProgress: Float get() = press.value

    internal fun onPressStart() {
        scope.launch {
            if (animationsEnabled) press.animateTo(1f, PressSpringSpec) else press.snapTo(1f)
        }
    }

    internal fun onPressEnd() {
        scope.launch {
            if (animationsEnabled) press.animateTo(0f, PressSpringSpec) else press.snapTo(0f)
        }
    }

    /** Press-only gesture modifier */
    val gestureModifier: Modifier = Modifier.pointerInput(this) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            onPressStart()
            waitForUpOrCancellation()
            onPressEnd()
        }
    }

    /** Alias kept for the architecture class diagram */
    val modifier: Modifier get() = gestureModifier

    companion object {
        val PressSpringSpec = spring<Float>(dampingRatio = 0.72f, stiffness = 240f)
    }
}

@Composable
fun rememberGlassAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale =
            try {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
                )
            } catch (_: Exception) {
                1f
            }
        scale != 0f
    }
}

@Composable
fun rememberGlassPressState(
    animationsEnabled: Boolean = rememberGlassAnimationsEnabled()
): GlassPressState {
    val scope = rememberCoroutineScope()
    return remember(scope, animationsEnabled) {
        GlassPressState(scope, animationsEnabled)
    }
}

fun Modifier.glassPressable(
    state: GlassPressState,
    enabled: Boolean = true,
    role: Role? = null,
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
): Modifier {
    var modifier = this
    if (role != null || onClick != null) {
        modifier = modifier.semantics {
            if (role != null) this.role = role
            if (onClick != null) this.onClick(label = onClickLabel, action = null)
        }
    }
    if (!enabled) return modifier
    return modifier.pointerInput(state, onClick, onLongClick) {
        detectTapGestures(
            onPress = {
                state.onPressStart()
                tryAwaitRelease()
                state.onPressEnd()
            },
            onTap = { onClick?.invoke() },
            onLongPress = onLongClick?.let { callback -> { callback() } }
        )
    }
}
