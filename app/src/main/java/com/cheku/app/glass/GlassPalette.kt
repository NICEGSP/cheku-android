package com.cheku.app.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop

/** Animated theme level */
val LocalAppDarkLevel = staticCompositionLocalOf { 0f }

@Immutable
data class GlassBackdrops(
    val background: LayerBackdrop,
    val dock: LayerBackdrop? = null
)

val LocalGlassBackdrops = staticCompositionLocalOf<GlassBackdrops?> { null }

val LocalCardBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

@Composable
fun glassThemeColor(light: Color, dark: Color): Color =
    androidx.compose.ui.graphics.lerp(light, dark, LocalAppDarkLevel.current)

@Composable
fun rememberLayerAwareBackdrop(): com.kyant.backdrop.Backdrop {
    val page = LocalGlassBackdrops.current?.background
    val card = LocalCardBackdrop.current
    return when {
        page != null && card != null -> rememberCombinedBackdrop(page, card)
        page != null -> page
        card != null -> card
        else -> emptyBackdrop()
    }
}
