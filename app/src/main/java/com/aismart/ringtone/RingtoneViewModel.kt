package com.aismart.ringtone

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

/** Một thông báo có thể dịch: id chuỗi + tham số. */
class Msg(@StringRes val res: Int, vararg val args: Any)

enum class PlayKind { RANGE, RESULT }

@OptIn(UnstableApi::class)
class RingtoneViewModel(private val app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)
    private val history = HistoryStore(app)

    // ---- Trạng thái hiển thị ----
    var sourceUri by mutableStateOf<Uri?>(null)
        private set
    var sourceName by mutableStateOf<String?>(null)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var startMs by mutableLongStateOf(0L)
        private set
    var endMs by mutableLongStateOf(0L)
        private set
    var profile by mutableStateOf<List<Float>?>(null)
        private set
    var analyzing by mutableStateOf(false)
        private set

    // Giá trị mặc định (màn hình Cài đặt) và giá trị đang dùng cho lần cắt hiện tại
    var defaultFadeIn by mutableFloatStateOf(settings.fadeInSec)
        private set
    var defaultFadeOut by mutableFloatStateOf(settings.fadeOutSec)
        private set
    var defaultVolume by mutableIntStateOf(settings.volumePercent)
        private set
    var defaultBitrate by mutableIntStateOf(settings.bitrateKbps)
        private set
    var themeMode by mutableIntStateOf(settings.themeMode)
        private set

    var fadeInSec by mutableFloatStateOf(settings.fadeInSec)
        private set
    var fadeOutSec by mutableFloatStateOf(settings.fadeOutSec)
        private set
    var volumePercent by mutableIntStateOf(settings.volumePercent)
        private set
    var bitrateKbps by mutableIntStateOf(settings.bitrateKbps)
        private set

    var historyEntries by mutableStateOf<List<HistoryEntry>>(emptyList())
        private set
    var playingHistoryId by mutableStateOf<String?>(null)
        private set

    var exporting by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var outputFile by mutableStateOf<File?>(null)
        private set
    var target by mutableStateOf(RingtoneTarget.RINGTONE)
        private set
    var playing by mutableStateOf<PlayKind?>(null)
        private set
    var message by mutableStateOf(Msg(R.string.msg_start_hint))
        private set

    // ---- Nội bộ ----
    private var analysisJob: Job? = null
    private var progressJob: Job? = null
    private var transformer: Transformer? = null
    private var pendingOutput: File? = null
    private var player: ExoPlayer? = null

    val hasSource: Boolean get() = sourceUri != null && durationMs > 0
    val clipMs: Long get() = endMs - startMs

    init {
        viewModelScope.launch {
            historyEntries = withContext(Dispatchers.IO) { history.load() }
        }
    }

    fun showMessage(@StringRes res: Int) {
        message = Msg(res)
    }

    // ---- Chọn file nguồn ----
    fun loadSource(uri: Uri) {
        cancelExport(silent = true)
        stopPlayback()
        analysisJob?.cancel()
        sourceUri = uri
        sourceName = null
        durationMs = 0L
        startMs = 0L
        endMs = 0L
        profile = null
        analyzing = false
        outputFile = null
        fadeInSec = defaultFadeIn
        fadeOutSec = defaultFadeOut
        volumePercent = defaultVolume
        bitrateKbps = defaultBitrate
        message = Msg(R.string.msg_loading)

        viewModelScope.launch {
            val (name, duration) = withContext(Dispatchers.IO) {
                AudioAnalyzer.readDisplayName(app, uri) to AudioAnalyzer.readDurationMs(app, uri)
            }
            if (sourceUri != uri) return@launch
            sourceName = name
            if (duration == null) {
                message = Msg(R.string.msg_no_duration)
                return@launch
            }
            durationMs = duration
            startMs = 0L
            endMs = min(duration, RingtoneMath.DEFAULT_CLIP_MS)
            message = Msg(R.string.msg_picked)
            startAnalysis(uri, duration)
        }
    }

    private fun startAnalysis(uri: Uri, duration: Long) {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            analyzing = true
            val result = AudioAnalyzer.energyProfile(app, uri, duration)
            if (sourceUri == uri) {
                profile = result
                analyzing = false
                if (result == null) message = Msg(R.string.msg_analysis_failed)
            }
        }
    }

    // ---- Chọn đoạn ----
    fun setRange(newStartMs: Long, newEndMs: Long) {
        if (!hasSource) return
        val range = RingtoneMath.clampRange(startMs, newStartMs, newEndMs, durationMs)
        if (range.startMs == startMs && range.endMs == endMs) return
        startMs = range.startMs
        endMs = range.endMs
        onSettingsChanged()
    }

    fun smartPick() {
        val data = profile ?: return
        if (!hasSource) return
        val length = (if (clipMs >= RingtoneMath.MIN_CLIP_MS) clipMs else RingtoneMath.DEFAULT_CLIP_MS)
            .coerceAtMost(durationMs)
        val windowBuckets = (length / RingtoneMath.BUCKET_MS).toInt().coerceAtLeast(1)
        val bucket = RingtoneMath.smartPickStartBucket(data, windowBuckets)
        val newStart = (bucket * RingtoneMath.BUCKET_MS).coerceAtMost(durationMs - length).coerceAtLeast(0L)
        startMs = newStart
        endMs = newStart + length
        onSettingsChanged()
        message = Msg(R.string.msg_smart_done)
    }

    // ---- Tùy chọn âm thanh ----
    fun setFadeIn(seconds: Float) {
        fadeInSec = seconds
        onSettingsChanged()
    }

    fun setFadeOut(seconds: Float) {
        fadeOutSec = seconds
        onSettingsChanged()
    }

    fun setVolume(percent: Int) {
        volumePercent = percent
        onSettingsChanged()
    }

    fun setBitrate(kbps: Int) {
        bitrateKbps = kbps
        onSettingsChanged()
    }

    fun selectTarget(newTarget: RingtoneTarget) {
        target = newTarget
    }

    /** Đổi bất kỳ thiết lập nào thì kết quả cũ không còn khớp nữa. */
    private fun onSettingsChanged() {
        if (outputFile != null && !exporting) {
            if (playing == PlayKind.RESULT) stopPlayback()
            outputFile = null
        }
    }

    // ---- Nghe thử ----
    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val created = ExoPlayer.Builder(app).build()
        created.setAudioAttributes(AudioAttributes.DEFAULT, true)
        created.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) clearPlaying()
            }

            override fun onPlayerError(error: PlaybackException) {
                clearPlaying()
                message = Msg(R.string.msg_playback_error)
            }
        })
        player = created
        return created
    }

    fun playRange() {
        val uri = sourceUri ?: return
        if (!hasSource) return
        val item = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()
        play(item, PlayKind.RANGE)
    }

    fun playResult() {
        val file = outputFile ?: return
        play(MediaItem.fromUri(Uri.fromFile(file)), PlayKind.RESULT)
    }

    private fun play(item: MediaItem, kind: PlayKind?, historyId: String? = null) {
        val p = ensurePlayer()
        p.setMediaItem(item)
        p.prepare()
        p.playWhenReady = true
        playing = kind
        playingHistoryId = historyId
    }

    fun playHistory(entry: HistoryEntry) {
        play(MediaItem.fromUri(Uri.fromFile(history.file(entry))), null, entry.id)
    }

    fun stopPlayback() {
        player?.stop()
        clearPlaying()
    }

    private fun clearPlaying() {
        playing = null
        playingHistoryId = null
    }

    // ---- Xuất file ----
    fun export() {
        val uri = sourceUri ?: return
        if (!hasSource || exporting) return
        stopPlayback()

        val clip = clipMs
        val bitrate = bitrateKbps
        val clipUs = clip * 1000L
        val maxFadeUs = clipUs / 2
        val fadeInUs = min((fadeInSec * 1_000_000f).toLong(), maxFadeUs)
        val fadeOutUs = min((fadeOutSec * 1_000_000f).toLong(), maxFadeUs)
        val processor = FadeGainAudioProcessor(clipUs, fadeInUs, fadeOutUs, volumePercent / 100f)

        app.cacheDir.listFiles { f -> f.name.startsWith("ringtone_") && f.name.endsWith(".m4a") }
            ?.forEach { it.delete() }
        val out = File(app.cacheDir, "ringtone_${System.currentTimeMillis()}.m4a")

        outputFile = null
        pendingOutput = out
        progress = 0f
        exporting = true
        message = Msg(R.string.msg_processing)

        try {
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                )
                .build()

            val edited = EditedMediaItem.Builder(mediaItem)
                .setRemoveVideo(true)
                .setEffects(Effects(listOf(processor), emptyList()))
                .build()

            val encoderFactory = DefaultEncoderFactory.Builder(app)
                .setRequestedAudioEncoderSettings(
                    AudioEncoderSettings.Builder().setBitrate(bitrateKbps * 1000).build()
                )
                .build()

            val built = Transformer.Builder(app)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoderFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        progressJob?.cancel()
                        transformer = null
                        pendingOutput = null
                        exporting = false
                        progress = 1f
                        outputFile = out
                        message = Msg(R.string.msg_export_done, RingtoneMath.formatSize(out.length()))
                        recordHistory(out, clip, bitrate)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        progressJob?.cancel()
                        transformer = null
                        pendingOutput = null
                        exporting = false
                        out.delete()
                        message = Msg(
                            R.string.msg_export_error,
                            exportException.message ?: app.getString(R.string.msg_unknown_error)
                        )
                    }
                })
                .build()

            transformer = built
            built.start(edited, out.absolutePath)
            trackProgress(built)
        } catch (e: Exception) {
            transformer = null
            pendingOutput = null
            exporting = false
            out.delete()
            message = Msg(
                R.string.msg_export_error,
                e.message ?: app.getString(R.string.msg_unknown_error)
            )
        }
    }

    private fun trackProgress(active: Transformer) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            val holder = ProgressHolder()
            while (isActive && exporting) {
                if (active.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    progress = (holder.progress / 100f).coerceIn(0f, 1f)
                }
                delay(250)
            }
        }
    }

    fun cancelExport(silent: Boolean = false) {
        val active = transformer
        if (!exporting || active == null) return
        progressJob?.cancel()
        try {
            active.cancel()
        } catch (_: Exception) {
        }
        transformer = null
        exporting = false
        pendingOutput?.delete()
        pendingOutput = null
        if (!silent) message = Msg(R.string.msg_export_cancelled)
    }

    // ---- Lịch sử ----
    private fun recordHistory(file: File, clip: Long, bitrate: Int) {
        val title = RingtoneMath.safeTitle(sourceName)
        viewModelScope.launch {
            try {
                val entry = withContext(Dispatchers.IO) { history.addFrom(file, title, clip, bitrate) }
                historyEntries = listOf(entry) + historyEntries
            } catch (_: Exception) {
            }
        }
    }

    fun historyFile(entry: HistoryEntry): File = history.file(entry)

    fun deleteHistory(entry: HistoryEntry) {
        if (playingHistoryId == entry.id) stopPlayback()
        historyEntries = historyEntries.filter { it.id != entry.id }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { history.delete(entry) }
            } catch (_: Exception) {
            }
        }
    }

    // ---- Cài đặt mặc định ----
    fun setDefaultFadeIn(value: Float) {
        defaultFadeIn = value
        settings.fadeInSec = value
    }

    fun setDefaultFadeOut(value: Float) {
        defaultFadeOut = value
        settings.fadeOutSec = value
    }

    fun setDefaultVolume(value: Int) {
        defaultVolume = value
        settings.volumePercent = value
    }

    fun setDefaultBitrate(value: Int) {
        defaultBitrate = value
        settings.bitrateKbps = value
    }

    fun selectThemeMode(mode: Int) {
        themeMode = mode
        settings.themeMode = mode
    }

    // ---- Lưu và đặt làm nhạc chuông ----
    fun saveToDevice(setAsDefault: Boolean) {
        val file = outputFile ?: return
        saveFile(file, RingtoneMath.safeTitle(sourceName), target, setAsDefault)
    }

    fun saveFile(file: File, title: String, chosen: RingtoneTarget, setAsDefault: Boolean) {
        viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    RingtoneSaver.saveToLibrary(app, file, title, chosen)
                }
                if (setAsDefault) {
                    RingtoneSaver.setAsDefault(app, uri, chosen)
                    message = Msg(R.string.msg_set_default_done, app.getString(chosen.labelRes))
                } else {
                    message = Msg(R.string.msg_saved, chosen.mediaDir)
                }
            } catch (e: Exception) {
                message = Msg(
                    R.string.msg_save_failed,
                    e.message ?: app.getString(R.string.msg_unknown_error)
                )
            }
        }
    }

    override fun onCleared() {
        cancelExport(silent = true)
        analysisJob?.cancel()
        progressJob?.cancel()
        player?.release()
        player = null
        super.onCleared()
    }
}
