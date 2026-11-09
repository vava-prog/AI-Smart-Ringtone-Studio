package com.aismart.ringtone

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.sqrt

/** Đọc thông tin file nguồn và phân tích năng lượng âm thanh để vẽ sóng và gợi ý đoạn hay. */
object AudioAnalyzer {
    /** Chỉ phân tích tối đa 15 phút đầu để không quá nặng với file dài. */
    private const val MAX_ANALYSIS_US = 15L * 60L * 1_000_000L

    fun readDurationMs(context: Context, uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    fun readDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index) else null
                    } else {
                        null
                    }
                } ?: uri.lastPathSegment
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }

    /**
     * Phân tích đặc trưng: năng lượng (RMS), độ sáng, độ nhấn nhịp và lưới nhịp
     * (đã chuẩn hóa 0..1), hoặc null nếu không giải mã được.
     */
    suspend fun analyze(context: Context, uri: Uri, durationMs: Long): TrackAnalysis? {
        val bucketCount = ((durationMs + RingtoneMath.BUCKET_MS - 1) / RingtoneMath.BUCKET_MS)
            .toInt()
            .coerceAtLeast(1)
        return withContext(Dispatchers.Default) {
            try {
                decodeEnergy(context, uri, bucketCount) { !isActive }
            } catch (e: Exception) {
                null
            }
        }
    }

    private const val HOP_MS = 25

    private fun decodeEnergy(
        context: Context,
        uri: Uri,
        bucketCount: Int,
        cancelled: () -> Boolean
    ): TrackAnalysis? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)

            var trackIndex = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    trackFormat = format
                    break
                }
            }
            if (trackIndex < 0 || trackFormat == null) return null
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return null

            extractor.selectTrack(trackIndex)
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(trackFormat, null, null, 0)
            decoder.start()

            val sumSquares = DoubleArray(bucketCount)
            val counts = LongArray(bucketCount)
            val zeroCross = LongArray(bucketCount)
            val hopCount = (bucketCount.toLong() * RingtoneMath.BUCKET_MS / HOP_MS + 2).toInt()
            val envSum = DoubleArray(hopCount)
            val envCount = IntArray(hopCount)
            var prevPositive = true
            var sampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var frameIndex = 0L

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (cancelled()) return null

                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuffer = decoder.getInputBuffer(inIndex)
                        val size = if (inBuffer != null) extractor.readSampleData(inBuffer, 0) else -1
                        val sampleTimeUs = extractor.sampleTime
                        if (size < 0 || sampleTimeUs > MAX_ANALYSIS_US) {
                            decoder.queueInputBuffer(
                                inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, sampleTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = decoder.dequeueOutputBuffer(info, 10_000)
                if (outIndex >= 0) {
                    if (info.size > 0 && sampleRate > 0) {
                        val outBuffer = decoder.getOutputBuffer(outIndex)
                        if (outBuffer != null) {
                            outBuffer.position(info.offset)
                            outBuffer.limit(info.offset + info.size)
                            outBuffer.order(ByteOrder.nativeOrder())
                            val isFloat = pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT
                            val bytesPerSample = if (isFloat) 4 else 2
                            val channelCount = max(1, channels)
                            val frames = info.size / (bytesPerSample * channelCount)
                            for (f in 0 until frames) {
                                var acc = 0.0
                                var mono = 0.0
                                for (c in 0 until channelCount) {
                                    val v = if (isFloat) {
                                        outBuffer.getFloat().toDouble()
                                    } else {
                                        outBuffer.getShort() / 32768.0
                                    }
                                    acc += v * v
                                    mono += v
                                }
                                mono /= channelCount
                                val positive = mono >= 0.0
                                val hop = (frameIndex * 1000L / (sampleRate.toLong() * HOP_MS)).toInt()
                                if (hop in 0 until hopCount) {
                                    envSum[hop] += kotlin.math.abs(mono)
                                    envCount[hop]++
                                }
                                val bucket = (frameIndex * 1000L /
                                    (sampleRate.toLong() * RingtoneMath.BUCKET_MS)).toInt()
                                if (bucket in 0 until bucketCount) {
                                    sumSquares[bucket] += acc / channelCount
                                    counts[bucket]++
                                    if (positive != prevPositive) zeroCross[bucket]++
                                }
                                prevPositive = positive
                                frameIndex++
                            }
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
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

            val rms = FloatArray(bucketCount) { i ->
                if (counts[i] > 0) sqrt(sumSquares[i] / counts[i]).toFloat() else 0f
            }
            val peak = rms.maxOrNull() ?: 0f
            if (peak <= 0f) return null

            val bright = FloatArray(bucketCount) { i ->
                if (counts[i] > 0) zeroCross[i].toFloat() / counts[i] else 0f
            }
            val brightPeak = bright.maxOrNull() ?: 0f
            if (brightPeak > 0f) for (i in bright.indices) bright[i] /= brightPeak

            // Đường onset: phần tăng dương của đường bao biên độ theo từng 25 ms
            val envelope = FloatArray(hopCount) { k ->
                if (envCount[k] > 0) (envSum[k] / envCount[k]).toFloat() else 0f
            }
            val onset = FloatArray(hopCount)
            for (k in 1 until hopCount) onset[k] = max(0f, envelope[k] - envelope[k - 1])
            val onsetPeak = onset.maxOrNull() ?: 0f
            if (onsetPeak > 0f) for (k in onset.indices) onset[k] /= onsetPeak

            return TrackAnalysis(
                rms = rms.map { it / peak },
                bright = bright.toList(),
                onsetFine = onset,
                onsetHopMs = HOP_MS,
                beat = BeatTracker.estimate(onset, HOP_MS)
            )
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
