package com.aismart.ringtone

import java.net.URI
import java.util.Locale

/**
 * Nhận dạng link YouTube / TikTok. Chỉ dùng để mở lại video và ghi nguồn,
 * ứng dụng không tải âm thanh từ các nền tảng này.
 */
object SourceLinks {
    enum class Platform { YOUTUBE, TIKTOK }

    data class Link(val url: String, val platform: Platform)

    private val urlRegex = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /** Lấy URL đầu tiên trong đoạn văn bản (ví dụ nội dung chia sẻ từ TikTok). */
    fun extractUrl(text: String?): String? {
        val match = text?.let { urlRegex.find(it) } ?: return null
        return match.value.trimEnd('.', ',', ')', ']', '!', '?', ';')
    }

    /** Trả về link đã làm sạch nếu là YouTube hoặc TikTok, ngược lại trả về null. */
    fun parse(input: String?): Link? {
        val raw = input?.trim().orEmpty()
        if (raw.isEmpty()) return null

        val url = extractUrl(raw)
            ?: if (raw.any { it.isWhitespace() }) return null else "https://$raw"

        val host = try {
            URI(url).host?.lowercase(Locale.ROOT)
        } catch (e: Exception) {
            null
        } ?: return null

        val platform = when {
            host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") ->
                Platform.YOUTUBE
            host == "tiktok.com" || host.endsWith(".tiktok.com") -> Platform.TIKTOK
            else -> return null
        }
        return Link(url, platform)
    }
}
