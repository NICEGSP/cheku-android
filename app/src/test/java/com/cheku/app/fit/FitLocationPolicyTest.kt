package com.cheku.app.fit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FitLocationPolicyTest {
    @Test
    fun stationaryMovementInsideAccuracyRadiusDoesNotAddDistance() {
        assertEquals(
            0.0,
            FitLocationPolicy.effectiveDistanceMeters(8.0, 0.1, 6f, 5f),
            0.001
        )
    }

    @Test
    fun realMovementStillAddsDistance() {
        assertEquals(
            20.0,
            FitLocationPolicy.effectiveDistanceMeters(20.0, 3.0, 6f, 5f),
            0.001
        )
    }

    @Test
    fun networkFixIsIgnoredWhileGpsIsFresh() {
        assertTrue(FitLocationPolicy.shouldUseFix(isGps = true, nowElapsedMs = 10_000L, lastGpsElapsedMs = 10_000L))
        assertFalse(FitLocationPolicy.shouldUseFix(isGps = false, nowElapsedMs = 11_000L, lastGpsElapsedMs = 10_000L))
        // 恰好等于门限仍算"新鲜"
        assertFalse(
            FitLocationPolicy.shouldUseFix(
                isGps = false,
                nowElapsedMs = 10_000L + FitLocationPolicy.GPS_FRESH_MS,
                lastGpsElapsedMs = 10_000L
            )
        )
    }

    @Test
    fun networkFixIsUsedWhenGpsIsStaleOrNeverSeen() {
        assertTrue(FitLocationPolicy.shouldUseFix(isGps = false, nowElapsedMs = 10_000L, lastGpsElapsedMs = null))
        assertTrue(
            FitLocationPolicy.shouldUseFix(
                isGps = false,
                nowElapsedMs = 10_001L + FitLocationPolicy.GPS_FRESH_MS,
                lastGpsElapsedMs = 10_000L
            )
        )
    }

    @Test
    fun interleavedNetworkFixesInflateDistanceUnlessFiltered() {
        data class Fix(val isGps: Boolean, val atMs: Long, val northM: Double, val eastM: Double)
        val fixes = buildList {
            for (s in 0..60) {
                add(Fix(true, s * 1_000L, s * 15.0, 0.0))
                if (s % 5 == 2) add(Fix(false, s * 1_000L + 300L, s * 15.0, 40.0))
            }
        }
        fun pathMeters(points: List<Fix>) = points.zipWithNext { a, b ->
            kotlin.math.hypot(b.northM - a.northM, b.eastM - a.eastM)
        }.sum()

        var lastGps: Long? = null
        val kept = fixes.filter { f ->
            if (f.isGps) lastGps = f.atMs
            FitLocationPolicy.shouldUseFix(f.isGps, f.atMs, lastGps)
        }

        assertEquals(900.0, pathMeters(kept), 0.001)
        assertTrue("混用应明显虚高", pathMeters(fixes) > 900.0 * 1.5)
    }

    @Test
    fun inaccurateImpossibleJumpIsRejected() {
        assertTrue(FitLocationPolicy.isImplausibleJump(300.0, 2.0, 15f))
        assertFalse(FitLocationPolicy.isImplausibleJump(30.0, 2.0, 5f))
    }
}
