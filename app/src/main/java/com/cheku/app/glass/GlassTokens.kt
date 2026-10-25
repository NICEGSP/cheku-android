package com.cheku.app.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kyant.backdrop.highlight.HighlightStyle

/** Glass elevation levels */
enum class GlassLevel {
    L1_Card,
    L2_Panel,
    S1_Control,
    L4_Nav,
    L3_Dialog
}

enum class GlassLens {
    None,
    Weak,
    Full
}

@Immutable
data class GlassLensSpec(
    val heightDp: Float,
    val amountDp: Float,
    val depth: Boolean,
    val chromaticAberration: Boolean
)

/** Directional highlight parameters */
@Immutable
data class GlassHighlightSpec(
    val widthDp: Float,
    val falloff: Float,
    val alpha: () -> Float,
    val style: HighlightStyle
)

/** Outer shadow parameters */
@Immutable
data class GlassShadowSpec(
    val radiusDp: Float,
    val alpha: () -> Float
)

/** Inner shadow parameters */
@Immutable
data class GlassInnerShadowSpec(
    val radiusDp: Float,
    val alpha: () -> Float
)

/** Full description of one glass tier */
@Immutable
data class GlassSpec(
    val level: GlassLevel,
    val fill: @Composable () -> Color,
    val blurRadiusDp: Float,
    val lens: GlassLens,
    val lensSpec: GlassLensSpec?,
    val highlight: GlassHighlightSpec?,
    val innerShadow: GlassInnerShadowSpec?,
    val shadow: GlassShadowSpec?,
    val shape: Shape,
    val consumesSharedCapture: Boolean
)

typealias HlStyle = HighlightStyle

/** The glass token layer */
object GlassTokens {
    val l1Fill: Color
        @Composable get() = glassThemeColor(
            Color(0xFFFAFAFA).copy(alpha = cardFillAlpha),
            Color(0xFF121212).copy(alpha = cardFillAlpha)
        )

    const val cardFillAlpha: Float = 0f

    /** S1 二层控件洗色 alpha */
    const val controlFillAlpha: Float = 0.34f

    val l2Fill: Color
        @Composable get() = glassThemeColor(
            Color(0xFFFAFAFA).copy(alpha = 0.22f),
            Color(0xFF121212).copy(alpha = 0.20f)
        )

    /** L3 dialog wash */
    val l3Fill: Color
        @Composable get() = glassThemeColor(
            Color(0xFFFAFAFA).copy(alpha = 0.44f),
            Color(0xFF121212).copy(alpha = 0.32f)
        )

    val navFill: Color
        @Composable get() = glassThemeColor(
            Color.White.copy(alpha = 0.30f),
            Color(0xFF182230).copy(alpha = 0.30f)
        )

    /** Control wash */
    val controlFill: Color
        @Composable get() = glassThemeColor(
            Color.White.copy(alpha = controlFillAlpha),
            Color(0xFF121212).copy(alpha = 0.28f)
        )

    const val controlBlurDp: Float = 7f

    const val actionHeightDp: Float = 46f

    /** Hairline stroke for real borders only */
    val strokeHairline: Color
        @Composable get() = glassThemeColor(
            Color.White.copy(alpha = 0.40f),
            Color.White.copy(alpha = 0.16f)
        )

    /** 进度条 / 轨道底色 */
    val trackFill: Color
        @Composable get() = glassThemeColor(
            Color(0xFF787878).copy(alpha = 0.2f),
            Color(0xFF787880).copy(alpha = 0.36f)
        )

    /** accent 洗色 */
    val accentWash: Color
        @Composable get() = glassThemeColor(
            Color(0xFF0088FF).copy(alpha = 0.12f),
            Color(0xFF0091FF).copy(alpha = 0.12f)
        )

    /** glass.shape.card - 20dp */
    val shapeCard: RoundedCornerShape get() = RoundedCornerShape(20.dp)

    /** glass.shape.control - 16dp */
    val shapeControl: RoundedCornerShape get() = RoundedCornerShape(16.dp)

