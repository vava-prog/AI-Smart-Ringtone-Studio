package com.aismart.ringtone

import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class OnlineSource(@StringRes val labelRes: Int) {
    OPENVERSE(R.string.src_openverse),
    ARCHIVE(R.string.src_archive),
    JAMENDO(R.string.src_jamendo)
}

/** Một bài nhạc có giấy phép mở tìm được trên mạng. */
data class OnlineTrack(
    val id: String,
    val source: OnlineSource,
    val title: String,
    val artist: String,
    val license: String,
    val licenseUrl: String?,
    val durationMs: Long,
    /** Link tải file; null nếu chưa biết (Internet Archive) hoặc không cho tải. */
    val audioUrl: String?,
    /** Link nghe thử trực tiếp; có thể khác link tải. */
    val previewUrl: String?,
    val pageUrl: String,
    val ext: String = "mp3"
) {
    val canUse: Boolean get() = audioUrl != null || source == OnlineSource.ARCHIVE
}

/** Một bài đang được nghe nhiều (chỉ thông tin, không có âm thanh). */
data class TrendingTrack(val title: String, val artist: String)

class RateLimitedException : IOException("rate limited")

/** Các hàm phân tích JSON thuần, tách riêng để dễ kiểm thử. */
object MusicParsers {
    private val noDerivatives = Regex("(^|[-/ ])nd($|[-/ ])", RegexOption.IGNORE_CASE)
    private val licensePath = Regex("licenses/([a-z\\-]+)/([0-9.]+)", RegexOption.IGNORE_CASE)

    /** Giấy phép có chữ ND (NoDerivatives) không cho phép chỉnh sửa/cắt nhạc. */
    fun allowsAdaptation(license: String?): Boolean {
        if (license.isNullOrBlank()) return false
        return !noDerivatives.containsMatchIn(license)
    }

    fun licenseLabelFromUrl(url: String?): String {
        if (url.isNullOrBlank()) return "CC"
        if (url.contains("publicdomain/zero")) return "CC0"
        val m = licensePath.find(url) ?: return "CC"
        return "CC ${m.groupValues[1].uppercase()} ${m.groupValues[2]}"
    }

    private fun labelFromCode(code: String, version: String?): String {
        val lower = code.lowercase()
        if (lower == "cc0" || lower == "pdm") return lower.uppercase()
        return "CC ${lower.uppercase()}" + if (!version.isNullOrBlank()) " $version" else ""
    }

    private fun JSONObject.text(key: String): String {
        val v = opt(key)
        return when (v) {
            null, JSONObject.NULL -> ""
            is JSONArray -> if (v.length() > 0) v.optString(0, "") else ""
            else -> v.toString()
        }
    }

    fun openverse(json: String): List<OnlineTrack> {
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<OnlineTrack>()
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            val url = o.text("url")
            val license = o.text("license")
            if (url.isEmpty() || !allowsAdaptation(license)) continue
            val ext = o.text("filetype").lowercase().ifEmpty {
                url.substringBefore('?').substringAfterLast('.', "mp3").lowercase()
            }
            out.add(
                OnlineTrack(
                    id = "ov:" + o.text("id"),
                    source = OnlineSource.OPENVERSE,
                    title = o.text("title").ifEmpty { "(no title)" },
                    artist = o.text("creator"),
                    license = labelFromCode(license, o.text("license_version")),
                    licenseUrl = o.text("license_url").ifEmpty { null },
                    durationMs = o.optLong("duration", 0L),
                    audioUrl = url,
                    previewUrl = url,
                    pageUrl = o.text("foreign_landing_url").ifEmpty { url },
                    ext = ext.take(5)
                )
            )
        }
        return out
    }

    fun jamendo(json: String): List<OnlineTrack> {
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<OnlineTrack>()
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            val ccUrl = o.text("license_ccurl")
            if (!allowsAdaptation(ccUrl)) continue
            val allowed = o.optBoolean("audiodownload_allowed", false)
            val download = o.text("audiodownload")
            val stream = o.text("audio")
            val id = o.text("id")
            out.add(
                OnlineTrack(
                    id = "jm:$id",
                    source = OnlineSource.JAMENDO,
                    title = o.text("name").ifEmpty { "(no title)" },
                    artist = o.text("artist_name"),
                    license = licenseLabelFromUrl(ccUrl),
                    licenseUrl = ccUrl.ifEmpty { null },
                    durationMs = o.optLong("duration", 0L) * 1000L,
                    audioUrl = if (allowed && download.isNotEmpty()) download else null,
                    previewUrl = stream.ifEmpty { null },
                    pageUrl = o.text("shareurl").ifEmpty { "https://www.jamendo.com/track/$id" }
                )
            )
        }
        return out
    }

    fun archiveDocs(json: String): List<OnlineTrack> {
        val docs = JSONObject(json).optJSONObject("response")?.optJSONArray("docs") ?: return emptyList()
        val out = ArrayList<OnlineTrack>()
        for (i in 0 until docs.length()) {
            val o = docs.optJSONObject(i) ?: continue
            val id = o.text("identifier")
            val licenseUrl = o.text("licenseurl")
            if (id.isEmpty() || !allowsAdaptation(licenseUrl)) continue
            out.add(
                OnlineTrack(
                    id = "ia:$id",
                    source = OnlineSource.ARCHIVE,
                    title = o.text("title").ifEmpty { id },
                    artist = o.text("creator"),
                    license = licenseLabelFromUrl(licenseUrl),
                    licenseUrl = licenseUrl.ifEmpty { null },
                    durationMs = 0L,
                    audioUrl = null,
                    previewUrl = null,
                    pageUrl = "https://archive.org/details/$id"
                )
            )
        }
        return out
    }

    /** Chọn file âm thanh phù hợp từ metadata của Internet Archive; trả về link tải và đuôi file. */
    fun archiveAudioFile(json: String, identifier: String): Pair<String, String>? {
        val files = JSONObject(json).optJSONArray("files") ?: return null
        var mp3: String? = null
        var ogg: String? = null
        for (i in 0 until files.length()) {
            val f = files.optJSONObject(i) ?: continue
            val name = f.text("name")
            val lower = name.lowercase()
            if (lower.endsWith(".mp3") && mp3 == null) mp3 = name
            if (lower.endsWith(".ogg") && ogg == null) ogg = name
        }
        val name = mp3 ?: ogg ?: return null
        val encoded = name.split('/').joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        return "https://archive.org/download/$identifier/$encoded" to name.substringAfterLast('.').lowercase()
    }

    fun deezerChart(json: String): List<TrendingTrack> {
        val data = JSONObject(json).optJSONArray("data") ?: return emptyList()
        val out = ArrayList<TrendingTrack>()
        for (i in 0 until data.length()) {
            val o = data.optJSONObject(i) ?: continue
            val title = o.text("title")
            if (title.isEmpty()) continue
            out.add(TrendingTrack(title, o.optJSONObject("artist")?.text("name").orEmpty()))
        }
        return out
    }
}

