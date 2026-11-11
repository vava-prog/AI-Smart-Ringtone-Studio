package com.aismart.ringtone

import androidx.annotation.StringRes
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Lưới nhịp ước tính: tempo, chu kỳ một nhịp và vị trí nhịp đầu tiên. */
data class BeatGrid(val bpm: Float, val periodMs: Float, val phaseMs: Float, val confidence: Float)

/**
 * Kết quả phân tích một bài: năng lượng (RMS), độ sáng (tốc độ đổi dấu) theo ô
 * [RingtoneMath.BUCKET_MS] ms, độ nhấn nhịp (onset) mịn theo [onsetHopMs] ms và lưới nhịp.
 */
class TrackAnalysis(
    val rms: List<Float>,
    val bright: List<Float>,
    val onsetFine: FloatArray,
    val onsetHopMs: Int,
    val beat: BeatGrid?
)

enum class PickKind(@StringRes val labelRes: Int) {
    CHORUS(R.string.pick_chorus),
    PEAK(R.string.pick_peak),
    INTRO(R.string.pick_intro)
}

data class PickCandidate(val startMs: Long, val endMs: Long, val kind: PickKind, val score: Float)

/** Ước tính tempo và pha nhịp từ đường onset bằng tự tương quan. */
object BeatTracker {
    const val MIN_CONFIDENCE = 0.2f

    fun estimate(onset: FloatArray, hopMs: Int): BeatGrid? {
        if (hopMs <= 0) return null
        val n = onset.size
        val minLag = max(2, 300 / hopMs)
        val maxLag = 1000 / hopMs
        if (maxLag <= minLag || n < maxLag * 8) return null

        var mean = 0.0
        for (v in onset) mean += v
        mean /= n
        val x = FloatArray(n) { (onset[it] - mean).toFloat() }
        var r0 = 0.0
        for (v in x) r0 += v.toDouble() * v
        if (r0 <= 1e-9) return null

        val acf = DoubleArray(maxLag + 2)
        for (lag in (minLag - 1)..(maxLag + 1)) {
            var sum = 0.0
            for (i in 0 until n - lag) sum += x[i].toDouble() * x[i + lag]
            acf[lag] = sum
        }
        var best = minLag
        for (lag in minLag..maxLag) if (acf[lag] > acf[best]) best = lag
        if (acf[best] <= 0.0) return null

        val y1 = acf[best - 1]
        val y2 = acf[best]
        val y3 = acf[best + 1]
        val denom = y1 - 2 * y2 + y3
        val shift = if (denom < 0) (0.5 * (y1 - y3) / denom).coerceIn(-0.5, 0.5) else 0.0
        val lagF = best + shift
        val confidence = ((acf[best] / (n - best)) / (r0 / n)).toFloat()

        var bestPhase = 0
        var bestSum = -1.0
        for (p in 0 until ceil(lagF).toInt()) {
            var sum = 0.0
            var k = 0
            while (true) {
                val idx = Math.round(p + k * lagF).toInt()
                if (idx >= n) break
                sum += onset[idx]
                k++
            }
            if (sum > bestSum) {
                bestSum = sum
                bestPhase = p
            }
        }
        val periodMs = (lagF * hopMs).toFloat()
        return BeatGrid(60_000f / periodMs, periodMs, (bestPhase * hopMs).toFloat(), confidence)
    }
}

/**
 * Chọn đoạn hay nhất: kết hợp năng lượng, mật độ nhịp nhấn và độ lặp lại (điệp khúc thường
 * lặp), tránh đoạn mở đầu im lặng, trả về tối đa 3 gợi ý khác nhau và khớp điểm cắt theo nhịp.
 */
object SmartPicker {
    private const val FRAME_BUCKETS = 4

    /** Số đoạn cao trào/điệp khúc tối đa được gợi ý (chưa tính đoạn mở đầu). */
    private const val MAX_PEAKS = 3

