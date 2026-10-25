package com.cheku.app.glass.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.cheku.app.glass.LocalCardBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.cheku.glass.ui.GlassTransitionTier
import com.cheku.app.glass.GlassLens
import com.cheku.app.glass.GlassLevel
import com.cheku.app.glass.GlassSpec
import com.cheku.app.glass.GlassTokens
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.cheku.app.glass.glassPressable
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.cheku.app.glass.rememberGlassPressState
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

internal fun BackdropEffectScope.applyGlassLens(
    spec: GlassSpec,
    pressProgress: Float,
    tier: GlassTransitionTier = GlassTransitionTier.None
) {
    val lensSpec = spec.lensSpec
    val scale = if (tier == GlassTransitionTier.Exiting) 0.5f else 1f
    when (spec.lens) {
        GlassLens.None -> Unit

        GlassLens.Weak -> {
            if (lensSpec != null && lensSpec.amountDp > 0f) {
                lens(
                    refractionHeight = lensSpec.heightDp.dp.toPx() * scale,
                    refractionAmount = lensSpec.amountDp.dp.toPx() * scale,
                    depthEffect = lensSpec.depth,
                    chromaticAberration =
                        tier == GlassTransitionTier.None && lensSpec.chromaticAberration
                )
            }
        }

        GlassLens.Full -> {
            if (lensSpec != null) {
                val height = lerp(lensSpec.heightDp, 24f, pressProgress).dp.toPx() * scale
                val amount = lerp(lensSpec.amountDp, 24f, pressProgress).dp.toPx() * scale
                lens(
                    refractionHeight = height,
                    refractionAmount = amount,
                    depthEffect = lensSpec.depth,
                    chromaticAberration = tier == GlassTransitionTier.None &&
                        (lensSpec.chromaticAberration || pressProgress > 0.01f)
                )
            }
        }
    }
}

/** Shared highlight computation */
internal fun glassHighlightOf(
    spec: GlassSpec,
    pressProgress: Float
): Highlight? {
    val hl = spec.highlight ?: return null
    val base = hl.alpha()
    return Highlight.Default.copy(
        width = hl.widthDp.dp,
        blurRadius = hl.widthDp.dp / 2f,
        alpha = base + (1f - base) * pressProgress,
        style = hl.style
    )
}

/** Shared outer shadow computation */
internal fun glassShadowOf(spec: GlassSpec, pressProgress: Float): Shadow? {
    val sh = spec.shadow ?: return null
    return Shadow(
        radius = (sh.radiusDp + 6f * pressProgress).dp,
        alpha = sh.alpha() + (0.26f - sh.alpha()) * pressProgress
    )
}

/** Shared inner shadow computation */
internal fun glassInnerShadowOf(spec: GlassSpec, pressProgress: Float): InnerShadow? {
    val ish = spec.innerShadow ?: return null
    val staticAlpha = ish.alpha()
    val alpha = if (staticAlpha > 0f) maxOf(staticAlpha, pressProgress * 0.18f) else pressProgress
    val radiusDp = if (staticAlpha > 0f) ish.radiusDp + 6f * pressProgress else ish.radiusDp * pressProgress
    return if (alpha > 0f) {
        InnerShadow(radius = radiusDp.dp, alpha = alpha)
    } else {
        null
    }
}

internal fun DrawScope.drawGlassFill(
    fill: androidx.compose.ui.graphics.Color,
    accentWash: androidx.compose.ui.graphics.Color? = null
) {
    drawRect(fill)
    if (accentWash != null) drawRect(accentWash)
}

/** The composite glass panel for L1/L2/L3 */
@Composable
fun GlassPanel(
    level: GlassLevel,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = GlassTokens.shapeCard,
    enabled: Boolean = true,
    accentWash: androidx.compose.ui.graphics.Color? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val spec = specForLayer(level)
    val fill = spec.fill()
    val pressState = rememberGlassPressState()
    val clickable = enabled && (onClick != null || onLongClick != null)
    val progress = { if (clickable) pressState.pressProgress else 0f }

    val cardLayer = rememberLayerBackdrop()

    Box(
        modifier = modifier
            .glassPressable(
                state = pressState,
                enabled = clickable,
                role = if (clickable) Role.Button else null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .then(
                if (enabled) {
                    Modifier
                } else {
                    Modifier.graphicsLayer { alpha = 0.38f }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // ---- 玻璃面（唯一记录点：只记录这一层） ----
        Box(
            Modifier
                .matchParentSize()
                .layerBackdrop(cardLayer)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        val p = progress()
                        vibrancy()
                        if (spec.blurRadiusDp > 0f) blur(spec.blurRadiusDp.dp.toPx())
                        if (level == GlassLevel.L3_Dialog) colorControls(saturation = 1.2f)
                        applyGlassLens(spec, p)
                    },
                    highlight = { glassHighlightOf(spec, progress()) },
                    shadow = { glassShadowOf(spec, progress()) },
                    innerShadow = { glassInnerShadowOf(spec, progress()) },
                    onDrawSurface = { drawGlassFill(fill, accentWash) }
                )
        )
        // ---- 内容层（二层控件在此采样 cardLayer） ----
        CompositionLocalProvider(LocalCardBackdrop provides cardLayer) {
            content()
        }
    }
}

/** 取一层级的玻璃规格 */
@Composable
private fun specForLayer(level: GlassLevel): GlassSpec = GlassTokens.specFor(level)

/** Generic glass modifier */
@Composable
fun Modifier.liquidGlass(
    level: GlassLevel,
    shape: Shape,
    backdrop: Backdrop,
    pressProgress: () -> Float = { 0f },
    modifierState: Any? = null
): Modifier {
    val spec = specForLayer(level)
    val fill = spec.fill()
    if (modifierState != null) {
        // No cached state is keyed on it yet; referencing it documents the contract for callers.
        @Suppress("UNUSED_EXPRESSION")
        modifierState.hashCode()
    }
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            val p = pressProgress()
            vibrancy()
            if (spec.blurRadiusDp > 0f) blur(spec.blurRadiusDp.dp.toPx())
            if (level == GlassLevel.L3_Dialog) colorControls(saturation = 1.2f)
            applyGlassLens(spec, p)
        },
        highlight = { glassHighlightOf(spec, pressProgress()) },
        shadow = { glassShadowOf(spec, pressProgress()) },
        innerShadow = { glassInnerShadowOf(spec, pressProgress()) },
        onDrawSurface = { drawGlassFill(fill) }
    )
}
