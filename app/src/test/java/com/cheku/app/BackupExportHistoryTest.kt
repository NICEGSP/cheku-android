package com.cheku.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupExportHistoryTest {
    @Test
    fun encodeDecodeRoundTripsEveryField() {
        val records = listOf(
            BackupExportRecord(at = 1_774_000_000_000L, fileName = "CheKu_用车记录_20260920_1751.che", bytes = 12_345_678L, scope = "车辆与行驶、能源"),
            BackupExportRecord(at = 1_774_000_001_000L, fileName = "CheKu_人车证书_20260920_1751.cert.che", bytes = 80_000_000L, scope = "证书 10 项"),
        )
        assertEquals(records, decodeExportHistory(encodeExportHistory(records)))
    }

    @Test
    fun decodeOfBlankOrGarbageIsEmptyNotCrash() {
        assertEquals(emptyList<BackupExportRecord>(), decodeExportHistory(null))
        assertEquals(emptyList<BackupExportRecord>(), decodeExportHistory(""))
        assertEquals(emptyList<BackupExportRecord>(), decodeExportHistory("   "))
    }

    @Test
    fun oneCorruptRecordDoesNotPoisonTheRest() {
        val good = BackupExportRecord(1_774_000_000_000L, "a.che", 100L, "能源")
        val corrupt = "not-a-number\u00012\u0001b.che\u0001检修"
        val raw = encodeExportHistory(listOf(good)) + '\u0002' + corrupt

        val decoded = decodeExportHistory(raw)
        assertEquals("坏记录应被丢弃、好记录必须还在", 1, decoded.size)
        assertEquals(good, decoded.single())
    }

    @Test
    fun separatorInsideTextDoesNotSplitTheRecord() {
        val tricky = BackupExportRecord(
            at = 1L, bytes = 2L,
            fileName = "weird\u0001name\u0002.che",
            scope = "含\u0001分隔符"
        )
        val decoded = decodeExportHistory(encodeExportHistory(listOf(tricky)))
        assertEquals("必须仍是恰好一条", 1, decoded.size)
        assertEquals(1L, decoded.single().at)
        assertEquals(2L, decoded.single().bytes)
        // 分隔符被替换成下划线，但其余内容保留
        assertTrue(decoded.single().fileName.contains("weird"))
        assertTrue(decoded.single().fileName.endsWith(".che"))
    }

    @Test
    fun historyIsCappedAtTwentyAndNewestComesFirst() {
        var history = emptyList<BackupExportRecord>()
        val total = EXPORT_HISTORY_LIMIT + 5
        repeat(total) { index ->
            history = appendExportHistory(
                history,
                BackupExportRecord(at = index.toLong(), fileName = "f$index.che", bytes = 1L, scope = "s")
            )
        }

        assertEquals("条目数必须收在上限内", EXPORT_HISTORY_LIMIT, history.size)
        assertEquals("最新的必须在最前", (total - 1).toLong(), history.first().at)
        assertTrue("最旧的应已被挤出", history.none { it.at < (total - EXPORT_HISTORY_LIMIT).toLong() })
        // 编码后再解码，条数与顺序都不变
        assertEquals(history, decodeExportHistory(encodeExportHistory(history)))
    }
}
