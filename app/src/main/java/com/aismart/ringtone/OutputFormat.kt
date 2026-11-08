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
    WAV("wav", "audio/x-wav", R.string.format_wav);

    /** WAV không nén nên không có khái niệm bitrate. */
    val usesBitrate: Boolean get() = this != WAV

    companion object {
        fun fromExt(ext: String?): OutputFormat =
            values().firstOrNull { it.ext.equals(ext, ignoreCase = true) } ?: M4A
    }
}
