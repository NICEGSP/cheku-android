package com.cheku.app.carmode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarMusicLyricWatchTest {
    @Test
    fun repeatedTitleChangesOnSameTrackUpgradeToLyricMode() {
        val track = "meta:专辑|215000|歌手"
        var watch = advanceLyricWatch(LyricWatch(), track, "歌名")
        assertFalse("刚进场还没证据", watch.isLyricMode)

        watch = advanceLyricWatch(watch, track, "第一行歌词")
        assertEquals(1, watch.flips)
        assertFalse("单次变化不算（可能只是播放器在补全元数据）", watch.isLyricMode)

        watch = advanceLyricWatch(watch, track, "第二行歌词")
        assertEquals(2, watch.flips)
        assertTrue("第 $LYRIC_FLIP_THRESHOLD 次变化即判定为歌词模式", watch.isLyricMode)
    }

    /** 值没变就不计数 */
    @Test
    fun unchangedTitleDoesNotCount() {
        val track = "meta:a|1|b"
        var watch = advanceLyricWatch(LyricWatch(), track, "歌名")
        repeat(5) { watch = advanceLyricWatch(watch, track, "歌名") }
        assertEquals(0, watch.flips)
        assertFalse(watch.isLyricMode)
    }

    /** 换曲必须归零 */
    @Test
    fun trackChangeResetsTheCounter() {
        val first = "meta:专辑一|215000|歌手"
        var watch = advanceLyricWatch(LyricWatch(), first, "A")
        watch = advanceLyricWatch(watch, first, "B")
        watch = advanceLyricWatch(watch, first, "C")
        assertTrue(watch.isLyricMode)

        // 新的一首：计数器从 0 开始（哪怕字段名与刚才完全一样）。
        watch = advanceLyricWatch(watch, "meta:专辑二|300000|歌手", "歌名")
        assertEquals(0, watch.flips)
        assertFalse("换曲后必须重新取证", watch.isLyricMode)
        assertEquals("歌名", watch.lastTitle)
    }

    @Test
    fun withdrawnTitleNeitherCountsNorClearsTheBaseline() {
        val track = "meta:a|1|b"
        var watch = advanceLyricWatch(LyricWatch(), track, "第一行")
        watch = advanceLyricWatch(watch, track, null)
        assertEquals("null 不计", 0, watch.flips)
        assertEquals("基准仍是第一行", "第一行", watch.lastTitle)
        assertFalse(watch.isLyricMode)
    }

    /** 只给 title、没有专辑/时长/歌手的播放器 */
    @Test
    fun titleOnlyKeyStaysOutOfLyricMode() {
        var watch = LyricWatch()
        listOf("歌曲 A", "歌词行 1", "歌词行 2", "歌词行 3").forEach {
            watch = advanceLyricWatch(watch, "title:$it", it)
            assertFalse(watch.isLyricMode)
        }
        assertEquals(0, watch.flips)
    }

    /** 阈值本身是契约的一部分 */
    @Test
    fun thresholdIsTwoChanges() {
        assertEquals(2, LYRIC_FLIP_THRESHOLD)
    }

    @Test
    fun titleDifferingFromDisplayTitleIsImmediateEvidence() {
        val track = "meta:专辑|215000|歌手"
        var watch = advanceLyricWatch(LyricWatch(), track, "歌名")
        assertFalse(watch.isLyricMode)

        watch = advanceLyricWatch(
            prev = watch,
            trackKey = track,
            title = "这一行歌词",
            titleDiffersFromDisplay = true
        )
        assertTrue("TITLE ≠ DISPLAY_TITLE 一次即判定", watch.isLyricMode)
        assertEquals("形状证据不靠 flips 计数", 0, watch.flips)
        assertEquals("这一行歌词", watch.lastTitle)
    }

    /** 进场那一采就同时成立也要判对 */
    @Test
    fun evidencePresentOnFirstSnapshotCounts() {
        val watch = advanceLyricWatch(
            prev = LyricWatch(),
            trackKey = "meta:a|1|b",
            title = "歌词行",
            titleDiffersFromDisplay = true
        )
        assertTrue(watch.isLyricMode)
    }

    @Test
    fun shapeEvidenceSticksWithinTheSameTrack() {
        val track = "meta:a|1|b"
        var watch = advanceLyricWatch(LyricWatch(), track, "歌名")
        watch = advanceLyricWatch(watch, track, "第一行", titleDiffersFromDisplay = true)
        assertTrue(watch.isLyricMode)

        watch = advanceLyricWatch(watch, track, "第二行", titleDiffersFromDisplay = false)
        assertTrue("同一首歌内形状证据必须保持", watch.isLyricMode)
    }

    /** 换曲必须把形状证据一起清掉 */
    @Test
    fun trackChangeClearsShapeEvidence() {
        var watch = advanceLyricWatch(LyricWatch(), "meta:一|1|歌手", "歌词行", titleDiffersFromDisplay = true)
        assertTrue(watch.isLyricMode)

        watch = advanceLyricWatch(watch, "meta:二|2|歌手", "歌名二")
        assertFalse("换曲后必须重新取证", watch.isLyricMode)
    }
}
