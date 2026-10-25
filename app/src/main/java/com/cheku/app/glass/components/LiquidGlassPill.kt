package com.cheku.app.glass.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.cheku.app.glass.GlassLevel
import com.cheku.app.glass.GlassTokens
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/** L4 selected capsule / pill */
@Composable
fun LiquidGlassPill(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape,
    pressProgress: () -> Float,
    content: @Composable BoxScope.() -> Unit
) {
    val spec = GlassTokens.specFor(GlassLevel.L4_Nav)
    val fill = spec.fill()

    Box(
        modifier = modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                val p = pressProgress()
                vibrancy()
                blur(8f.dp.toPx() * (1f - p))
                lens(
                    refractionHeight = 10f.dp.toPx() * p,
                    refractionAmount = 14f.dp.toPx() * p,
                    depthEffect = true,
                    chromaticAberration = p > 0.01f
                )
            },
            highlight = { Highlight.Default.copy(alpha = pressProgress()) },
            shadow = {
                val p = pressProgress()
                Shadow(radius = (2.5f + 2.5f * p).dp, alpha = p)
            },
            innerShadow = {
                val p = pressProgress()
                if (p > 0f) InnerShadow(radius = 8f.dp * p, alpha = p) else null
            },
            onDrawSurface = { drawRect(fill) }
        ),
        contentAlignment = Alignment.Center,
        content = content
    )
}
