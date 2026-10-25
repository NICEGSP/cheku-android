package com.cheku.app.carmode

import android.content.Context
import android.provider.Settings
import kotlin.math.roundToInt

/** 屏幕亮度 */

internal const val CAR_MIN_BRIGHTNESS = 0.01f

private const val BRIGHTNESS_SCALE = 255f

/** 能不能写系统设置 */
internal fun canWriteSystemBrightness(context: Context): Boolean = runCatching {
    Settings.System.canWrite(context)
}.getOrDefault(false)

/** 系统亮度 */
internal fun systemBrightnessFraction(context: Context): Float = runCatching {
    val raw = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    (raw / BRIGHTNESS_SCALE).coerceIn(CAR_MIN_BRIGHTNESS, 1f)
}.getOrDefault(0.6f)

internal fun isSystemBrightnessAutomatic(context: Context): Boolean = runCatching {
    Settings.System.getInt(
        context.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS_MODE
    ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
}.getOrDefault(false)

internal fun writeSystemBrightness(context: Context, fraction: Float): Boolean = runCatching {
    val resolver = context.contentResolver
    Settings.System.putInt(
        resolver,
        Settings.System.SCREEN_BRIGHTNESS_MODE,
        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
    )
    Settings.System.putInt(
        resolver,
        Settings.System.SCREEN_BRIGHTNESS,
        (fraction.coerceIn(0f, 1f) * BRIGHTNESS_SCALE).roundToInt()
    )
}.isSuccess
