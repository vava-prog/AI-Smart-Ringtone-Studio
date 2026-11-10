package com.aismart.ringtone

import androidx.annotation.StringRes

/** Định dạng file đầu ra. */
enum class OutputFormat(
    val ext: String,
    val mime: String,
    @StringRes val labelRes: Int
) {
    /** AAC trong container MP4, mã hóa bằng Media3 Transformer. */
    M4A("m4a", "audio/mp4", R.string.format_m4a),

    /** MP3 CBR, mã hóa bằng LAME (bản Java đi kèm trong dự án). */
    MP3("mp3", "audio/mpeg", R.string.format_mp3),

    /** PCM 16-bit không nén. */
    WAV("wav", "audio/x-wav", R.string.format_wav),

    /** FLAC nén không mất dữ liệu, bộ mã hóa viết bằng Java thuần. */
    FLAC("flac", "audio/flac", R.string.format_flac);

    /** WAV và FLAC là định dạng không mất dữ liệu nên không có khái niệm bitrate. */
    val usesBitrate: Boolean get() = this != WAV && this != FLAC

    companion object {
        fun fromExt(ext: String?): OutputFormat =
            values().firstOrNull { it.ext.equals(ext, ignoreCase = true) } ?: M4A
    }
}

/** Chọn tần số lấy mẫu gần nhất mà MP3 hỗ trợ khi file gốc dùng tần số khác. */
object Mp3Rates {
    private val SUPPORTED = intArrayOf(8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000)

    fun target(sampleRate: Int): Int {
        if (sampleRate in SUPPORTED) return sampleRate
        if (sampleRate > 48000) return if (sampleRate % 44100 == 0) 44100 else 48000
        return SUPPORTED.firstOrNull { it >= sampleRate } ?: 48000
    }
}
