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
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/** Một thông báo có thể dịch: id chuỗi + tham số. */
class Msg(@StringRes val res: Int, vararg val args: Any)

/** Ghi nguồn cho nhạc lấy từ mạng: nội dung hiển thị và link trang gốc. */
data class Credit(val text: String, val url: String)

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
    /** Các đoạn do bộ chọn thông minh gợi ý (tối đa 3). */
    var pickCandidates by mutableStateOf<List<PickCandidate>>(emptyList())
        private set
    /** Tempo ước tính (BPM) nếu nhận ra được nhịp. */
    var bpm by mutableStateOf<Int?>(null)
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
    var defaultFormat by mutableStateOf(OutputFormat.fromExt(settings.formatExt))
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
    var format by mutableStateOf(OutputFormat.fromExt(settings.formatExt))
        private set

    // Hiệu ứng âm thanh (giữ nguyên khi đổi file trong cùng phiên)
    var preampDb by mutableFloatStateOf(0f)
        private set
    var eqBands by mutableStateOf(EqPreset.FLAT.bands)
        private set
    var customPresets by mutableStateOf(CustomPresets.parse(settings.customPresetsJson))
        private set
    var normalizeOn by mutableStateOf(false)
        private set
    var limiterOn by mutableStateOf(false)
        private set

    // Phóng to quanh điểm cắt
    var zoomOn by mutableStateOf(false)
        private set
    var zoomStartView by mutableStateOf<ZoomView?>(null)
        private set
    var zoomEndView by mutableStateOf<ZoomView?>(null)
        private set

    /** Kết quả của lần chạy lệnh trợ lý gần nhất. */
    var agentLog by mutableStateOf<List<Msg>>(emptyList())
        private set

    /** Tăng lên khi trợ lý muốn mở tab Khám phá. */
    var openDiscoverTick by mutableIntStateOf(0)
        private set

    var jamendoClientId by mutableStateOf(settings.jamendoClientId)
        private set

    /** Nguồn/giấy phép của bài đang mở (nếu lấy từ tab Khám phá). */
    var credit by mutableStateOf<Credit?>(null)
        private set

    val discover = DiscoverController(app, viewModelScope) { jamendoClientId }

    var sourceLink by mutableStateOf("")
        private set
    var openCreateTick by mutableIntStateOf(0)
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
    private val previewProcessor = PreviewEffectsAudioProcessor()
    private var analysisJob: Job? = null
    private var analysisData: TrackAnalysis? = null
    private var progressJob: Job? = null
    private var encodeJob: Job? = null
    private var measureJob: Job? = null
    private var zoomJob: Job? = null
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

    fun updateSourceLink(text: String) {
        sourceLink = text
    }

    /** Nhận link YouTube/TikTok được chia sẻ từ app khác và chuyển sang tab Tạo mới. */
    fun receiveLink(url: String) {
        sourceLink = url
        openCreateTick++
        message = Msg(R.string.msg_link_received)
    }

    fun showMessage(@StringRes res: Int) {
        message = Msg(res)
    }

    // ---- Chọn file nguồn ----
    fun loadSource(uri: Uri, newCredit: Credit? = null) {
        cancelExport(silent = true)
        stopPlayback()
        credit = newCredit
        analysisJob?.cancel()
        zoomJob?.cancel()
        zoomStartView = null
        zoomEndView = null
        sourceUri = uri
        sourceName = null
        durationMs = 0L
        startMs = 0L
        endMs = 0L
        profile = null
        analysisData = null
        pickCandidates = emptyList()
        bpm = null
        analyzing = false
        outputFile = null
        fadeInSec = defaultFadeIn
        fadeOutSec = defaultFadeOut
        volumePercent = defaultVolume
        bitrateKbps = defaultBitrate
        format = defaultFormat
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
            scheduleZoom()
        }
    }

    private fun startAnalysis(uri: Uri, duration: Long) {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            analyzing = true
            val result = AudioAnalyzer.analyze(app, uri, duration)
            if (sourceUri == uri) {
                analysisData = result
                profile = result?.rms
                bpm = result?.beat
                    ?.takeIf { it.confidence >= BeatTracker.MIN_CONFIDENCE }
                    ?.bpm?.roundToInt()
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
        scheduleZoom()
    }

    /** Dịch điểm bắt đầu [deltaMs] mili giây (âm = lùi). */
    fun nudgeStart(deltaMs: Long) = setRange(startMs + deltaMs, endMs)

    /** Dịch điểm kết thúc [deltaMs] mili giây (âm = lùi). */
    fun nudgeEnd(deltaMs: Long) = setRange(startMs, endMs + deltaMs)

    fun toggleZoom(on: Boolean) {
        zoomOn = on
        if (on) {
            scheduleZoom()
        } else {
            zoomJob?.cancel()
            zoomStartView = null
            zoomEndView = null
        }
    }

    private fun scheduleZoom() {
        if (!zoomOn) return
        val uri = sourceUri ?: return
        if (durationMs <= 0L) return
        zoomJob?.cancel()
        val s = startMs
        val e = endMs
        val d = durationMs
        zoomJob = viewModelScope.launch {
            delay(350)
            val startView = withContext(Dispatchers.Default) { zoomAround(uri, s, d) { !isActive } }
            val endView = withContext(Dispatchers.Default) { zoomAround(uri, e, d) { !isActive } }
            if (sourceUri == uri) {
                zoomStartView = startView
                zoomEndView = endView
            }
        }
    }

    private fun zoomAround(uri: Uri, boundaryMs: Long, duration: Long, cancelled: () -> Boolean): ZoomView? {
        return try {
            val from = (boundaryMs - ZOOM_HALF_MS).coerceAtLeast(0L)
            val to = (boundaryMs + ZOOM_HALF_MS).coerceAtMost(duration)
            if (to - from < 200L) return null
            val result = RangeScanner.scan(app, uri, from, to, ZOOM_BARS, cancelled)
            val top = result.buckets.maxOrNull() ?: 0f
            if (top <= 0f) return null
            ZoomView(
                peaks = result.buckets.map { it / top },
                marker = (boundaryMs - from).toFloat() / (to - from)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    fun smartPick(kind: PickKind? = null) {
        val data = analysisData ?: return
        if (!hasSource) return
        val length = (if (clipMs >= RingtoneMath.MIN_CLIP_MS) clipMs else RingtoneMath.DEFAULT_CLIP_MS)
            .coerceAtMost(durationMs)
        val candidates = SmartPicker.pick(data, length, durationMs)
        if (candidates.isEmpty()) return
        pickCandidates = candidates
        applyCandidate(kind?.let { k -> candidates.firstOrNull { it.kind == k } } ?: candidates[0])
        message = Msg(R.string.msg_smart_done)
    }

    /** Áp dụng một đoạn gợi ý. */
    fun applyCandidate(candidate: PickCandidate) {
        if (!hasSource) return
        setRange(candidate.startMs, candidate.endMs)
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

    fun selectFormat(newFormat: OutputFormat) {
        format = newFormat
        onSettingsChanged()
    }

    /** Trợ lý chạy trên máy: hiểu lệnh đơn giản và điều khiển các chức năng của ứng dụng. */
    fun runAgent(text: String) {
        val actions = AgentParser.parse(text)
        if (actions.isEmpty()) {
            agentLog = listOf(Msg(R.string.agent_unknown))
            return
        }
        val log = ArrayList<Msg>()
        var needSource = false
        for (action in actions) {
            when (action) {
                is AgentAction.SetLength -> if (hasSource) {
                    setRange(startMs, startMs + action.ms)
                    log.add(Msg(R.string.agent_set_length, RingtoneMath.formatTime(clipMs)))
                } else needSource = true
                is AgentAction.SetStart -> if (hasSource) {
                    setRange(action.ms, action.ms + clipMs)
                    log.add(Msg(R.string.agent_set_range, RingtoneMath.formatTime(startMs), RingtoneMath.formatTime(endMs)))
                } else needSource = true
                is AgentAction.SetRange -> if (hasSource) {
                    setRange(action.startMs, action.endMs)
                    log.add(Msg(R.string.agent_set_range, RingtoneMath.formatTime(startMs), RingtoneMath.formatTime(endMs)))
                } else needSource = true
                is AgentAction.Smart -> if (!hasSource) {
                    needSource = true
                } else if (analysisData == null) {
                    log.add(Msg(R.string.agent_no_analysis))
                } else {
                    smartPick(action.kind)
                    log.add(Msg(R.string.agent_smart_done, RingtoneMath.formatTime(startMs), RingtoneMath.formatTime(endMs)))
                }
                is AgentAction.FadeIn -> {
                    setFadeIn(action.seconds)
                    log.add(Msg(R.string.agent_fade_in, oneDecimal(action.seconds)))
                }
                is AgentAction.FadeOut -> {
                    setFadeOut(action.seconds)
                    log.add(Msg(R.string.agent_fade_out, oneDecimal(action.seconds)))
                }
                is AgentAction.VolumeSet -> {
                    setVolume(action.percent)
                    log.add(Msg(R.string.agent_volume, action.percent))
                }
                is AgentAction.VolumeAdjust -> {
                    setVolume((volumePercent + action.delta).coerceIn(50, 150))
                    log.add(Msg(R.string.agent_volume, volumePercent))
                }
                is AgentAction.Format -> {
                    selectFormat(action.format)
                    log.add(Msg(R.string.agent_format, app.getString(action.format.labelRes)))
                }
                is AgentAction.Bitrate -> {
                    setBitrate(action.kbps)
                    log.add(Msg(R.string.agent_bitrate, action.kbps))
                }
                is AgentAction.Preset -> {
                    selectPreset(action.preset)
                    log.add(Msg(R.string.agent_preset, app.getString(action.preset.labelRes)))
                }
                is AgentAction.Normalize -> {
                    toggleNormalize(action.on)
                    log.add(Msg(if (action.on) R.string.agent_normalize_on else R.string.agent_normalize_off))
                }
                is AgentAction.Limiter -> {
                    toggleLimiter(action.on)
                    log.add(Msg(if (action.on) R.string.agent_limiter_on else R.string.agent_limiter_off))
                }
                is AgentAction.Target -> {
                    val chosen = when (action.target) {
                        AgentTarget.RINGTONE -> RingtoneTarget.RINGTONE
                        AgentTarget.NOTIFICATION -> RingtoneTarget.NOTIFICATION
                        AgentTarget.ALARM -> RingtoneTarget.ALARM
                    }
                    selectTarget(chosen)
                    log.add(Msg(R.string.agent_target, app.getString(chosen.labelRes)))
                }
                is AgentAction.Search -> {
                    discover.updateQuery(action.query)
                    discover.chooseSource(OnlineSource.OPENVERSE)
                    discover.search()
                    openDiscoverTick++
                    log.add(Msg(R.string.agent_search, action.query))
                }
                AgentAction.Export -> if (!hasSource) {
                    needSource = true
                } else if (!exporting) {
                    export()
                    log.add(Msg(R.string.agent_export))
                }
            }
        }
        if (needSource) log.add(Msg(R.string.agent_need_source))
        agentLog = log
    }

    private fun oneDecimal(value: Float): String = String.format(java.util.Locale.getDefault(), "%.1f", value)

    fun updateJamendoId(text: String) {
        jamendoClientId = text.trim()
        settings.jamendoClientId = text
        if (jamendoClientId.isEmpty() && discover.source == OnlineSource.JAMENDO) {
            discover.chooseSource(OnlineSource.OPENVERSE)
        }
    }

    /** Nghe thử một bài trực tuyến; bấm lần nữa để dừng. */
    fun previewOnline(track: OnlineTrack) {
        val key = "online:${track.id}"
        if (playingHistoryId == key) {
            stopPlayback()
            return
        }
        viewModelScope.launch {
            val resolved = discover.resolve(track) ?: return@launch
            val url = resolved.previewUrl ?: resolved.audioUrl ?: return@launch
            play(MediaItem.fromUri(Uri.parse(url)), null, key)
        }
    }

    /** Tải bài về, mở trong tab Tạo mới kèm thông tin nguồn/giấy phép. */
    fun useOnlineTrack(track: OnlineTrack) {
        stopPlayback()
        viewModelScope.launch {
            val file = discover.download(track) ?: return@launch
            val artist = if (track.artist.isNotBlank()) " — ${track.artist}" else ""
            loadSource(
                Uri.fromFile(file),
                Credit("${track.title}$artist (${track.license})", track.pageUrl)
            )
            openCreateTick++
        }
    }

    fun saveCustomPreset(rawName: String) {
        val name = CustomPresets.cleanName(rawName)
        if (name.isEmpty()) return
        customPresets = CustomPresets.upsert(customPresets, CustomPreset(name, preampDb, eqBands))
        settings.customPresetsJson = CustomPresets.toJson(customPresets)
    }

    fun applyCustomPreset(preset: CustomPreset) {
        preampDb = preset.preampDb
        eqBands = preset.bands
        onSettingsChanged()
    }

    fun deleteCustomPreset(preset: CustomPreset) {
        customPresets = customPresets.filterNot { it.name == preset.name }
        settings.customPresetsJson = CustomPresets.toJson(customPresets)
    }

    fun selectPreset(preset: EqPreset) {
        preampDb = preset.preampDb
        eqBands = preset.bands
        onSettingsChanged()
    }

    fun changePreamp(db: Float) {
        preampDb = db
        onSettingsChanged()
    }

    fun changeBand(index: Int, db: Float) {
        if (index !in 0 until EqConfig.COUNT) return
        eqBands = eqBands.toMutableList().also { it[index] = db }
        onSettingsChanged()
    }

    fun toggleNormalize(on: Boolean) {
        normalizeOn = on
        if (on) limiterOn = true
        onSettingsChanged()
    }

    fun toggleLimiter(on: Boolean) {
        limiterOn = on
        onSettingsChanged()
    }

    fun selectTarget(newTarget: RingtoneTarget) {
        target = newTarget
    }

    /** Đổi bất kỳ thiết lập nào thì kết quả cũ không còn khớp nữa. */
    private fun onSettingsChanged() {
        syncPreview()
        if (outputFile != null && !exporting) {
            if (playing == PlayKind.RESULT) stopPlayback()
            outputFile = null
        }
    }

    // ---- Nghe thử ----
    /** Đẩy cấu hình hiệu ứng hiện tại vào bộ xử lý nghe thử. */
    private fun syncPreview() {
        previewProcessor.update(
            EffectSettings(preampDb, eqBands, 1f, limiterOn),
            volumePercent / 100f
        )
    }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val renderers = object : DefaultRenderersFactory(app) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf<AudioProcessor>(previewProcessor))
                    .build()
            }
        }
        val created = ExoPlayer.Builder(app, renderers).build()
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

    /** Nghe 2 giây quanh điểm bắt đầu (đầu đoạn) hoặc điểm kết thúc (cuối đoạn). */
    fun playEdge(atStart: Boolean) {
        val uri = sourceUri ?: return
        if (!hasSource) return
        val from = if (atStart) startMs else maxOf(startMs, endMs - EDGE_PREVIEW_MS)
        val to = if (atStart) minOf(endMs, startMs + EDGE_PREVIEW_MS) else endMs
        val item = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(from)
                    .setEndPositionMs(to)
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
        // Chỉ nghe thử đoạn gốc mới áp dụng hiệu ứng trực tiếp
        previewProcessor.enabled = kind == PlayKind.RANGE
        syncPreview()
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

        if (!normalizeOn) {
            startExport(uri, 1f)
            return
        }

        // Chuẩn hóa: đo đỉnh biên độ của đoạn đã chọn trước rồi mới xuất
        val from = startMs
        val to = endMs
        outputFile = null
        progress = 0f
        exporting = true
        message = Msg(R.string.msg_measuring)
        measureJob?.cancel()
        measureJob = viewModelScope.launch {
            try {
                val peak = withContext(Dispatchers.Default) {
                    RangeScanner.scan(app, uri, from, to, 1) { !isActive }.peak
                }
                val normalizeGain = if (peak > 0.001f) {
                    (NORMALIZE_TARGET / peak).coerceIn(0.25f, 8f)
                } else {
                    1f
                }
                startExport(uri, normalizeGain)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Exception) {
                startExport(uri, 1f)
            }
        }
    }

    private fun startExport(uri: Uri, normalizeGain: Float) {
        val effects = EffectSettings(preampDb, eqBands, normalizeGain, limiterOn)
        val clip = clipMs
        val fmt = format
        val bitrate = if (fmt.usesBitrate) bitrateKbps else 0
        val link = SourceLinks.parse(sourceLink)?.url ?: credit?.url
        val clipUs = clip * 1000L
        val maxFadeUs = clipUs / 2
        val fadeInUs = min((fadeInSec * 1_000_000f).toLong(), maxFadeUs)
        val fadeOutUs = min((fadeOutSec * 1_000_000f).toLong(), maxFadeUs)
        val gain = volumePercent / 100f

        app.cacheDir.listFiles { f -> f.name.startsWith("ringtone_") }?.forEach { it.delete() }
        val out = File(app.cacheDir, "ringtone_${System.currentTimeMillis()}.${fmt.ext}")

        outputFile = null
        pendingOutput = out
        progress = 0f
        exporting = true
        message = Msg(R.string.msg_processing)

        // MP3 và WAV: tự giải mã và mã hóa; M4A: dùng Media3 Transformer
        if (fmt != OutputFormat.M4A) {
            exportWithEncoder(uri, fmt, out, clip, bitrate, link, fadeInUs, fadeOutUs, gain, effects)
            return
        }

        try {
            val processor = FadeGainAudioProcessor(clipUs, fadeInUs, fadeOutUs, gain, effects)
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
                    AudioEncoderSettings.Builder().setBitrate(bitrate * 1000).build()
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
                        message = Msg(
                            R.string.msg_export_done,
                            app.getString(fmt.labelRes),
                            RingtoneMath.formatSize(out.length())
                        )
                        recordHistory(out, clip, bitrate, link)
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

    private fun exportWithEncoder(
        uri: Uri,
        fmt: OutputFormat,
        out: File,
        clip: Long,
        bitrate: Int,
        link: String?,
        fadeInUs: Long,
        fadeOutUs: Long,
        gain: Float,
        effects: EffectSettings
    ) {
        val rangeStart = startMs
        val rangeEnd = endMs
        encodeJob?.cancel()
        encodeJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    PcmExporter.export(
                        context = app,
                        uri = uri,
                        startMs = rangeStart,
                        endMs = rangeEnd,
                        format = fmt,
                        bitrateKbps = bitrate,
                        fadeInUs = fadeInUs,
                        fadeOutUs = fadeOutUs,
                        gain = gain,
                        effects = effects,
                        outFile = out,
                        onProgress = { value -> progress = value },
                        cancelled = { !isActive }
                    )
                }
                pendingOutput = null
                exporting = false
                progress = 1f
                outputFile = out
                message = Msg(
                    R.string.msg_export_done,
                    app.getString(fmt.labelRes),
                    RingtoneMath.formatSize(out.length())
                )
                recordHistory(out, clip, bitrate, link)
            } catch (e: CancellationException) {
                out.delete()
                throw e
            } catch (e: PcmExporter.UnsupportedSampleRateException) {
                finishWithError(out, Msg(R.string.msg_mp3_rate_unsupported, e.sampleRate))
            } catch (e: PcmExporter.NoAudioException) {
                finishWithError(out, Msg(R.string.msg_no_audio))
            } catch (e: Exception) {
                finishWithError(
                    out,
                    Msg(R.string.msg_export_error, e.message ?: app.getString(R.string.msg_unknown_error))
                )
            }
        }
    }

    private fun finishWithError(out: File, error: Msg) {
        pendingOutput = null
        exporting = false
        out.delete()
        message = error
    }

    fun cancelExport(silent: Boolean = false) {
        if (!exporting) return
        progressJob?.cancel()
        measureJob?.cancel()
        measureJob = null
        try {
            transformer?.cancel()
        } catch (_: Exception) {
        }
        transformer = null
        encodeJob?.cancel()
        encodeJob = null
        exporting = false
        pendingOutput?.delete()
        pendingOutput = null
        if (!silent) message = Msg(R.string.msg_export_cancelled)
    }

    // ---- Lịch sử ----
    private fun recordHistory(file: File, clip: Long, bitrate: Int, link: String?) {
        val title = RingtoneMath.safeTitle(sourceName)
        viewModelScope.launch {
            try {
                val entry = withContext(Dispatchers.IO) { history.addFrom(file, title, clip, bitrate, link) }
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
    fun updateDefaultFadeIn(value: Float) {
        defaultFadeIn = value
        settings.fadeInSec = value
    }

    fun updateDefaultFadeOut(value: Float) {
        defaultFadeOut = value
        settings.fadeOutSec = value
    }

    fun updateDefaultVolume(value: Int) {
        defaultVolume = value
        settings.volumePercent = value
    }

    fun updateDefaultBitrate(value: Int) {
        defaultBitrate = value
        settings.bitrateKbps = value
    }

    fun chooseDefaultFormat(value: OutputFormat) {
        defaultFormat = value
        settings.formatExt = value.ext
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
        zoomJob?.cancel()
        measureJob?.cancel()
        progressJob?.cancel()
        player?.release()
        player = null
        super.onCleared()
    }
}

/** Dữ liệu sóng phóng to quanh một điểm cắt; [marker] là vị trí điểm cắt trong cửa sổ (0..1). */
data class ZoomView(val peaks: List<Float>, val marker: Float)

private const val ZOOM_HALF_MS = 1500L
private const val ZOOM_BARS = 72
private const val EDGE_PREVIEW_MS = 2000L

/** Đỉnh mục tiêu khi chuẩn hóa, khoảng -1 dBFS. */
private const val NORMALIZE_TARGET = 0.89f
