package com.cheku.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateServiceTest {
    @Test
    fun versionComparisonSupportsPrefixedVersions() {
        assertTrue(isVersionNewer("预览版0.1.1", "0.1.0"))
        assertTrue(isVersionNewer("内测版v0.1.3", "内测版v0.1.2"))
        assertTrue(isVersionNewer("1.10.0", "1.9.9"))
        assertFalse(isVersionNewer("0.1.1", "内测版v0.1.2"))
        assertFalse(isVersionNewer("0.1", "0.1.0"))
        assertFalse(isVersionNewer("测试版", "0.1.0"))
    }

    @Test
    fun releaseDateUsesShanghaiDay() {
        assertEquals("2026-07-14", formatReleaseDay("2026-07-13T16:30:00Z"))
        assertEquals("待发布", formatReleaseDay(null))
    }

    @Test
    fun forceUpdateUsesConfiguredMinimumVersion() {
        assertTrue(isForceUpdateRequired("1.1.0", "1.0.9"))
        assertFalse(isForceUpdateRequired("1.1.0", "1.1.0"))
        assertFalse(isForceUpdateRequired(null, "1.0.0"))
    }
}