    /** L3 dialog - 48dp */
    val shapeDialog: RoundedCornerShape get() = RoundedCornerShape(48.dp)

    /** L4 navigation bar - 28dp */
    val shapeNav: RoundedCornerShape get() = RoundedCornerShape(28.dp)

    /** Fully rounded capsule */
    val shapeCapsule: Shape get() = RoundedCornerShape(percent = 50)

    fun specFor(level: GlassLevel): GlassSpec = when (level) {
        GlassLevel.L1_Card -> GlassSpec(
            level = level,
            fill = { l1Fill },
            blurRadiusDp = 0f,
            lens = GlassLens.Weak,
            lensSpec = GlassLensSpec(heightDp = 24f, amountDp = 24f, depth = false, chromaticAberration = false),
            highlight = GlassHighlightSpec(widthDp = 0.5f, falloff = 1f, alpha = { 1f }, style = HighlightStyle.Plain),
            innerShadow = null,
            shadow = null,
            shape = shapeCard,
            consumesSharedCapture = true
        )

        GlassLevel.L2_Panel -> GlassSpec(
            level = level,
            fill = { l2Fill },
            blurRadiusDp = 3f,
            lens = GlassLens.Weak,
            lensSpec = GlassLensSpec(heightDp = 24f, amountDp = 24f, depth = false, chromaticAberration = false),
            highlight = GlassHighlightSpec(widthDp = 0.5f, falloff = 3f, alpha = { 0.40f }, style = HighlightStyle.Default),
            innerShadow = GlassInnerShadowSpec(radiusDp = 1f, alpha = { 0.12f }),
            shadow = GlassShadowSpec(radiusDp = 8f, alpha = { 0.14f }),
            shape = shapeCard,
            consumesSharedCapture = true
        )

        GlassLevel.S1_Control -> GlassSpec(
            level = level,
            fill = { controlFill },
            blurRadiusDp = controlBlurDp,
            lens = GlassLens.Weak,
            lensSpec = GlassLensSpec(heightDp = 12f, amountDp = 24f, depth = false, chromaticAberration = false),
            highlight = GlassHighlightSpec(widthDp = 0.5f, falloff = 2f, alpha = { 0.40f }, style = HighlightStyle.Default),
            innerShadow = GlassInnerShadowSpec(radiusDp = 1f, alpha = { 0.12f }),
            shadow = GlassShadowSpec(radiusDp = 8f, alpha = { 0.12f }),
            shape = shapeControl,
            consumesSharedCapture = true
        )

        GlassLevel.L3_Dialog -> GlassSpec(
            level = level,
            fill = { l3Fill },
            blurRadiusDp = 11f,
            lens = GlassLens.Full,
            lensSpec = GlassLensSpec(heightDp = 24f, amountDp = 48f, depth = true, chromaticAberration = true),
            highlight = GlassHighlightSpec(widthDp = 0.5f, falloff = 1f, alpha = { 0.55f }, style = HighlightStyle.Default),
            innerShadow = GlassInnerShadowSpec(radiusDp = 2f, alpha = { 0.18f }),
            shadow = GlassShadowSpec(radiusDp = 24f, alpha = { 0.26f }),
            shape = shapeDialog,
            consumesSharedCapture = true
        )

        GlassLevel.L4_Nav -> GlassSpec(
            level = level,
            fill = { navFill },
            blurRadiusDp = 11f,
            lens = GlassLens.Full,
            lensSpec = GlassLensSpec(heightDp = 24f, amountDp = 24f, depth = true, chromaticAberration = true),
            highlight = GlassHighlightSpec(widthDp = 0.5f, falloff = 1f, alpha = { 0.30f }, style = HighlightStyle.Default),
            innerShadow = GlassInnerShadowSpec(radiusDp = 8f, alpha = { 0.00f }),
            shadow = GlassShadowSpec(radiusDp = 22f, alpha = { 0.14f }),
            shape = shapeNav,
            consumesSharedCapture = true
        )
    }
}
