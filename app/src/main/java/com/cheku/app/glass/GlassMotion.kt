package com.cheku.app.glass

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

object GlassMotion {
    const val SELECTION_DURATION_MS: Int = 360

    val SelectionEasing: Easing = FastOutSlowInEasing

    /** 选中态胶囊位移的统一动画 spec */
    fun <T> selectionSpec(): TweenSpec<T> =
        tween(durationMillis = SELECTION_DURATION_MS, easing = SelectionEasing)

    /** 松手吸附用的弹簧 */
    const val SNAP_DAMPING_RATIO: Float = 1f
    const val SNAP_STIFFNESS: Float = 300f
    const val SNAP_VISIBILITY_THRESHOLD: Float = 0.5f
}
