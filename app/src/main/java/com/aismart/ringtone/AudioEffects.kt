package com.aismart.ringtone

import androidx.annotation.StringRes
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Cấu hình dải equalizer (5 dải). */
object EqConfig {
    const val COUNT = 5
    val FREQS = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)
    val LABELS = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
    const val MAX_DB = 12f
}

/** Các mẫu equalizer có sẵn. */
enum class EqPreset(@StringRes val labelRes: Int, val preampDb: Float, val bands: List<Float>) {
    FLAT(R.string.preset_flat, 0f, listOf(0f, 0f, 0f, 0f, 0f)),
    BASS(R.string.preset_bass, 0f, listOf(6f, 3f, 0f, 0f, 0f)),
    VOCAL(R.string.preset_vocal, 0f, listOf(-2f, 0f, 3f, 4f, 1f)),
    SPEAKER(R.string.preset_speaker, 0f, listOf(-3f, 0f, 2f, 4f, 3f))
}

/**
 * Hiệu ứng áp dụng khi xuất: preamp, equalizer 5 dải, chuẩn hóa (hệ số tuyến tính đã đo sẵn)
 * và bộ giới hạn đỉnh.
 */
data class EffectSettings(
    val preampDb: Float = 0f,
    val bandGainsDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
    val normalizeGain: Float = 1f,
    val limiter: Boolean = false
) {
    val isNeutral: Boolean
        get() = preampDb == 0f && bandGainsDb.all { it == 0f } && normalizeGain == 1f && !limiter
}

/** Bộ lọc biquad (dạng transposed direct form II), mỗi kênh một trạng thái. */
private class Biquad(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
    channels: Int
) {
    private val z1 = DoubleArray(channels)
    private val z2 = DoubleArray(channels)

    fun process(channel: Int, x: Double): Double {
        val y = b0 * x + z1[channel]
        z1[channel] = b1 * x - a1 * y + z2[channel]
        z2[channel] = b2 * x - a2 * y
        return y
    }
}

/**
 * Chuỗi xử lý theo từng khung: preamp x chuẩn hóa -> equalizer -> giới hạn đỉnh.
 * Mẫu có thang -1..1. Một thể hiện cho mỗi lần xuất (có trạng thái bộ lọc).
 */
class EffectChain(sampleRate: Int, private val channels: Int, settings: EffectSettings) {
    private val preGain: Double =
        10.0.pow(settings.preampDb / 20.0) * settings.normalizeGain
    private val filters: List<Biquad> = buildFilters(sampleRate, channels, settings.bandGainsDb)
    private val limiterOn = settings.limiter
    private val releaseCoef = 1.0 - exp(-1.0 / (RELEASE_SECONDS * sampleRate))
    private var limiterGain = 1.0

    /** Xử lý tại chỗ một khung gồm [channels] mẫu đầu của [frame]. */
    fun process(frame: FloatArray) {
        var peak = 0.0
        for (c in 0 until channels) {
            var x = frame[c] * preGain
            for (f in filters) x = f.process(c, x)
            frame[c] = x.toFloat()
            peak = maxOf(peak, abs(x))
        }
        if (limiterOn) {
            val need = if (peak > CEILING) CEILING / peak else 1.0
            limiterGain = if (need < limiterGain) {
                need
            } else {
                minOf(need, limiterGain + (1.0 - limiterGain) * releaseCoef)
            }
            for (c in 0 until channels) frame[c] = (frame[c] * limiterGain).toFloat()
        }
    }

    companion object {
        /** Trần giới hạn đỉnh, khoảng -0,3 dBFS. */
        const val CEILING = 0.97
        private const val RELEASE_SECONDS = 0.1

        private fun buildFilters(sampleRate: Int, channels: Int, gainsDb: List<Float>): List<Biquad> {
            val result = ArrayList<Biquad>()
            for (i in 0 until EqConfig.COUNT) {
                val gain = gainsDb.getOrElse(i) { 0f }
                val freq = EqConfig.FREQS[i].toDouble()
                if (gain == 0f || freq >= 0.45 * sampleRate) continue
                val kind = when (i) {
                    0 -> Kind.LOW_SHELF
                    EqConfig.COUNT - 1 -> Kind.HIGH_SHELF
                    else -> Kind.PEAKING
                }
                result.add(design(kind, sampleRate.toDouble(), freq, gain.toDouble(), channels))
            }
            return result
        }

        private enum class Kind { LOW_SHELF, PEAKING, HIGH_SHELF }

        /** Công thức Audio EQ Cookbook (RBJ). */
        private fun design(kind: Kind, fs: Double, f0: Double, gainDb: Double, channels: Int): Biquad {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * f0 / fs
            val cosW = cos(w0)
            val sinW = sin(w0)
            val b0: Double
            val b1: Double
            val b2: Double
            val a0: Double
            val a1: Double
            val a2: Double
            when (kind) {
                Kind.PEAKING -> {
                    val alpha = sinW / (2.0 * 1.0)
                    b0 = 1.0 + alpha * a
                    b1 = -2.0 * cosW
                    b2 = 1.0 - alpha * a
                    a0 = 1.0 + alpha / a
                    a1 = -2.0 * cosW
                    a2 = 1.0 - alpha / a
                }
                Kind.LOW_SHELF -> {
                    val alpha = sinW / 2.0 * sqrt(2.0)
                    val beta = 2.0 * sqrt(a) * alpha
                    b0 = a * ((a + 1) - (a - 1) * cosW + beta)
                    b1 = 2.0 * a * ((a - 1) - (a + 1) * cosW)
                    b2 = a * ((a + 1) - (a - 1) * cosW - beta)
                    a0 = (a + 1) + (a - 1) * cosW + beta
                    a1 = -2.0 * ((a - 1) + (a + 1) * cosW)
                    a2 = (a + 1) + (a - 1) * cosW - beta
                }
                Kind.HIGH_SHELF -> {
                    val alpha = sinW / 2.0 * sqrt(2.0)
                    val beta = 2.0 * sqrt(a) * alpha
                    b0 = a * ((a + 1) + (a - 1) * cosW + beta)
                    b1 = -2.0 * a * ((a - 1) + (a + 1) * cosW)
                    b2 = a * ((a + 1) + (a - 1) * cosW - beta)
                    a0 = (a + 1) - (a - 1) * cosW + beta
                    a1 = 2.0 * ((a - 1) - (a + 1) * cosW)
                    a2 = (a + 1) - (a - 1) * cosW - beta
                }
            }
            return Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0, channels)
        }
    }
}