    fun pick(a: TrackAnalysis, clipMs: Long, durationMs: Long): List<PickCandidate> {
        val n = a.rms.size
        val bucketMs = RingtoneMath.BUCKET_MS
        if (n == 0 || durationMs <= 0L) return emptyList()
        val w = (clipMs / bucketMs).toInt().coerceIn(1, n)
        if (w >= n) {
            return listOf(PickCandidate(0L, min(durationMs, max(clipMs, 1L)), PickKind.PEAK, 1f))
        }

        // Độ nhấn nhịp gom theo ô, chuẩn hóa 0..1
        val per = (bucketMs / a.onsetHopMs).toInt().coerceAtLeast(1)
        val onsetB = FloatArray(n)
        for (i in 0 until n) {
            var sum = 0f
            for (k in i * per until min(a.onsetFine.size, (i + 1) * per)) sum += a.onsetFine[k]
            onsetB[i] = sum
        }
        val onsetMax = onsetB.maxOrNull() ?: 0f
        if (onsetMax > 0f) for (i in 0 until n) onsetB[i] /= onsetMax

        val rms = FloatArray(n) { a.rms[it] }
        val bright = FloatArray(n) { a.bright.getOrElse(it) { 0f } }
        val intensity = FloatArray(n) { 0.6f * rms[it] + 0.25f * onsetB[it] + 0.15f * bright[it] }

        val starts = n - w + 1
        val energy = windowAverage(intensity, w).normalized()
        val density = windowAverage(onsetB, w).normalized()

        // Độ lặp lại: so sánh cửa sổ với các cửa sổ không chồng lấn ở độ phân giải 1 giây
        val frames = n / FRAME_BUCKETS
        val frameLen = max(1, w / FRAME_BUCKETS)
        val repetition = FloatArray(starts)
        if (frames >= frameLen * 2) {
            val f0 = FloatArray(frames)
            val f1 = FloatArray(frames)
            val f2 = FloatArray(frames)
            for (f in 0 until frames) {
                for (k in 0 until FRAME_BUCKETS) {
                    val i = f * FRAME_BUCKETS + k
                    f0[f] += rms[i] / FRAME_BUCKETS
                    f1[f] += bright[i] / FRAME_BUCKETS
                    f2[f] += onsetB[i] / FRAME_BUCKETS
                }
            }
            val lastFrameStart = frames - frameLen
            val step = if (frames > 500) 2 else 1
            val frameRep = FloatArray(lastFrameStart + 1)
            for (s in 0..lastFrameStart) {
                var bestSim = 0f
                var t = 0
                while (t <= lastFrameStart) {
                    if (abs(t - s) >= frameLen) {
                        var diff = 0f
                        for (k in 0 until frameLen) {
                            diff += abs(f0[s + k] - f0[t + k]) +
                                abs(f1[s + k] - f1[t + k]) +
                                abs(f2[s + k] - f2[t + k])
                        }
                        val sim = 1f - diff / (3f * frameLen)
                        if (sim > bestSim) bestSim = sim
                    }
                    t += step
                }
                frameRep[s] = bestSim
            }
            for (b in 0 until starts) {
                repetition[b] = frameRep[min(b / FRAME_BUCKETS, lastFrameStart)]
            }
        }

        val score = FloatArray(starts) { b ->
            var s = 0.45f * energy[b] + 0.35f * repetition[b] * energy[b] + 0.20f * density[b]
            if (rms[b] + rms[min(n - 1, b + 1)] < 0.1f) s *= 0.6f
            s
        }

        val minGap = max(1, w / 2)
        val chosen = ArrayList<Int>()
        for (b in (0 until starts).sortedByDescending { score[it] }) {
            if (chosen.all { abs(it - b) >= minGap }) chosen.add(b)
            if (chosen.size == MAX_PEAKS) break
        }

        val result = ArrayList<PickCandidate>()
        for (b in chosen) {
            val kind = if (repetition[b] >= 0.8f && energy[b] >= 0.8f) PickKind.CHORUS else PickKind.PEAK
            result.add(build(b, kind, score[b], clipMs, durationMs, a.beat))
        }

        // Mở đầu: bỏ qua đoạn im lặng ở đầu bài
        val maxIntensity = intensity.maxOrNull() ?: 0f
        val intro = intensity.indexOfFirst { it >= 0.25f * maxIntensity }.coerceAtLeast(0)
            .coerceAtMost(starts - 1)
        if (chosen.all { abs(it - intro) >= minGap }) {
            result.add(build(intro, PickKind.INTRO, score[intro], clipMs, durationMs, a.beat))
        }
        return result
    }

    private fun build(
        bucket: Int,
        kind: PickKind,
        score: Float,
        clipMs: Long,
        durationMs: Long,
        beat: BeatGrid?
    ): PickCandidate {
        val start = bucket * RingtoneMath.BUCKET_MS
        val end = min(durationMs, start + clipMs)
        if (beat != null && beat.confidence >= BeatTracker.MIN_CONFIDENCE) {
            val (s, e) = snapToGrid(start, end - start, beat, durationMs)
            return PickCandidate(s, e, kind, score)
        }
        return PickCandidate(start, end, kind, score)
    }

    /** Dịch điểm bắt đầu về nhịp gần nhất và làm tròn độ dài về bội số 4 nhịp (1 ô nhịp). */
    fun snapToGrid(startMs: Long, lengthMs: Long, grid: BeatGrid, durationMs: Long): Pair<Long, Long> {
        val period = grid.periodMs.toDouble()
        val phase = grid.phaseMs.toDouble()
        var s = phase + Math.round((startMs - phase) / period) * period
        if (s < 0) s += period
        val beats = Math.round(lengthMs / period).toInt().coerceAtLeast(1)
        val snapped = if (beats >= 8) ((beats + 2) / 4) * 4 else beats
        var len = snapped * period
        if (abs(len - lengthMs) > lengthMs * 0.2) len = lengthMs.toDouble()
        var st = s.roundToLong()
        val ln = len.roundToLong()
        if (st + ln > durationMs) st = durationMs - ln
        if (st < 0) return Pair(startMs, startMs + lengthMs)
        return Pair(st, st + ln)
    }

    private fun windowAverage(values: FloatArray, w: Int): FloatArray {
        val prefix = DoubleArray(values.size + 1)
        for (i in values.indices) prefix[i + 1] = prefix[i] + values[i]
        return FloatArray(values.size - w + 1) { ((prefix[it + w] - prefix[it]) / w).toFloat() }
    }

    private fun FloatArray.normalized(): FloatArray {
        val top = maxOrNull() ?: 0f
        if (top <= 0f) return this
        return FloatArray(size) { this[it] / top }
    }
}
