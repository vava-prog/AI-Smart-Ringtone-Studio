package com.aismart.ringtone

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Bộ xử lý PCM 16-bit: fade in, fade out và chỉnh âm lượng cho đoạn đã cắt.
 *
 * Transformer áp dụng việc cắt (clipping) trước khi đưa vào bộ xử lý, nên mẫu đầu tiên
 * nhận được chính là điểm bắt đầu của đoạn. Mỗi lần xuất phải tạo một thể hiện mới.
 */
class FadeGainAudioProcessor(
    private val clipDurationUs: Long,
    private val fadeInUs: Long,
    private val fadeOutUs: Long,
    private val gain: Float
) : BaseAudioProcessor() {

    private var frameIndex = 0L

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat
    ): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val channels = inputAudioFormat.channelCount
        val sampleRate = inputAudioFormat.sampleRate
        val output = replaceOutputBuffer(remaining)
        inputBuffer.order(ByteOrder.nativeOrder())

        val frameBytes = 2 * channels
        while (inputBuffer.remaining() >= frameBytes) {
            val g = RingtoneMath.gainAt(
                frameIndex, sampleRate, clipDurationUs, fadeInUs, fadeOutUs, gain
            )
            for (c in 0 until channels) {
                val sample = inputBuffer.getShort().toInt()
                val scaled = (sample * g).roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                output.putShort(scaled.toShort())
            }
            frameIndex++
        }
        // Bỏ phần dư (nếu có) để bộ đệm đầu vào được tiêu thụ hết
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }
}
