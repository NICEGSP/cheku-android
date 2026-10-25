/*
 * Derived from Kyant0/AndroidLiquidGlass (https://github.com/Kyant0/AndroidLiquidGlass),
 * licensed under the Apache License, Version 2.0. Modified for CheKu.
 */
package com.cheku.glass.ui.utils

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

fun interface ProgressConverter {
    fun convert(progress: Float): Float

    companion object {
        val Default: ProgressConverter =
            ProgressConverter { progress ->
                (1f - exp(-abs(progress))) * progress.sign
            }
    }
}
