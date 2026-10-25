package com.cheku.app

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackInputTest {
    @Test
    fun multilineKeepsLineBreaksButStripsControlCharacters() {
        assertEquals(
            "第一行\n第二行",
            sanitizeMultilineInput("第一行\r\n第二行", 1000)
        )
        // 裸 \r（老式换行）也归一为 \n，否则 Compose 里会显示成一个方块。
        assertEquals("A\nB", sanitizeMultilineInput("A\rB", 1000))
        // 制表符要清掉（后台按纯文本存储展示，制表符只会变成一段空白）。
        assertEquals("AB", sanitizeMultilineInput("A\tB", 1000))
        assertEquals("[31m红", sanitizeMultilineInput("\u001B[31m红", 1000))
    }

    @Test
    fun multilineIsBoundedByMaxChars() {
        assertEquals(1000, sanitizeMultilineInput("x".repeat(5000), 1000).length)
        // 自定义上限同样生效（不是写死 1000）。
        assertEquals(20, sanitizeMultilineInput("x".repeat(500), 20).length)
    }

    @Test
    fun dayKeyUsesLocalCalendarSoMorningIsNotYesterday() {
        assertEquals(feedbackDayKey(at(2026, 3, 1, 0, 5)), feedbackDayKey(at(2026, 3, 1, 23, 30)))
        assertNotEquals(feedbackDayKey(at(2026, 3, 1, 23, 30)), feedbackDayKey(at(2026, 3, 2, 0, 30)))
        // 跨月、跨年都要变。
        assertNotEquals(feedbackDayKey(at(2026, 3, 31, 12, 0)), feedbackDayKey(at(2026, 4, 1, 12, 0)))
        assertNotEquals(feedbackDayKey(at(2026, 12, 31, 12, 0)), feedbackDayKey(at(2027, 1, 1, 12, 0)))
        assertTrue(feedbackDayKey(at(2026, 3, 1, 12, 0)).matches(Regex("""\d{4}-\d{2}-\d{2}""")))
    }

    @Test
    fun isSubmittedOnOnlyCountsTheSameDayAndIgnoresNeverSubmitted() {
        val yesterday = at(2026, 3, 1, 22, 0)
        val today = at(2026, 3, 2, 9, 0)

        assertFalse(isSubmittedOn(0L, today))
        assertFalse(isSubmittedOn(yesterday, today))
        assertTrue(isSubmittedOn(today, today))
        assertTrue(isSubmittedOn(at(2026, 3, 2, 0, 1), today))
        // 负数/异常值不能被当成"提交过"（否则一个脏值就把入口永久锁死）。
        assertFalse(isSubmittedOn(-1L, today))
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis
}
