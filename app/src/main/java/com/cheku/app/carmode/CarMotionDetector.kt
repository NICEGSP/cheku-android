package com.cheku.app.carmode

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock

/**
 * 用加速度计和陀螺仪判断手机是否静止（放在桌上、车停稳）。
 *
 * 室内 GPS 精度只有几十米，坐标和多普勒速度都会漂，单看定位分不清"没动"和"在慢慢开"。
 * 传感器能直接回答这个问题；判定规则见 [isAccelMotion] / [isGyroMotion] / [isDeviceStill]。
 * 设备没有这两个传感器时 [isStill] 恒为 false，车速只按定位质量处理。
 */
internal class CarMotionDetector(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private var lastMotionMs = SystemClock.elapsedRealtime()
    private var registered = false
    private val lastAccel = FloatArray(3)
    private var hasLastAccel = false

    val isStill: Boolean
        get() = registered && isDeviceStill(SystemClock.elapsedRealtime(), lastMotionMs)

    fun start() {
        if (registered || manager == null) return
        lastMotionMs = SystemClock.elapsedRealtime()
        hasLastAccel = false
        for (type in intArrayOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE)) {
            val sensor = manager.getDefaultSensor(type) ?: continue
            if (manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)) registered = true
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { manager?.unregisterListener(this) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        if (v.size < 3) return
        val moving = when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> isAccelMotion(v[0], v[1], v[2], lastAccel.takeIf { hasLastAccel })
                .also {
                    v.copyInto(lastAccel, endIndex = 3)
                    hasLastAccel = true
                }
            Sensor.TYPE_GYROSCOPE -> isGyroMotion(v[0], v[1], v[2])
            else -> false
        }
        if (moving) lastMotionMs = SystemClock.elapsedRealtime()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
