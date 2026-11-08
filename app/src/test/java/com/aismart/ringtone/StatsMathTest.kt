package com.aismart.ringtone

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsMathTest {
    private val now = Instant.parse("2026-11-08T12:00:00Z").toEpochMilli()
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun dailyCounts_groupsByDayOldestFirst() {
        val stamps = listOf(now, now - day, now - day, now - 10 * day, now + day)
        val counts = StatsMath.dailyCounts(stamps, now, ZoneOffset.UTC, 7)
        assertEquals(listOf(0, 0, 0, 0, 0, 2, 1), counts)
    }

    @Test
    fun dailyCounts_emptyListGivesZeros() {
        assertEquals(List(7) { 0 }, StatsMath.dailyCounts(emptyList(), now, ZoneOffset.UTC, 7))
    }

    @Test
    fun dailyCounts_respectsTimeZoneBoundary() {
        // 23:30 UTC hôm qua là 06:30 hôm nay ở UTC+7
        val stamp = Instant.parse("2026-11-07T23:30:00Z").toEpochMilli()
        val utc = StatsMath.dailyCounts(listOf(stamp), now, ZoneOffset.UTC, 3)
        val vn = StatsMath.dailyCounts(listOf(stamp), now, ZoneOffset.ofHours(7), 3)
        assertEquals(listOf(0, 1, 0), utc)
        assertEquals(listOf(0, 0, 1), vn)
    }

    @Test
    fun formatDuration_picksReadableUnits() {
        assertEquals("5s", RingtoneMath.formatDuration(5_000))
        assertEquals("2m 30s", RingtoneMath.formatDuration(150_000))
        assertEquals("1h 02m", RingtoneMath.formatDuration(3_725_000))
        assertEquals("0s", RingtoneMath.formatDuration(-1))
    }
}
