package com.aismart.ringtone

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Step4Test {

    @Test
    fun eqResponse_flatIsZeroEverywhere() {
        for (f in listOf(20.0, 100.0, 1000.0, 10000.0)) {
            assertEquals(0.0, EqResponse.gainDb(f, 0f, listOf(0f, 0f, 0f, 0f, 0f)), 1e-9)
        }
    }

    @Test
    fun eqResponse_peakingCenterMatchesGain() {
        val bands = listOf(0f, 0f, 6f, 0f, 0f)
        assertEquals(6.0, EqResponse.gainDb(910.0, 0f, bands), 0.05)
        assertTrue(abs(EqResponse.gainDb(60.0, 0f, bands)) < 0.8)
    }

    @Test
    fun eqResponse_shelvesReachFullGainAtExtremes() {
        assertEquals(6.0, EqResponse.gainDb(20.0, 0f, listOf(6f, 0f, 0f, 0f, 0f)), 0.6)
        assertEquals(6.0, EqResponse.gainDb(20000.0, 0f, listOf(0f, 0f, 0f, 0f, 6f)), 1.5)
    }

    @Test
    fun eqResponse_includesPreamp() {
        assertEquals(3.0, EqResponse.gainDb(500.0, 3f, listOf(0f, 0f, 0f, 0f, 0f)), 1e-9)
    }

    @Test
    fun mp3Rates_mapsToSupportedRates() {
        assertEquals(44100, Mp3Rates.target(44100))
        assertEquals(48000, Mp3Rates.target(96000))
        assertEquals(44100, Mp3Rates.target(88200))
        assertEquals(44100, Mp3Rates.target(176400))
        assertEquals(22050, Mp3Rates.target(18000))
        assertEquals(8000, Mp3Rates.target(7000))
    }

    @Test
    fun outputFormat_flacIsLossless() {
        assertEquals(OutputFormat.FLAC, OutputFormat.fromExt("FLAC"))
        assertEquals("audio/flac", OutputFormat.FLAC.mime)
        assertTrue(!OutputFormat.FLAC.usesBitrate)
        assertEquals(3_175_226L, RingtoneMath.estimateOutputBytes(OutputFormat.FLAC, 128, 30_000))
    }

    @Test
    fun customPresets_roundTripAndUpsert() {
        val a = CustomPreset("Bass+", 2f, listOf(6f, 3f, 0f, 0f, 0f))
        val b = CustomPreset("Rõ giọng", -1f, listOf(0f, 0f, 3f, 4f, 1f))
        val json = CustomPresets.toJson(listOf(a, b))
        assertEquals(listOf(a, b), CustomPresets.parse(json))
        val replaced = CustomPresets.upsert(listOf(a, b), a.copy(name = "bass+", preampDb = 5f))
        assertEquals(2, replaced.size)
        assertEquals(5f, replaced.last().preampDb, 0f)
        assertTrue(CustomPresets.parse("not json").isEmpty())
        assertTrue(CustomPresets.parse(null).isEmpty())
        assertEquals("Hai dòng", CustomPresets.cleanName("  Hai   dòng  "))
    }

    @Test
    fun customPresets_capsCount() {
        var list = emptyList<CustomPreset>()
        for (i in 1..25) list = CustomPresets.upsert(list, CustomPreset("p$i", 0f, List(5) { 0f }))
        assertEquals(CustomPresets.MAX_PRESETS, list.size)
        assertEquals("p25", list.last().name)
    }

    @Test
    fun flacEncoder_writesValidHeaderAndTotalSamples() {
        val file = File.createTempFile("test", ".flac")
        try {
            val frames = 10_000
            val pcm = ShortArray(frames * 2) { (8000 * sin(2 * PI * 440 * (it / 2) / 44100)).toInt().toShort() }
            val enc = FlacEncoder(RandomAccessFile(file, "rw"), 44100, 2)
            enc.encode(pcm, frames)
            enc.finish()
            val bytes = file.readBytes()
            assertEquals("fLaC", String(bytes, 0, 4, Charsets.US_ASCII))
            // STREAMINFO: 20 bit tần số + 3 bit kênh + 5 bit độ sâu + 36 bit tổng số mẫu bắt đầu ở byte 18
            var packed = 0L
            for (i in 18 until 26) packed = (packed shl 8) or (bytes[i].toLong() and 0xFF)
            assertEquals(frames.toLong(), packed and 0xFFFFFFFFFL)
            assertEquals(44100L, packed ushr 44)
            assertTrue(bytes.size < pcm.size * 2)
        } finally {
            file.delete()
        }
    }

    @Test
    fun resampler_keepsDurationAndDc() {
        val r = Resampler(96000, 48000, 1)
        val input = ShortArray(96000) { 1000 }
        val a = r.process(input, input.size)
        val b = r.flush()
        assertEquals(48000.0, (a.size + b.size).toDouble(), 2.0)
        assertEquals(1000.0, a[a.size / 2].toDouble(), 3.0)
    }
}
