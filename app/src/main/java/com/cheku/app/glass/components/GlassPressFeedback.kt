package com.cheku.app.glass.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun Modifier.glassPressFeedback(
    enabled: Boolean = true,
    pressedScale: Float = 0.985f,
    pressedAlpha: Float = 0.92f,
    disabledAlpha: Float = 0.55f,
    onClick: (() -> Unit)? = null
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(if (pressed) 90 else 180, easing = FastOutSlowInEasing),
        label = "glass-press-scale"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = if (pressed) pressedAlpha else if (enabled) 1f else disabledAlpha
        }
        .then(
            if (onClick == null) {
                Modifier
            } else {
                Modifier.clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick
                )
            }
        )
}
