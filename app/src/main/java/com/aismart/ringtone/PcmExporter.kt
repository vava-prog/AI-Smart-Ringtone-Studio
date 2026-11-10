package com.aismart.ringtone

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.CancellationException
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Xuất MP3 hoặc WAV: giải mã đoạn đã chọn thành PCM, áp dụng fade và âm lượng
 * rồi ghi ra file. (M4A được xử lý riêng bằng Media3 Transformer.)
 */
object PcmExporter {
    class NoAudioException : Exception("No audio in the selected range")
    class UnsupportedSampleRateException(val sampleRate: Int) :
        Exception("Sample rate $sampleRate Hz is not supported")

    private interface PcmSink {
        fun write(pcm: ShortArray, frames: Int)
        fun finish()
        fun abort()
    }

    private class WavSink(
        file: File,
        private val sampleRate: Int,
        private val channels: Int
    ) : PcmSink {
        private val raf = RandomAccessFile(file, "rw")
        private var dataBytes = 0L
        private var bytes = ByteArray(0)

        init {
            raf.setLength(0)
            raf.write(WavFormat.header(sampleRate, channels, 0))
        }

        override fun write(pcm: ShortArray, frames: Int) {
            val samples = frames * channels
            val needed = samples * 2
            if (bytes.size < needed) bytes = ByteArray(needed)
            var j = 0
            for (i in 0 until samples) {
                val v = pcm[i].toInt()
                bytes[j++] = v.toByte()
                bytes[j++] = (v shr 8).toByte()
            }
            raf.write(bytes, 0, needed)
            dataBytes += needed
        }

        override fun finish() {
            raf.seek(0)
            raf.write(WavFormat.header(sampleRate, channels, dataBytes))
            raf.close()
        }

        override fun abort() {
            try {
                raf.close()
            } catch (_: Exception) {
            }
        }
    }

    private class Mp3Sink(
        file: File,
        sampleRate: Int,
        private val channels: Int,
        bitrateKbps: Int
    ) : PcmSink {
        private val stream = BufferedOutputStream(FileOutputStream(file), 64 * 1024)
        private val encoder = Mp3Encoder(sampleRate, channels, bitrateKbps)

        override fun write(pcm: ShortArray, frames: Int) {
            encoder.encode(pcm, frames, stream)
        }

        override fun finish() {
            encoder.finish(stream)
            stream.flush()
            stream.close()
            encoder.close()
        }

        override fun abort() {
            try {
                stream.close()
            } catch (_: Exception) {
            }
            try {
                encoder.close()
            } catch (_: Exception) {
            }
        }
    }

    private class FlacSink(file: File, sampleRate: Int, channels: Int) : PcmSink {
        private val encoder = FlacEncoder(RandomAccessFile(file, "rw"), sampleRate, channels)

        override fun write(pcm: ShortArray, frames: Int) {
            encoder.encode(pcm, frames)
        }

        override fun finish() {
            encoder.finish()
        }

        override fun abort() {
            encoder.abort()
        }
    }

    /** Đổi tần số lấy mẫu trước khi chuyển PCM cho bộ mã hóa phía sau. */
    private class ResamplingSink(
        private val inner: PcmSink,
        inRate: Int,
        outRate: Int,
        private val channels: Int
    ) : PcmSink {
        private val resampler = Resampler(inRate, outRate, channels)

        override fun write(pcm: ShortArray, frames: Int) {
            val out = resampler.process(pcm, frames)
            if (out.isNotEmpty()) inner.write(out, out.size / channels)
        }

        override fun finish() {
            val tail = resampler.flush()
            if (tail.isNotEmpty()) inner.write(tail, tail.size / channels)
            inner.finish()
        }

        override fun abort() {
            inner.abort()
        }
    }

    private fun createSink(
        format: OutputFormat,
        file: File,
        sampleRate: Int,
        channels: Int,
        bitrateKbps: Int
    ): PcmSink = when (format) {
        OutputFormat.WAV -> WavSink(file, sampleRate, channels)
        OutputFormat.FLAC -> FlacSink(file, sampleRate, channels)
        OutputFormat.MP3 -> {
            val target = Mp3Rates.target(sampleRate)
            val mp3 = Mp3Sink(file, target, channels, bitrateKbps)
            if (target == sampleRate) mp3 else ResamplingSink(mp3, sampleRate, target, channels)
        }
        OutputFormat.M4A -> throw IllegalArgumentException("M4A is exported with Transformer")
    }

