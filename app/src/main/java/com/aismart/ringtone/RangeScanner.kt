package com.aismart.ringtone

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.CancellationException
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/**
 * Giải mã một đoạn [startMs, endMs) và đo đỉnh biên độ (0..1): đỉnh toàn đoạn
 * (dùng để chuẩn hóa âm lượng) và đỉnh theo từng ô (dùng để vẽ sóng phóng to).
 */
object RangeScanner {
    class Result(val peak: Float, val buckets: List<Float>)

    /** Chạy đồng bộ; ném [CancellationException] khi [cancelled] trả về true. */
    fun scan(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long,
        bucketCount: Int,
        cancelled: () -> Boolean
    ): Result {
        val startUs = startMs * 1000L
        val endUs = endMs * 1000L
        val clipUs = (endUs - startUs).coerceAtLeast(1L)
        val buckets = FloatArray(bucketCount.coerceAtLeast(1))
        var overall = 0f

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var trackIndex = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                val mime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    trackFormat = candidate
                    break
                }
            }
            if (trackIndex < 0 || trackFormat == null) return Result(0f, buckets.toList())
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)
                ?: return Result(0f, buckets.toList())

            extractor.selectTrack(trackIndex)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(trackFormat, null, null, 0)
            decoder.start()

            var sampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var done = false

            while (!done) {
                if (cancelled()) throw CancellationException("Scan cancelled")

                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuffer = decoder.getInputBuffer(inIndex)
                        val size = if (inBuffer != null) extractor.readSampleData(inBuffer, 0) else -1
                        val sampleTimeUs = extractor.sampleTime
                        if (size < 0 || sampleTimeUs > endUs) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, sampleTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = decoder.dequeueOutputBuffer(info, 10_000)
                if (outIndex >= 0) {
                    if (info.size > 0 && sampleRate > 0 && channels > 0) {
                        val buf = decoder.getOutputBuffer(outIndex)
                        if (buf != null) {
                            buf.order(ByteOrder.nativeOrder())
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            val isFloat = pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT
                            val frameBytes = (if (isFloat) 4 else 2) * channels
                            val frames = info.size / frameBytes
                            val ptsUs = info.presentationTimeUs
                            for (f in 0 until frames) {
                                val frameUs = ptsUs + f * 1_000_000L / sampleRate
                                if (frameUs < startUs) {
                                    buf.position(buf.position() + frameBytes)
                                    continue
                                }
                                if (frameUs >= endUs) {
                                    done = true
                                    break
                                }
                                var p = 0f
                                for (c in 0 until channels) {
                                    val v = if (isFloat) buf.getFloat() else buf.getShort() / 32768f
                                    p = max(p, abs(v))
                                }
                                val index = ((frameUs - startUs) * buckets.size / clipUs).toInt()
                                    .coerceIn(0, buckets.size - 1)
                                if (p > buckets[index]) buckets[index] = p
                                if (p > overall) overall = p
                            }
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) done = true
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val outFormat = decoder.outputFormat
                    if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    pcmEncoding = if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    } else {
                        AudioFormat.ENCODING_PCM_16BIT
                    }
                }
            }
            return Result(overall, buckets.toList())
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
            extractor.release()
        }
    }
}
