package com.cheku.app.glass.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.Backdrop
import com.cheku.app.NAV_FADE_IN_MS
import com.cheku.app.NAV_FADE_OUT_MS
import com.cheku.app.glass.GlassFallback
import com.cheku.app.glass.GlassLevel
import com.cheku.app.glass.GlassTokens
import com.cheku.app.glass.LocalGlassBackdrops
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.cheku.app.glass.glassThemeColor
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

@Composable
fun LiquidGlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: @Composable ColumnScope.() -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    dismissible: Boolean = true
) {
    val sharedBackdrop = LocalGlassBackdrops.current?.background
    val hasCapture = sharedBackdrop != null
    val backdrop: Backdrop = sharedBackdrop ?: emptyBackdrop()

    if (!hasCapture) {
        GlassFallback.reportMaterialFallback(
            material = "L3_Dialog",
            message = "no shared capture in LocalGlassBackdrops; degrading to wash + shadow"
        )
    }

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val requestClose: () -> Unit = remember { { shown = false } }
    // 遮罩只做透明度：它铺满整屏，若跟着面板一起缩放，屏幕上会看到一圈随动画移动的边。
    val scrimAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(
            if (shown) NAV_FADE_IN_MS else NAV_FADE_OUT_MS,
            easing = FastOutSlowInEasing
        ),
        label = "dialog-scrim"
    )

    Dialog(
        onDismissRequest = { if (dismissible) requestClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible
        )
    ) {
        val spec = GlassTokens.specFor(GlassLevel.L3_Dialog)
        val fill = spec.fill()
        val isLightTheme = !isSystemInDarkTheme()
        val dimColor =
            if (isLightTheme) Color(0xFF29293A).copy(0.23f)
            else Color(0xFF121212).copy(0.56f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = scrimAlpha }
                .background(dimColor)
                .pointerInput(requestClose, dismissible) {
                    if (dismissible) detectTapGestures { requestClose() }
                },
            contentAlignment = Alignment.Center
        ) {
            GlassDialogMotionHost(shown = shown, onExited = onDismissRequest) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    // Swallow taps on the panel so they do not reach the dismissing scrim.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { GlassTokens.shapeDialog },
                        effects = {
                            if (hasCapture) {
                                vibrancy()
                                colorControls(
                                    brightness = if (isLightTheme) 0.2f else 0f,
                                    saturation = 1.5f
                                )
                                blur((if (isLightTheme) spec.blurRadiusDp else spec.blurRadiusDp / 2f).dp.toPx())
                                applyGlassLens(spec, 0f)
                            }
                        },
                        highlight = { Highlight.Plain },
                        shadow = { glassShadowOf(spec, 0f) },
                        innerShadow = { glassInnerShadowOf(spec, 0f) },
                        onDrawSurface = { drawRect(fill) }
                    )
                    .padding(20.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isLightTheme) Color.Black else Color.White
                )
                Spacer(Modifier.height(12.dp))
                text()
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reference order: dismiss ("Cancel") on the left, confirm ("Okay") on the right.
                    dismissButton()
                    confirmButton()
                }
            }
            }   // AnimatedVisibility（面板出入场）
        }
    }
}

/** Reference-shaped convenience dialog */
@Composable
fun LiquidGlassConfirmDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    title: String,
    text: @Composable ColumnScope.() -> Unit,
    confirmText: String = "Okay",
    dismissText: String = "Cancel"
) {
    val accent = glassThemeColor(Color(0xFF0088FF), Color(0xFF0091FF))

    LiquidGlassDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        text = text,
        dismissButton = {
            val isLightTheme = !isSystemInDarkTheme()
            Row(
                modifier = Modifier
                    .glassPressFeedback(onClick = onDismissRequest)
                    .clip(GlassTokens.shapeCapsule)
                    .background(GlassTokens.l3Fill.copy(alpha = 0.55f))
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    dismissText,
                    fontSize = 16.sp,
                    color = if (isLightTheme) Color.Black else Color.White
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier
                    .glassPressFeedback(onClick = onConfirm)
                    .clip(GlassTokens.shapeCapsule)
                    .background(accent)
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(confirmText, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    )
}

@Composable
fun LiquidGlassDialogButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit
) = com.cheku.glass.ui.components.LiquidButton(
    onClick = onClick,
    backdrop = backdrop,
    modifier = modifier,
    tint = tint,
    surfaceColor = surfaceColor,
    content = content
)
