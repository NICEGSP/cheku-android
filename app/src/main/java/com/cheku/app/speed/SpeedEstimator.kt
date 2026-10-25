package com.cheku.app.speed

object SpeedEstimator {
    data class Result(
        val speedMps: Double,
        val outlier: Boolean
    )

    fun update(
        rawSpeedMps: Double,
        previousSpeedMps: Double?,
        dtSeconds: Double,
        speedAccuracyMps: Double? = null,
        locationAccuracyM: Float = 0f
    ): Result {
        if (!rawSpeedMps.isFinite() || rawSpeedMps < 0.0) {
            return Result(previousSpeedMps?.coerceAtLeast(0.0) ?: 0.0, true)
        }
        if (previousSpeedMps == null) return Result(rawSpeedMps, false)
        if (dtSeconds <= 0.0) return Result(previousSpeedMps, true)

        val maxDelta = maxOf(8.0 * dtSeconds, 2.5)
        if (kotlin.math.abs(rawSpeedMps - previousSpeedMps) > maxDelta && locationAccuracyM > 25f) {
            return Result(previousSpeedMps, true)
        }

        val alpha = if ((speedAccuracyMps ?: 0.0) > 1.5 || locationAccuracyM > 15f) 0.82 else 0.92
        return Result(alpha * rawSpeedMps + (1.0 - alpha) * previousSpeedMps, false)
    }
}
