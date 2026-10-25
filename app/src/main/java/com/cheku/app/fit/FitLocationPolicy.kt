package com.cheku.app.fit

import kotlin.math.max

object FitLocationPolicy {
    /** GPS 在这么久之内来过定位点，就不采用网络定位点 */
    const val GPS_FRESH_MS = 3_000L

    fun shouldUseFix(isGps: Boolean, nowElapsedMs: Long, lastGpsElapsedMs: Long?): Boolean =
        isGps || lastGpsElapsedMs == null || nowElapsedMs - lastGpsElapsedMs > GPS_FRESH_MS

    fun effectiveDistanceMeters(
        rawDistanceMeters: Double,
        speedMps: Double,
        currentAccuracyMeters: Float,
        previousAccuracyMeters: Float?
    ): Double {
        val noiseRadius = max(currentAccuracyMeters, previousAccuracyMeters ?: currentAccuracyMeters) * 1.5
        return if (speedMps < 0.5 && rawDistanceMeters <= noiseRadius) 0.0 else rawDistanceMeters
    }

    fun isImplausibleJump(
        distanceMeters: Double,
        elapsedSeconds: Double,
        accuracyMeters: Float
    ): Boolean = elapsedSeconds > 0.0 && distanceMeters / elapsedSeconds > 100.0 && accuracyMeters > 10f
}
