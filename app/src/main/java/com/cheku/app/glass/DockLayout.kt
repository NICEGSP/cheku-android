package com.cheku.app.glass

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cheku.app.findActivity
import com.cheku.app.inMultiWindowCompat

/** dock 玻璃容器高度 */
const val DockBarHeightDp: Float = 64f

/** dock 距屏幕底部的常规间距 */
const val DockMarginDp: Float = 18f

/** 多窗口/自由窗口下的间距 */
const val DockMarginMultiWindowDp: Float = 36f

/** 贴底浮层与 dock 顶边之间的呼吸间距 */
val DockFloatGap: Dp = 8.dp

fun dockFloatBottomInset(dockMargin: Dp, gap: Dp = DockFloatGap): Dp =
    dockMargin + DockBarHeightDp.dp + gap

/** dock 当前的底边距 */
@Composable
fun dockBottomMargin(): Dp {
    val activity = LocalContext.current.findActivity()
    return if (activity?.inMultiWindowCompat() == true) DockMarginMultiWindowDp.dp else DockMarginDp.dp
}

/** 把浮层抬到 dock 顶边之上 */
@Composable
fun Modifier.aboveDock(gap: Dp = DockFloatGap): Modifier =
    this
        .navigationBarsPadding()
        .padding(bottom = dockFloatBottomInset(dockBottomMargin(), gap))
