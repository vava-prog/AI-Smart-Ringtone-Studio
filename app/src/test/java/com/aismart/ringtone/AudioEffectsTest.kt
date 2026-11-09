package com.aismart.ringtone

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEffectsTest {
    private val rate = 44100

    private fun run(settings: EffectSettings, freq: Double, amp: Float, frames: Int): FloatArray {
        val chain = EffectChain(rate, 1, settings)
        val frame = FloatArray(1)
        return FloatArray(frames) { i ->
            frame[0] = (amp * sin(2.0 * PI * freq * i / rate)).toFloat()
            chain.process(frame)
            frame[0]
        }
    }

    private fun peakOfTail(data: FloatArray, tail: Int): Float =
        data.takeLast(tail).maxOf { abs(it) }

    @Test
    fun neutralSettings_leaveSamplesUnchanged() {
        assertTrue(EffectSettings().isNeutral)
        val out = run(EffectSettings(), 1000.0, 0.5f, 500)
        val expected = FloatArray(500) { i -> (0.5f * sin(2.0 * PI * 1000.0 * i / rate)).toFloat() }
        for (i in out.indices) assertEquals(expected[i], out[i], 1e-6f)
    }

    @Test
    fun preamp_sixDbDoublesAmplitude() {
        val out = run(EffectSettings(preampDb = 6.0206f), 1000.0, 0.2f, 2000)
        assertEquals(0.4f, peakOfTail(out, 500), 0.005f)
    }

    @Test
    fun normalizeGain_scalesLinearly() {
        val out = run(EffectSettings(normalizeGain = 2f), 1000.0, 0.2f, 2000)
        assertEquals(0.4f, peakOfTail(out, 500), 0.005f)
    }

    @Test
    fun peakingBand_boostsCenterFrequency() {
        val gains = listOf(0f, 0f, 6f, 0f, 0f)
        val out = run(EffectSettings(bandGainsDb = gains), 910.0, 0.1f, 6000)
        assertEquals(0.2f, peakOfTail(out, 1000), 0.01f)
    }

    @Test
    fun peakingBand_leavesFarFrequencyAlmostUnchanged() {
        val gains = listOf(0f, 0f, 6f, 0f, 0f)
        val out = run(EffectSettings(bandGainsDb = gains), 100.0, 0.1f, 6000)
        assertEquals(0.1f, peakOfTail(out, 2000), 0.015f)
    }

    @Test
    fun limiter_neverExceedsCeiling() {
        val out = run(EffectSettings(limiter = true, preampDb = 12f), 300.0, 0.9f, 20000)
        assertTrue(out.maxOf { abs(it) } <= EffectChain.CEILING.toFloat() + 1e-4f)
        assertFalse(EffectSettings(limiter = true).isNeutral)
    }

    @Test
    fun limiter_doesNotTouchQuietSignal() {
        val out = run(EffectSettings(limiter = true), 300.0, 0.3f, 4000)
        assertEquals(0.3f, peakOfTail(out, 1000), 0.005f)
    }
}
