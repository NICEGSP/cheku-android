package com.cheku.app.carmode

import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** 地球平均半径，WGS-84 平均值 */
private const val EARTH_RADIUS_METERS = 6_371_008.8

internal fun mpsToKmh(mps: Double): Double =
    if (!mps.isFinite() || mps <= 0.0) 0.0 else mps * 3.6

/** 两点间大圆距离 */
internal fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    if (!lat1.isFinite() || !lon1.isFinite() || !lat2.isFinite() || !lon2.isFinite()) return 0.0
    if (abs(lat1) > 90.0 || abs(lat2) > 90.0) return 0.0
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val halfPhi = (phi2 - phi1) / 2.0
    val halfLambda = Math.toRadians(lon2 - lon1) / 2.0
    val sinHalfPhi = sin(halfPhi)
    val sinHalfLambda = sin(halfLambda)
    val h = sinHalfPhi * sinHalfPhi + cos(phi1) * cos(phi2) * sinHalfLambda * sinHalfLambda
    return 2.0 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(h)))
}

internal fun shouldAccumulateSegment(
    meters: Double,
    dtSeconds: Double,
    accuracyMeters: Float,
    /** 单段位移下限 */
    minSegmentMeters: Double = 8.0,
    /** 定位精度上限 */
    maxAccuracyMeters: Float = 50f
): Boolean {
    // 距离：非有限值直接拒；低于下限视为抖动（含边界，恰好等于下限算通过）。
    if (!meters.isFinite() || meters < minSegmentMeters) return false
    // 时间：非正或 NaN 说明这一段不是"走过去的"（同一帧重复 / 时钟回拨）。
    if (!dtSeconds.isFinite() || dtSeconds <= 0.0) return false
    // 精度：超过上限不计入。0（未知精度）刻意放行，见 KDoc。
    if (accuracyMeters > maxAccuracyMeters) return false
    return true
}

internal class TripAccumulator(
    /** 判定漂移的比值下限 */
    private val minDirectness: Double = 0.35,
    /** 滑动窗口长度 */
    private val windowSeconds: Double = 60.0
) {
    /** 窗口内每个采样点 */
    private val window = ArrayDeque<Triple<Double, Double, Double>>()

    var meters: Double = 0.0
        private set

    fun add(
        timeSeconds: Double,
        lat: Double,
        lon: Double,
        segmentMeters: Double,
        dtSeconds: Double = 1.0,
        accuracyMeters: Float = 0f
    ): Boolean {
        if (!lat.isFinite() || !lon.isFinite() || !segmentMeters.isFinite()) return false
        if (!shouldAccumulateSegment(segmentMeters, dtSeconds, accuracyMeters)) return false
        window.addLast(Triple(timeSeconds, lat, lon))

        // 滑出窗口外的点
        while (window.size >= 2 && timeSeconds - window.first().first > windowSeconds) {
            window.removeFirst()
        }
        if (window.size < 3) return false

        val first = window.first()
        val last = window.last()
        val net = haversineMeters(first.second, first.third, last.second, last.third)
        var path = 0.0
        // 累计：窗口内相邻点的直线距离之和
        var prev = first
        for (i in 1 until window.size) {
            val cur = window[i]
            path += haversineMeters(prev.second, prev.third, cur.second, cur.third)
            prev = cur
        }
        if (path <= 1.0) return false   // 窗口内几乎没动，交给 minSegmentMeters 门

        val directness = net / path
        if (directness < minDirectness) return false

        meters += segmentMeters
        return true
    }

    /** 清空 */
    fun reset() {
        window.clear()
        meters = 0.0
    }
}

/** 上一帧车速高于它时，单帧突降到 0 先视为可疑 */
internal const val SUSPECT_ZERO_MIN_PREVIOUS_MPS = 3.0

internal fun isSuspectZeroSpeed(rawMps: Double, previousMps: Double?, alreadyHeld: Boolean): Boolean =
    !alreadyHeld && rawMps <= 0.0 && (previousMps ?: 0.0) > SUSPECT_ZERO_MIN_PREVIOUS_MPS

/** 加速度模长偏离重力加速度超过它（m/s²）即算一次"在动"。 */
internal const val MOTION_ACCEL_THRESHOLD = 0.2

/** 角速度模长超过它（rad/s）即算一次"在动"。 */
internal const val MOTION_GYRO_THRESHOLD = 0.08

/** 连续这么久（毫秒）没有任何"在动"的采样，才判定手机静止。 */
internal const val STILL_AFTER_MS = 5_000L

/** 车速低于它（米/秒，约 3 km/h）按 0 显示：GPS 在静止时也会给出零点几米每秒的噪声速度。 */
internal const val SPEED_DEADBAND_MPS = 0.8

/** 速度小于"速度误差 × 该倍数"时，与 0 区分不开。 */
internal const val SPEED_SIGNIFICANCE = 1.5