/** Gọi mạng cho tính năng Khám phá nhạc; chỉ dùng HTTPS. */
object MusicApi {
    private const val UA = "SmartRingtoneStudio/0.3 (Android)"
    const val MAX_DOWNLOAD_BYTES = 40L * 1024 * 1024

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun open(url: String): HttpURLConnection {
        val secure = if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url
        val conn = URL(secure).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept", "application/json, audio/*;q=0.9, */*;q=0.5")
        return conn
    }

    private fun getString(url: String): String {
        val conn = open(url)
        try {
            val code = conn.responseCode
            if (code == 429) throw RateLimitedException()
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun searchOpenverse(query: String): List<OnlineTrack> = withContext(Dispatchers.IO) {
        MusicParsers.openverse(
            getString(
                "https://api.openverse.org/v1/audio/?q=${enc(query)}&page_size=20" +
                    "&license_type=modification&mature=false"
            )
        )
    }

    suspend fun searchArchive(query: String): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val q = "($query) AND mediatype:audio AND licenseurl:*creativecommons.org*"
        MusicParsers.archiveDocs(
            getString(
                "https://archive.org/advancedsearch.php?q=${enc(q)}" +
                    "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&fl%5B%5D=licenseurl" +
                    "&rows=20&output=json"
            )
        )
    }

    /** Để trống [query] thì lấy các bài nổi bật tuần này của Jamendo. */
    suspend fun searchJamendo(query: String, clientId: String): List<OnlineTrack> =
        withContext(Dispatchers.IO) {
            val base = "https://api.jamendo.com/v3.0/tracks/?client_id=${enc(clientId)}" +
                "&format=json&limit=20&audiodownload_allowed=true&include=licenses"
            val url = if (query.isBlank()) "$base&order=popularity_week" else "$base&search=${enc(query)}"
            MusicParsers.jamendo(getString(url))
        }

    /** Tìm file âm thanh của một mục Internet Archive. */
    suspend fun resolveArchive(track: OnlineTrack): OnlineTrack? = withContext(Dispatchers.IO) {
        val identifier = track.id.removePrefix("ia:")
        val file = MusicParsers.archiveAudioFile(
            getString("https://archive.org/metadata/${enc(identifier)}"),
            identifier
        ) ?: return@withContext null
        track.copy(audioUrl = file.first, previewUrl = file.first, ext = file.second)
    }

    suspend fun trending(): List<TrendingTrack> = withContext(Dispatchers.IO) {
        MusicParsers.deezerChart(getString("https://api.deezer.com/chart/0/tracks?limit=25"))
    }

    /** Tải file về [dest]; ném IOException nếu quá [MAX_DOWNLOAD_BYTES] hoặc bị hủy. */
    suspend fun download(
        url: String,
        dest: File,
        onProgress: (Float) -> Unit,
        cancelled: () -> Boolean
    ) = withContext(Dispatchers.IO) {
        val conn = open(url)
        try {
            val code = conn.responseCode
            if (code == 429) throw RateLimitedException()
            if (code !in 200..299) throw IOException("HTTP $code")
            val total = conn.contentLengthLong
            if (total > MAX_DOWNLOAD_BYTES) throw IOException("too big")
            var read = 0L
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        if (cancelled()) throw kotlinx.coroutines.CancellationException("cancelled")
                        val n = input.read(buf)
                        if (n < 0) break
                        read += n
                        if (read > MAX_DOWNLOAD_BYTES) throw IOException("too big")
                        output.write(buf, 0, n)
                        if (total > 0) onProgress(read.toFloat() / total)
                    }
                }
            }
        } catch (e: Exception) {
            dest.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }
}
