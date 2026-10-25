package com.cheku.app.carmode

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

internal data class CarHeading(
    val azimuth: Float?,

    /** 读数是否可信 */
    val reliable: Boolean
)

/** 没有传感器 / 还没出值时的初值 */
internal val CarHeadingUnknown = CarHeading(azimuth = null, reliable = false)

internal fun lerpAngleDegrees(from: Float, to: Float, weight: Float): Float {
    val delta = ((to - from + 540f) % 360f) - 180f
    return from + delta * weight
}

internal fun normalizeDegrees(degrees: Float): Float {
    if (!degrees.isFinite()) return 0f
    val normalized = degrees % 360f
    return if (normalized < 0f) normalized + 360f else normalized
}

/** 低通滤波权重 */
private const val HEADING_FILTER_WEIGHT = 0.22f

internal fun displayRemapAxes(displayRotation: Int): Pair<Int, Int> = when (displayRotation) {
    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y // ROTATION_0（含未知值）
}

private fun currentDisplayRotation(context: Context, view: View?): Int {
    view?.display?.let { return it.rotation }
    @Suppress("DEPRECATION")
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    @Suppress("DEPRECATION")
    return windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
}

@Composable
internal fun rememberCarHeading(): CarHeading {
    val context = LocalContext.current
    val view = LocalView.current
    var heading by remember { mutableStateOf(CarHeadingUnknown) }

    DisposableEffect(context, view) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (manager == null || sensor == null) {
            return@DisposableEffect onDispose { }
        }

        val rotation = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)
        var filtered: Float? = null
        var lastDisplayRotation = Int.MIN_VALUE

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                val displayRotation = currentDisplayRotation(context, view)
                val (axisX, axisY) = displayRemapAxes(displayRotation)
                val remapOk = SensorManager.remapCoordinateSystem(rotation, axisX, axisY, remapped)
                SensorManager.getOrientation(if (remapOk) remapped else rotation, orientation)
                // orientation[0] 就是方位角（弧度，正北为 0、向东为正）。
                val raw = normalizeDegrees(Math.toDegrees(orientation[0].toDouble()).toFloat())
                if (displayRotation != lastDisplayRotation) {
                    // 转屏是"参考系换了"，不是"设备慢慢转过来的"：直接跳过去。
                    // 不重置的话，低通会把这 90° 当成一次真实旋转，之后几百毫秒里指针都在"追"，
                    // 看着像罗盘被转屏甩了一把。
                    lastDisplayRotation = displayRotation
                    filtered = null
                }
                val previous = filtered
                val next = if (previous == null) {
                    raw
                } else {
                    normalizeDegrees(lerpAngleDegrees(previous, raw, HEADING_FILTER_WEIGHT))
                }
                filtered = next
                heading = CarHeading(
                    azimuth = next,
                    // SENSOR_STATUS_UNRELIABLE = -1。其余（含 LOW/MEDIUM/HIGH/NO_CONTACT=0）都当"可用"，
                    // 因为部分 ROM 对 ROTATION_VECTOR 恒报 0，按 0 判死会让罗盘永远不亮。
                    reliable = event.accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { runCatching { manager.unregisterListener(listener) } }
    }

    return heading
}
