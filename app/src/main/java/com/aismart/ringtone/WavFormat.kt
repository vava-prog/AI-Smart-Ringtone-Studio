package com.aismart.ringtone

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Tạo phần đầu (44 byte) của file WAV PCM 16-bit. */
object WavFormat {
    const val HEADER_SIZE = 44

    fun header(sampleRate: Int, channels: Int, dataBytes: Long): ByteArray {
        val bitsPerSample = 16
        val blockAlign = channels * bitsPerSample / 8
        val byteRate = sampleRate * blockAlign
        val data = dataBytes.coerceIn(0L, 0xFFFF_FFFFL - 36L)

        val buffer = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt((36L + data).toInt())
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(data.toInt())
        return buffer.array()
    }
}
