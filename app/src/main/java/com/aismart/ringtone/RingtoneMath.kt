package com.aismart.ringtone

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Các hàm tính toán thuần (không phụ thuộc Android) để dễ kiểm thử. */
object RingtoneMath {
    const val BUCKET_MS = 250L
    const val MAX_CLIP_MS = 240_000L
    const val MIN_CLIP_MS = 1_000L
    const val DEFAULT_CLIP_MS = 30_000L

    data class Range(val startMs: Long, val endMs: Long)

    /** Định dạng m:ss.d, ví dụ 65500 -> "1:05.5". */
    fun formatTime(ms: Long): String {
        val total = max(0L, ms)
        val minutes = total / 60_000
        val seconds = (total / 1000) % 60
        val tenths = (total % 1000) / 100
        return String.format(Locale.ROOT, "%d:%02d.%d", minutes, seconds, tenths)
    }

    /** Thời lượng tổng quát: "5s", "2m 30s" hoặc "1h 02m". */
    fun formatDuration(ms: Long): String {
        val totalSeconds = max(0L, ms) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> String.format(Locale.ROOT, "%dh %02dm", hours, minutes)
            minutes > 0 -> String.format(Locale.ROOT, "%dm %02ds", minutes, seconds)
            else -> "${seconds}s"
        }
    }

    fun formatSize(bytes: Long, locale: Locale = Locale.getDefault()): String {
        return if (bytes < 1024L * 1024L) {
            String.format(locale, "%.0f KB", bytes / 1024.0)
        } else {
            String.format(locale, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    /** Dung lượng ước tính (byte) của đoạn AAC theo bitrate (kbps) và thời lượng (ms). */
    fun estimateSizeBytes(bitrateKbps: Int, durationMs: Long): Long {
        return bitrateKbps * 125L * max(0L, durationMs) / 1000L
    }

    /** Dung lượng ước tính theo định dạng; WAV tính theo 44,1 kHz stereo 16-bit. */
    fun estimateOutputBytes(format: OutputFormat, bitrateKbps: Int, durationMs: Long): Long {
        val wav = max(0L, durationMs) * 176_400L / 1000L + 44L
        return when {
            format == OutputFormat.FLAC -> wav * 6L / 10L
            format.usesBitrate -> estimateSizeBytes(bitrateKbps, durationMs)
            else -> wav
        }
    }

    /** Tên file an toàn lấy từ tên hiển thị của file nguồn. */
    fun safeTitle(name: String?): String {
        val raw = name?.trim().orEmpty()
        val base = if (raw.contains('.')) raw.substringBeforeLast('.') else raw
        val cleaned = base.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.ifEmpty { "ringtone" }.take(60)
    }

    /**
     * Hệ số khuếch đại tại khung hình [frameIndex] của đoạn dài [totalDurationUs],
     * gồm fade in, fade out và âm lượng nền [baseGain].
     */
    fun gainAt(
        frameIndex: Long,
        sampleRate: Int,
        totalDurationUs: Long,
        fadeInUs: Long,
        fadeOutUs: Long,
        baseGain: Float
    ): Float {
        val positionUs = frameIndex * 1_000_000L / sampleRate
        var gain = baseGain
        if (fadeInUs > 0 && positionUs < fadeInUs) {
            gain *= positionUs.toFloat() / fadeInUs
        }
        if (fadeOutUs > 0 && totalDurationUs > 0) {
            val fadeStartUs = totalDurationUs - fadeOutUs
            if (positionUs > fadeStartUs) {
                gain *= max(0f, (totalDurationUs - positionUs).toFloat() / fadeOutUs)
            }
        }
        return gain
    }

    /**
     * Tìm cửa sổ [windowBuckets] ô liên tiếp có tổng năng lượng lớn nhất,
     * trả về chỉ số ô bắt đầu. Nếu nhiều cửa sổ bằng nhau thì lấy cửa sổ đầu tiên.
     */
    fun smartPickStartBucket(profile: List<Float>, windowBuckets: Int): Int {
        if (profile.isEmpty() || windowBuckets <= 0 || windowBuckets >= profile.size) return 0
        var sum = 0.0
        for (i in 0 until windowBuckets) sum += profile[i]
        var best = sum
        var bestIndex = 0
        for (i in 1..(profile.size - windowBuckets)) {
            sum += profile[i + windowBuckets - 1] - profile[i - 1]
            if (sum > best + 1e-9) {
                best = sum
                bestIndex = i
            }
        }
        return bestIndex
    }

    /**
     * Giới hạn khoảng chọn trong [0, durationMs], dài tối thiểu [MIN_CLIP_MS] và tối đa [MAX_CLIP_MS].
     * Khi phải chỉnh độ dài thì giữ nguyên đầu mà người dùng vừa kéo (so với giá trị cũ).
     */
    fun clampRange(
        oldStartMs: Long,
        newStartMs: Long,
        newEndMs: Long,
        durationMs: Long
    ): Range {
        var start = newStartMs.coerceIn(0L, durationMs)
        var end = newEndMs.coerceIn(0L, durationMs)
        if (end < start) end = start
        val minLength = min(MIN_CLIP_MS, durationMs)
        val startMoved = start != oldStartMs
        if (end - start > MAX_CLIP_MS) {
            if (startMoved) end = start + MAX_CLIP_MS else start = end - MAX_CLIP_MS
        }
        if (end - start < minLength) {
            if (startMoved) start = max(0L, end - minLength) else end = min(durationMs, start + minLength)
        }
        if (end - start < minLength) start = max(0L, end - minLength)
        return Range(start, end)
    }
}
