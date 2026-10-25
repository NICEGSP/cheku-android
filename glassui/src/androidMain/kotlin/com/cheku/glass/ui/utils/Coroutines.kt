/*
 * Derived from Kyant0/AndroidLiquidGlass (https://github.com/Kyant0/AndroidLiquidGlass),
 * licensed under the Apache License, Version 2.0. Modified for CheKu.
 */
package com.cheku.glass.ui.utils

import kotlinx.coroutines.android.awaitFrame

actual suspend fun awaitFrame() {
    awaitFrame()
}