    /**
     * Chạy đồng bộ trên luồng gọi (nên gọi từ Dispatchers.Default/IO).
     * Ném [CancellationException] khi [cancelled] trả về true.
     */
    fun export(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long,
        format: OutputFormat,
        bitrateKbps: Int,
        fadeInUs: Long,
        fadeOutUs: Long,
        gain: Float,
        effects: EffectSettings,
        outFile: File,
        onProgress: (Float) -> Unit,
        cancelled: () -> Boolean
    ) {
        val startUs = startMs * 1000L
        val endUs = endMs * 1000L
        val clipUs = endUs - startUs

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var sink: PcmSink? = null
        var success = false
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
            if (trackIndex < 0 || trackFormat == null) throw NoAudioException()
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: throw NoAudioException()

            extractor.selectTrack(trackIndex)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(trackFormat, null, null, 0)
            decoder.start()

            var sampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var inChannels = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            var scratch = ShortArray(0)
            var chain: EffectChain? = null
            var frameBuf = FloatArray(2)
            var clipFrame = 0L
            var inputDone = false
            var done = false
            val info = MediaCodec.BufferInfo()

            while (!done) {
                if (cancelled()) throw CancellationException("Export cancelled")

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
                    if (info.size > 0) {
                        val buf = decoder.getOutputBuffer(outIndex)
                        if (buf != null && sampleRate > 0 && inChannels > 0) {
                            buf.order(ByteOrder.nativeOrder())
                            val isFloat = pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT
                            val bytesPerSample = if (isFloat) 4 else 2
                            val frameBytes = bytesPerSample * inChannels
                            val frames = info.size / frameBytes
                            val ptsUs = info.presentationTimeUs

                            // Chỉ giữ các khung nằm trong [startUs, endUs)
                            val firstFrame = if (ptsUs >= startUs) {
                                0
                            } else {
                                (((startUs - ptsUs) * sampleRate + 999_999L) / 1_000_000L).toInt()
                            }
                            val lastFrame = (((endUs - ptsUs) * sampleRate) / 1_000_000L)
                                .coerceAtMost(frames.toLong()).toInt()

                            if (lastFrame > firstFrame) {
                                val outChannels = if (inChannels == 1) 1 else 2
                                val count = lastFrame - firstFrame
                                if (sink == null) {
                                    sink = createSink(format, outFile, sampleRate, outChannels, bitrateKbps)
                                    chain = if (effects.isNeutral) {
                                        null
                                    } else {
                                        EffectChain(sampleRate, outChannels, effects)
                                    }
                                }
                                if (scratch.size < count * outChannels) {
                                    scratch = ShortArray(count * outChannels)
                                }
                                buf.position(info.offset + firstFrame * frameBytes)
                                buf.limit(info.offset + info.size)
                                for (f in 0 until count) {
                                    val g = RingtoneMath.gainAt(
                                        clipFrame, sampleRate, clipUs, fadeInUs, fadeOutUs, gain
                                    )
                                    for (c in 0 until inChannels) {
                                        val v = if (isFloat) buf.getFloat() else buf.getShort() / 32768f
                                        if (c < outChannels) frameBuf[c] = v * g
                                    }
                                    chain?.process(frameBuf)
                                    for (c in 0 until outChannels) {
                                        scratch[f * outChannels + c] =
                                            (frameBuf[c] * 32768f).roundToInt().coerceIn(-32768, 32767).toShort()
                                    }
                                    clipFrame++
                                }
                                sink!!.write(scratch, count)
                                if (clipUs > 0) {
                                    onProgress(((ptsUs - startUs).toFloat() / clipUs).coerceIn(0f, 1f))
                                }
                            }
                            if (lastFrame < frames || ptsUs >= endUs) done = true
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
                        inChannels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    pcmEncoding = if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    } else {
                        AudioFormat.ENCODING_PCM_16BIT
                    }
                }
            }

            val finished = sink ?: throw NoAudioException()
            finished.finish()
            sink = null
            onProgress(1f)
            success = true
        } finally {
            sink?.abort()
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
            extractor.release()
            if (!success) outFile.delete()
        }
    }
}
