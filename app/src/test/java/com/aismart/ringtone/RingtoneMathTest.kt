package com.aismart.ringtone

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class RingtoneMathTest {

    @Test
    fun formatTime_formatsMinutesSecondsAndTenths() {
        assertEquals("0:00.0", RingtoneMath.formatTime(0))
        assertEquals("1:05.5", RingtoneMath.formatTime(65_500))
        assertEquals("0:00.0", RingtoneMath.formatTime(-10))
    }

    @Test
    fun formatSize_usesKbBelowOneMegabyte() {
        assertEquals("512 KB", RingtoneMath.formatSize(512L * 1024, Locale.ROOT))
        assertEquals("2.00 MB", RingtoneMath.formatSize(2L * 1024 * 1024, Locale.ROOT))
    }

    @Test
    fun estimateSize_matchesBitrateTimesDuration() {
        // 128 kbps = 16000 byte/s, 30 s -> 480000 byte
        assertEquals(480_000L, RingtoneMath.estimateSizeBytes(128, 30_000))
        assertEquals(0L, RingtoneMath.estimateSizeBytes(128, -5))
    }

    @Test
    fun safeTitle_removesExtensionAndIllegalCharacters() {
        assertEquals("My_Song", RingtoneMath.safeTitle("My:Song.mp3"))
        assertEquals("a_b", RingtoneMath.safeTitle("a/b.m4a"))
        assertEquals("ringtone", RingtoneMath.safeTitle(null))
        assertEquals("ringtone", RingtoneMath.safeTitle("   "))
    }

    @Test
    fun gainAt_fadeInHalfway() {
        // 1000 Hz, khung 500 -> 0.5 s, fade in 1 s -> 0.5
        val gain = RingtoneMath.gainAt(500, 1000, 10_000_000, 1_000_000, 0, 1f)
        assertEquals(0.5f, gain, 0.001f)
    }

    @Test
    fun gainAt_fadeOutHalfway() {
        // tổng 10 s, fade out 2 s, tại 9 s còn 1 s -> 0.5
        val gain = RingtoneMath.gainAt(9000, 1000, 10_000_000, 0, 2_000_000, 1f)
        assertEquals(0.5f, gain, 0.001f)
    }

    @Test
    fun gainAt_appliesBaseGainWithoutFades() {
        val gain = RingtoneMath.gainAt(5000, 1000, 10_000_000, 0, 0, 1.5f)
        assertEquals(1.5f, gain, 0.001f)
    }

    @Test
    fun smartPick_findsLoudestWindow() {
        val profile = listOf(0f, 0f, 1f, 1f, 0f)
        assertEquals(2, RingtoneMath.smartPickStartBucket(profile, 2))
    }

    @Test
    fun smartPick_windowLargerThanProfileStartsAtZero() {
        assertEquals(0, RingtoneMath.smartPickStartBucket(listOf(1f, 2f), 5))
        assertEquals(0, RingtoneMath.smartPickStartBucket(emptyList(), 3))
    }

    @Test
    fun clampRange_limitsToDuration() {
        val range = RingtoneMath.clampRange(0, -500, 99_000, 20_000)
        assertEquals(0L, range.startMs)
        assertEquals(20_000L, range.endMs)
    }

    @Test
    fun clampRange_capsMaxLengthKeepingMovedStart() {
        val range = RingtoneMath.clampRange(0, 10_000, 600_000, 700_000)
        assertEquals(10_000L, range.startMs)
        assertEquals(10_000L + RingtoneMath.MAX_CLIP_MS, range.endMs)
    }

    @Test
    fun clampRange_enforcesMinLength() {
        val range = RingtoneMath.clampRange(5_000, 5_000, 5_100, 60_000)
        assertEquals(5_000L, range.startMs)
        assertEquals(5_000L + RingtoneMath.MIN_CLIP_MS, range.endMs)
    }
}
