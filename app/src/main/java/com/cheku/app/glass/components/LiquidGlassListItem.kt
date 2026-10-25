package com.cheku.app.glass.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cheku.app.glass.GlassLevel
import com.cheku.app.glass.GlassTokens
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.cheku.app.glass.glassPressable
import com.kyant.backdrop.highlight.Highlight
import com.cheku.app.glass.rememberGlassPressState
import com.kyant.backdrop.shadow.Shadow

@Composable
fun LiquidGlassListItem(
    modifier: Modifier = Modifier,
    /** 列表项圆角 */
    shape: Shape = RoundedCornerShape(12.dp),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val spec = GlassTokens.specFor(GlassLevel.L1_Card)
    val fill = spec.fill()
    val pressState = rememberGlassPressState()
    val clickable = onClick != null || onLongClick != null
    val progress = { if (clickable) pressState.pressProgress else 0f }

    Column(
        modifier = modifier
            .glassPressable(
                state = pressState,
                enabled = clickable,
                role = if (clickable) Role.Button else null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .graphicsLayer {
                val p = progress()
                scaleX = 1f - 0.02f * p
                scaleY = 1f - 0.02f * p
            }
            .drawBackdrop(
                backdrop = emptyBackdrop(),
                shape = { shape },
                effects = { /* lens = None: no refraction / no capture for list items */ },
                highlight = { glassHighlightOf(spec, progress()) },
                shadow = { Shadow(radius = 4.dp, alpha = 0.08f) },
                innerShadow = null,
                onDrawSurface = {
                    drawGlassFill(fill)
                    val p = progress()
                    if (p > 0f) {
                        drawRect(Color.Black.copy(alpha = 0.04f * p))
                    }
                }
            ),
        content = content
    )
}
