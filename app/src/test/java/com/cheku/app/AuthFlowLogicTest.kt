package com.cheku.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthFlowLogicTest {
    @Test
    fun passwordNeedsLetterAndDigit() {
        assertFalse("纯数字不合格", isStrongEnoughPassword("12345678"))
        assertFalse("纯字母不合格", isStrongEnoughPassword("abcdefgh"))
        assertFalse("长度不足不合格", isStrongEnoughPassword("a1b2c3"))
        assertTrue("字母+数字且 ≥8 位合格", isStrongEnoughPassword("a1b2c3d4"))
    }

    @Test
    fun submitRegisterRequiresCodeLengthAndMatchingPassword() {
        assertTrue(canSubmitRegister("123456", "a1b2c3d4", "a1b2c3d4"))
        assertFalse("验证码位数不对", canSubmitRegister("12345", "a1b2c3d4", "a1b2c3d4"))
        assertFalse("两次不一致", canSubmitRegister("123456", "a1b2c3d4", "a1b2c3d5"))
        assertFalse("弱密码", canSubmitRegister("123456", "12345678", "12345678"))
    }

    @Test
    fun rateLimitSecondsLeftHandlesExpiredAndActive() {
        val now = 1_000_000L
        assertEquals("未限流返回 0", 0, rateLimitSecondsLeft(0L, now))
        assertEquals("已过期返回 0", 0, rateLimitSecondsLeft(now - 5_000L, now))
        assertEquals("剩余 42 秒", 42, rateLimitSecondsLeft(now + 42_000L, now))
        assertEquals("不足 1 秒也归 0", 0, rateLimitSecondsLeft(now + 400L, now))
    }

    @Test
    fun retryWaitTextUsesMinutesAboveOneMinute() {
        assertEquals("42 秒", retryWaitText(42))
        assertEquals("59 秒", retryWaitText(59))
        assertEquals("1 分钟", retryWaitText(60))
        assertEquals("3 分钟", retryWaitText(150))
    }

    @Test
    fun retryAfterSecondsPrefersRetryAfterSecondsField() {
        assertEquals(42L, retryAfterSecondsOf("""{"error":"too many","retryAfterSeconds":42}"""))
    }

    @Test
    fun retryAfterSecondsFallsBackToRetryAfterField() {
        assertEquals(1800L, retryAfterSecondsOf("""{"error":"too many","retryAfter":1800}"""))
    }

    @Test
    fun retryAfterSecondsIsZeroWhenAbsentOrInvalid() {
        assertEquals(0L, retryAfterSecondsOf("""{"error":"too many"}"""))
        assertEquals(0L, retryAfterSecondsOf("not json"))
        assertEquals(0L, retryAfterSecondsOf(""))
    }

    @Test
    fun mapAccountError429CarriesSecondsInMessage() {
        val error = mapAccountError(429, """{"error":"too many","retryAfterSeconds":42}""")
        assertTrue("429 可重试", error.retryable)
        assertTrue("文案应含剩余秒数：${error.userMessage}", error.userMessage.contains("42"))
    }
}
