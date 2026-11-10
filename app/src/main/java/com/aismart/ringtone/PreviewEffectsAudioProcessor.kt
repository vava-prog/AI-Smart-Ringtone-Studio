package com.aismart.ringtone

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * Áp dụng trực tiếp preamp, equalizer, giới hạn đỉnh và âm lượng khi nghe thử đoạn cắt.
 * Chỉ bật khi nghe thử đoạn gốc; khi nghe file đã xuất thì [enabled] = false để không xử lý hai lần.
 */
class PreviewEffectsAudioProcessor : BaseAudioProcessor() {
    @Volatile
    private var settings = EffectSettings()

    @Volatile
    private var gain = 1f

    @Volatile
    private var version = 0

    @Volatile
    var enabled = false

    private var appliedVersion = -1
    private var chain: EffectChain? = null
    private var sampleRate = 0
    private var channelCount = 0

    fun update(newSettings: EffectSettings, newGain: Float) {
        settings = newSettings
        gain = newGain
        version++
    }

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat
    ): AudioProcessor.AudioFormat {
        // Định dạng khác 16-bit thì bỏ qua (processor không hoạt động) thay vì làm hỏng phát nhạc
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.channelCount !in 1..8
        ) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        appliedVersion = -1
        chain = null
        return inputAudioFormat
    }

    override fun onFlush() {
        appliedVersion = -1
    }

    override fun onReset() {
        chain = null
        appliedVersion = -1
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val output = replaceOutputBuffer(size)
        if (!enabled) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        if (appliedVersion != version) {
            val current = settings
            chain = if (current.isNeutral) null else EffectChain(sampleRate, channelCount, current)
            appliedVersion = version
        }
        val activeChain = chain
        val g = gain
        val frame = FloatArray(channelCount)
        val frames = size / (2 * channelCount)
        for (f in 0 until frames) {
            for (c in 0 until channelCount) frame[c] = inputBuffer.getShort() / 32768f * g
            activeChain?.process(frame)
            for (c in 0 until channelCount) {
                val v = Math.round(frame[c] * 32768f).coerceIn(-32768, 32767)
                output.putShort(v.toShort())
            }
        }
        // Byte lẻ còn lại (nếu có) được chép nguyên
        if (inputBuffer.hasRemaining()) output.put(inputBuffer)
        output.flip()
    }
}
