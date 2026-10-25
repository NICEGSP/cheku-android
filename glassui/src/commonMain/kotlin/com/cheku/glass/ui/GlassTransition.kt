package com.cheku.glass.ui

import androidx.compose.runtime.compositionLocalOf

enum class GlassTransitionTier {
    /** 非转场 */
    None,

    /** 进场页 */
    Entering,

    /** 退场页 */
    Exiting,
}

/** 当前生效的转场降级档位 */
val LocalGlassTransitionTier = compositionLocalOf { GlassTransitionTier.None }
