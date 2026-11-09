package com.aismart.ringtone

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/** Trạng thái và thao tác của tab Khám phá nhạc. */
class DiscoverController(
    private val app: Application,
    private val scope: CoroutineScope,
    private val jamendoClientId: () -> String
) {
    var query by mutableStateOf("")
        private set
    var source by mutableStateOf(OnlineSource.OPENVERSE)
        private set
    var results by mutableStateOf<List<OnlineTrack>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var searched by mutableStateOf(false)
        private set
    var error by mutableStateOf<Msg?>(null)
        private set
    var trending by mutableStateOf<List<TrendingTrack>>(emptyList())
        private set
    var trendingLoading by mutableStateOf(false)
        private set
    var trendingError by mutableStateOf<Msg?>(null)
        private set
    /** Bài đang được xử lý (tìm file hoặc tải về). */
    var busyId by mutableStateOf<String?>(null)
        private set
    var downloadProgress by mutableStateOf(0f)
        private set

    private var searchJob: Job? = null
    private var trendingJob: Job? = null

    fun updateQuery(text: String) {
        query = text
    }

    fun chooseSource(value: OnlineSource) {
        source = value
        results = emptyList()
        searched = false
        error = null
    }

    fun clearError() {
        error = null
    }

    private fun describe(e: Exception): Msg = when (e) {
        is RateLimitedException -> Msg(R.string.discover_rate_limited)
        else -> Msg(R.string.discover_error_fmt, e.message ?: app.getString(R.string.msg_unknown_error))
    }

    fun search() {
        val text = query.trim()
        val clientId = jamendoClientId().trim()
        if (text.isEmpty() && !(source == OnlineSource.JAMENDO && clientId.isNotEmpty())) return
        searchJob?.cancel()
        loading = true
        error = null
        searchJob = scope.launch {
            try {
                results = when (source) {
                    OnlineSource.OPENVERSE -> MusicApi.searchOpenverse(text)
                    OnlineSource.ARCHIVE -> MusicApi.searchArchive(text)
                    OnlineSource.JAMENDO -> MusicApi.searchJamendo(text, clientId)
                }
                searched = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                results = emptyList()
                error = describe(e)
            } finally {
                loading = false
            }
        }
    }

    fun loadTrending() {
        if (trendingLoading) return
        trendingLoading = true
        trendingError = null
        trendingJob?.cancel()
        trendingJob = scope.launch {
            try {
                trending = MusicApi.trending()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                trendingError = describe(e)
            } finally {
                trendingLoading = false
            }
        }
    }

    /** Tìm bản có giấy phép mở cho một bài đang hot. */
    fun searchForTrend(track: TrendingTrack) {
        query = track.title
        source = OnlineSource.OPENVERSE
        search()
    }

    /** Với Internet Archive, tra file âm thanh trước; các nguồn khác trả lại nguyên bài. */
    suspend fun resolve(track: OnlineTrack): OnlineTrack? {
        if (track.audioUrl != null || track.previewUrl != null) return track
        if (track.source != OnlineSource.ARCHIVE) return null
        busyId = track.id
        return try {
            MusicApi.resolveArchive(track) ?: run {
                error = Msg(R.string.discover_no_audio)
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = describe(e)
            null
        } finally {
            busyId = null
        }
    }

    /** Tải bài về bộ nhớ tạm của ứng dụng; trả về null nếu lỗi (đã báo trong [error]). */
    suspend fun download(track: OnlineTrack): File? {
        busyId = track.id
        downloadProgress = 0f
        error = null
        return try {
            val resolved = if (track.audioUrl == null) resolve(track).also { busyId = track.id } else track
            val url = resolved?.audioUrl
            if (resolved == null || url == null) {
                if (error == null) error = Msg(R.string.discover_no_download)
                return null
            }
            app.cacheDir.listFiles { f -> f.name.startsWith("discover_") }?.forEach { it.delete() }
            val safeId = resolved.id.replace(Regex("[^A-Za-z0-9]"), "_").take(40)
            val file = File(app.cacheDir, "discover_$safeId.${resolved.ext.ifEmpty { "mp3" }}")
            MusicApi.download(url, file, { downloadProgress = it }, { false })
            file
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            error = if (e.message == "too big") Msg(R.string.discover_too_big) else describe(e)
            null
        } catch (e: Exception) {
            error = describe(e)
            null
        } finally {
            busyId = null
        }
    }
}
