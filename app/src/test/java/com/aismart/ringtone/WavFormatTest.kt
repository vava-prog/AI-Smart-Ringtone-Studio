package com.aismart.ringtone

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class WavFormatTest {

    @Test
    fun header_hasExpectedLayoutForStereo44k() {
        val h = WavFormat.header(44100, 2, 1000)
        assertEquals(WavFormat.HEADER_SIZE, h.size)
        assertEquals("RIFF", String(h, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(h, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(h, 12, 4, Charsets.US_ASCII))
        assertEquals("data", String(h, 36, 4, Charsets.US_ASCII))

        val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1036, b.getInt(4))          // 36 + dữ liệu
        assertEquals(16, b.getInt(16))           // kích thước khối fmt
        assertEquals(1, b.getShort(20).toInt())  // PCM
        assertEquals(2, b.getShort(22).toInt())  // số kênh
        assertEquals(44100, b.getInt(24))        // tần số
        assertEquals(176400, b.getInt(28))       // byte/giây
        assertEquals(4, b.getShort(32).toInt())  // block align
        assertEquals(16, b.getShort(34).toInt()) // bit/mẫu
        assertEquals(1000, b.getInt(40))         // kích thước dữ liệu
    }

    @Test
    fun header_monoComputesByteRateAndBlockAlign() {
        val b = ByteBuffer.wrap(WavFormat.header(22050, 1, 0)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(44100, b.getInt(28))
        assertEquals(2, b.getShort(32).toInt())
        assertEquals(36, b.getInt(4))
    }

    @Test
    fun outputFormat_fromExtFallsBackToM4a() {
        assertEquals(OutputFormat.MP3, OutputFormat.fromExt("MP3"))
        assertEquals(OutputFormat.WAV, OutputFormat.fromExt("wav"))
        assertEquals(OutputFormat.M4A, OutputFormat.fromExt("xyz"))
        assertEquals(OutputFormat.M4A, OutputFormat.fromExt(null))
    }
}
