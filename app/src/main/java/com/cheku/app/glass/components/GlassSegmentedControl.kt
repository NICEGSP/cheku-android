package com.cheku.app.glass.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.cheku.app.glass.GlassMotion
import com.cheku.app.glass.GlassTokens
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.roundToInt

@Composable
fun GlassSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    accent: Color,
    selectedTextColor: Color,
    unselectedTextColor: Color,
    modifier: Modifier = Modifier,
    barHeight: Dp = 40.dp,
    fontSize: TextUnit = 13.sp
) {
    if (options.isEmpty()) return
    val safeIndex = selectedIndex.coerceIn(0, options.lastIndex)
    val shape = GlassTokens.shapeCapsule

    var pressedIndex by remember { mutableStateOf(-1) }

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val segmentWidthPx = constraints.maxWidth.toFloat() / options.size
        val capsuleOffset = remember { Animatable(safeIndex.toFloat()) }
        LaunchedEffect(safeIndex) {
            capsuleOffset.animateTo(safeIndex.toFloat(), GlassMotion.selectionSpec())
        }

        // 1. 玻璃轨道（L2 玻璃面；只做 vibrancy + 轻模糊 + 弱透镜，不折射内容层）
        Box(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(shape)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        // S1_Control 层统一磨砂
                        blur(GlassTokens.controlBlurDp.dp.toPx())
                        lens(12f.dp.toPx(), 24f.dp.toPx())
                    }
                )
        )

        LiquidGlassPill(
            backdrop = backdrop,
            shape = shape,
            pressProgress = {
                val p = if (pressedIndex == safeIndex) 1f else 0f
                p
            },
            modifier = Modifier
                .offset { IntOffset((capsuleOffset.value * segmentWidthPx).roundToInt(), 0) }
                .fillMaxWidth(1f / options.size)
                .height(barHeight)
                .padding(2.dp)
        ) {}

        // 3. 内容层（最上；不参与 lens 采样）
        Row(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
        ) {
            options.forEachIndexed { index, label ->
                val interaction = remember { MutableInteractionSource() }
                val isPressed by interaction.collectIsPressedAsState()
                LaunchedEffect(isPressed) {
                    pressedIndex = if (isPressed) index else -1
                }
                val selected = index == safeIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = interaction,
                            indication = null
                        ) { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (selected) selectedTextColor else unselectedTextColor,
                        fontSize = fontSize,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