/** 手机静止时，定位精度差于它（米）就不再相信 GPS 给出的速度。 */
internal const val STILL_TRUSTED_ACCURACY_M = 20f

private const val GRAVITY = 9.81

/** 相邻两次加速度采样的矢量差超过它（m/s²）即算一次"在动"。 */
internal const val MOTION_ACCEL_DELTA_THRESHOLD = 0.25

/**
 * 加速度采样是否表明手机在动：模长明显偏离重力加速度（加减速），
 * 或与上一次采样相比矢量变化明显（震动、颠簸、转动）。
 * 只看模长不够：侧向 0.5 m/s² 叠加在重力上，模长只变化约 0.01。
 */
internal fun isAccelMotion(x: Float, y: Float, z: Float, previous: FloatArray? = null): Boolean {
    val magnitude = sqrt((x * x + y * y + z * z).toDouble())
    if (!magnitude.isFinite()) return false
    if (abs(magnitude - GRAVITY) > MOTION_ACCEL_THRESHOLD) return true
    if (previous == null || previous.size < 3) return false
    val dx = (x - previous[0]).toDouble()
    val dy = (y - previous[1]).toDouble()
    val dz = (z - previous[2]).toDouble()
    return sqrt(dx * dx + dy * dy + dz * dz) > MOTION_ACCEL_DELTA_THRESHOLD
}

internal fun isGyroMotion(x: Float, y: Float, z: Float): Boolean {
    val magnitude = sqrt((x * x + y * y + z * z).toDouble())
    return magnitude.isFinite() && magnitude > MOTION_GYRO_THRESHOLD
}

internal fun isDeviceStill(nowMs: Long, lastMotionMs: Long): Boolean = nowMs - lastMotionMs >= STILL_AFTER_MS

/**
 * 不带多普勒速度的定位点，用同一来源相邻两点的位移推算车速。
 *
 * 位移没有超出两点的定位精度时，它只是坐标漂移，不是车在走，返回 0。
 * 室内 GPS 精度 50~70 米，每秒漂十几米很常见，直接相除就是几十 km/h。
 * 时间差非正时无法推算，返回 null。
 */
internal fun derivedSpeedMps(
    segmentMeters: Double,
    dtSeconds: Double,
    accuracyM: Float,
    previousAccuracyM: Float
): Double? {
    if (!segmentMeters.isFinite() || !dtSeconds.isFinite() || dtSeconds <= 0.0) return null
    val noiseMeters = maxOf(accuracyM, previousAccuracyM).toDouble()
    return if (segmentMeters <= noiseMeters) 0.0 else segmentMeters / dtSeconds
}

/**
 * 这一帧是否应当按"静止"处理：车速显示 0，本次里程不累加。
 *
 * - 速度低于 [SPEED_DEADBAND_MPS]；
 * - 速度小于自身误差的 [SPEED_SIGNIFICANCE] 倍（误差未知时跳过这一条）；
 * - 传感器判定手机静止，且定位精度未知或差于 [STILL_TRUSTED_ACCURACY_M]。
 *   精度好的时候仍然相信 GPS，避免平稳行驶时被误判为静止。
 */
internal fun isLikelyStationary(
    rawMps: Double,
    accuracyM: Float,
    speedAccuracyMps: Double?,
    deviceStill: Boolean
): Boolean {
    if (!rawMps.isFinite() || rawMps < SPEED_DEADBAND_MPS) return true
    if (speedAccuracyMps != null && speedAccuracyMps > 0.0 && rawMps < speedAccuracyMps * SPEED_SIGNIFICANCE) return true
    if (deviceStill && (accuracyM <= 0f || accuracyM > STILL_TRUSTED_ACCURACY_M)) return true
    return false
}

/** 本次行程平均速度 */
internal fun tripAverageSpeedKmh(meters: Double, seconds: Double): Double =
    if (!meters.isFinite() || !seconds.isFinite() || seconds <= 0.0) 0.0
    else mpsToKmh(meters / seconds)

/** 行程时长文案 */
internal fun formatTripDuration(seconds: Double): String {
    val total = if (!seconds.isFinite() || seconds <= 0.0) 0L else seconds.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format(Locale.CHINA, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.CHINA, "%02d:%02d", m, s)
    }
}

/** 24 小时制时钟文案 */
internal fun formatClock(hour: Int, minute: Int): String {
    val h = ((hour % 24) + 24) % 24
    val m = ((minute % 60) + 60) % 60
    return String.format(Locale.CHINA, "%02d:%02d", h, m)
}

/** 车速文案 */
internal fun formatSpeedKmh(kmh: Double?): String =
    if (kmh == null || !kmh.isFinite() || kmh < 0.0) "--" else kmh.roundToInt().toString()

/** 本次行程里程文案 */
internal fun formatTripDistanceKm(meters: Double): String {
    val km = if (!meters.isFinite() || meters <= 0.0) 0.0 else meters / 1000.0
    return String.format(Locale.CHINA, "%.2f km", km)
}
