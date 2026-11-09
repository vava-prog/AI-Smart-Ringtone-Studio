package com.aismart.ringtone

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPickerTest {

    @Test
    fun beatTracker_findsTempoAndPhase() {
        val onset = FloatArray(2400)
        var i = 3
        while (i < onset.size) {
            onset[i] = 1f
            i += 20
        }
        val grid = BeatTracker.estimate(onset, 25)
        assertNotNull(grid)
        assertEquals(120f, grid!!.bpm, 1.5f)
        assertEquals(75f, grid.phaseMs, 25f)
        assertTrue(grid.confidence > 0.5f)
    }

    @Test
    fun beatTracker_rejectsSilenceAndShortInput() {
        assertNull(BeatTracker.estimate(FloatArray(2400), 25))
        assertNull(BeatTracker.estimate(FloatArray(50) { 1f }, 25))
    }

    private fun analysis(loudFrom: Int, loudTo: Int, n: Int = 240): TrackAnalysis {
        val rms = List(n) { if (it in loudFrom until loudTo) 1f else 0.2f }
        return TrackAnalysis(rms, List(n) { 0.5f }, FloatArray(n * 10), 25, null)
    }

    @Test
    fun picker_choosesLoudSection() {
        val result = SmartPicker.pick(analysis(100, 160), 15_000, 60_000)
        assertTrue(result.isNotEmpty())
        assertEquals(25_000L, result[0].startMs, 500.0)
        assertEquals(15_000L, result[0].endMs - result[0].startMs, 0.0)
    }

    @Test
    fun picker_candidatesDoNotOverlapMoreThanHalf() {
        val result = SmartPicker.pick(analysis(100, 160), 15_000, 60_000)
        for (i in result.indices) for (j in i + 1 until result.size) {
            assertTrue(abs(result[i].startMs - result[j].startMs) >= 7_500L)
        }
        assertTrue(result.all { it.startMs >= 0 && it.endMs <= 60_000 })
    }

    @Test
    fun picker_returnsWholeTrackWhenClipIsLongerThanTrack() {
        val result = SmartPicker.pick(analysis(0, 10, n = 40), 30_000, 10_000)
        assertEquals(1, result.size)
        assertEquals(0L, result[0].startMs)
        assertEquals(10_000L, result[0].endMs)
    }

    @Test
    fun picker_emptyAnalysisGivesNoCandidates() {
        val empty = TrackAnalysis(emptyList(), emptyList(), FloatArray(0), 25, null)
        assertTrue(SmartPicker.pick(empty, 30_000, 60_000).isEmpty())
    }

    @Test
    fun snapToGrid_alignsStartAndLengthToBars() {
        val grid = BeatGrid(120f, 500f, 100f, 1f)
        assertEquals(10_100L to 40_100L, SmartPicker.snapToGrid(10_020, 30_000, grid, 120_000))
        assertEquals(10_100L to 40_100L, SmartPicker.snapToGrid(10_020, 29_800, grid, 120_000))
    }

    @Test
    fun snapToGrid_staysInsideTrack() {
        val grid = BeatGrid(120f, 500f, 100f, 1f)
        assertEquals(5_000L to 35_000L, SmartPicker.snapToGrid(10_020, 30_000, grid, 35_000))
    }
}
