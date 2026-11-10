package com.aismart.ringtone

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateScreen(vm: RingtoneViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            vm.loadSource(uri)
        }
    }

    val saveRequester = rememberSaveRequester(vm)

    fun requestSave(setDefault: Boolean) {
        val file = vm.outputFile ?: return
        saveRequester(
            SaveRequest(file, RingtoneMath.safeTitle(vm.sourceName), vm.target, setDefault)
        )
    }

    fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(stringResource(R.string.title_home), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.subtitle), style = MaterialTheme.typography.bodyMedium)

        AgentCard(vm)

        // Link YouTube / TikTok (tùy chọn): chỉ để mở lại video và ghi nguồn
        CollapsibleGlassCard(stringResource(R.string.section_link), Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val link = SourceLinks.parse(vm.sourceLink)
                OutlinedTextField(
                    value = vm.sourceLink,
                    onValueChange = { vm.updateSourceLink(it) },
                    label = { Text(stringResource(R.string.label_link)) },
                    singleLine = true,
                    isError = vm.sourceLink.isNotBlank() && link == null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (vm.sourceLink.isNotBlank()) {
                    if (link == null) {
                        Text(
                            stringResource(R.string.link_invalid),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        val platformName = stringResource(
                            if (link.platform == SourceLinks.Platform.YOUTUBE) R.string.platform_youtube
                            else R.string.platform_tiktok
                        )
                        Text(stringResource(R.string.link_detected_fmt, platformName))
                        OutlinedButton(
                            onClick = { openUrl(link.url) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.open_in_fmt, platformName)) }
                    }
                }
                Text(
                    stringResource(R.string.link_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. File nguồn
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.section_source), style = MaterialTheme.typography.titleMedium)
                Text(vm.sourceName ?: stringResource(R.string.no_file))
                vm.credit?.let { credit ->
                    Text(
                        stringResource(R.string.credit_fmt, credit.text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (vm.hasSource) {
                    Text(stringResource(R.string.duration_fmt, RingtoneMath.formatTime(vm.durationMs)))
                }
                Button(
                    onClick = { picker.launch(arrayOf("audio/*", "video/*")) },
                    enabled = !vm.exporting,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.btn_pick)) }
            }
        }

        if (vm.hasSource) {
            // 2. Chọn đoạn
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.section_range), style = MaterialTheme.typography.titleMedium)

                    Waveform(
                        profile = vm.profile,
                        startMs = vm.startMs,
                        endMs = vm.endMs,
                        durationMs = vm.durationMs
                    )
                    if (vm.analyzing) {
                        Text(stringResource(R.string.analyzing_wave), style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }

                    RangeSlider(
                        value = vm.startMs.toFloat()..vm.endMs.toFloat(),
                        onValueChange = { vm.setRange(it.start.toLong(), it.endInclusive.toLong()) },
                        valueRange = 0f..vm.durationMs.toFloat(),
                        enabled = !vm.exporting
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${stringResource(R.string.label_start)}: ${RingtoneMath.formatTime(vm.startMs)}")
                        Text("${stringResource(R.string.label_end)}: ${RingtoneMath.formatTime(vm.endMs)}")
                    }
                    Text(
                        stringResource(R.string.clip_length_fmt, RingtoneMath.formatTime(vm.clipMs)),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(stringResource(R.string.label_fine_start), style = MaterialTheme.typography.labelLarge)
                    NudgeRow(enabled = !vm.exporting, onNudge = { vm.nudgeStart(it) })
                    Text(stringResource(R.string.label_fine_end), style = MaterialTheme.typography.labelLarge)
                    NudgeRow(enabled = !vm.exporting, onNudge = { vm.nudgeEnd(it) })

                    FilterChip(
                        selected = vm.zoomOn,
                        onClick = { vm.toggleZoom(!vm.zoomOn) },
                        label = { Text(stringResource(R.string.label_zoom)) }
                    )
                    if (vm.zoomOn) {
                        Text(stringResource(R.string.label_zoom_start), style = MaterialTheme.typography.bodySmall)
                        ZoomWave(vm.zoomStartView, insideAfterMarker = true)
                        OutlinedButton(
                            onClick = { vm.playEdge(true) },
                            enabled = !vm.exporting,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.btn_listen_start)) }
                        Text(stringResource(R.string.label_zoom_end), style = MaterialTheme.typography.bodySmall)
                        ZoomWave(vm.zoomEndView, insideAfterMarker = false)
                        OutlinedButton(
                            onClick = { vm.playEdge(false) },
                            enabled = !vm.exporting,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.btn_listen_end)) }
                    }

                    OutlinedButton(
                        onClick = { vm.smartPick() },
                        enabled = vm.profile != null && !vm.exporting,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.btn_smart_pick)) }
                    if (vm.pickCandidates.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            vm.pickCandidates.forEach { candidate ->
                                FilterChip(
                                    selected = vm.startMs == candidate.startMs && vm.endMs == candidate.endMs,
                                    onClick = { vm.applyCandidate(candidate) },
                                    enabled = !vm.exporting,
                                    label = {
                                        Text(
                                            stringResource(candidate.kind.labelRes) + " " +
                                                RingtoneMath.formatTime(candidate.startMs) + "–" +
                                                RingtoneMath.formatTime(candidate.endMs)
                                        )
                                    }
                                )
                            }
                        }
                    }
                    vm.bpm?.let { bpm ->
                        Text(
                            stringResource(R.string.bpm_fmt, bpm),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (vm.playing == PlayKind.RANGE) vm.stopPlayback() else vm.playRange()
                        },
                        enabled = !vm.exporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(
                                if (vm.playing == PlayKind.RANGE) R.string.btn_stop else R.string.btn_preview_range
                            )
                        )
                    }
                }
            }

            // 3. Tùy chọn âm thanh
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.section_options), style = MaterialTheme.typography.titleMedium)

                    Text(stringResource(R.string.fade_in_label, oneDecimal(vm.fadeInSec)))
                    Slider(
                        value = vm.fadeInSec,
                        onValueChange = { vm.setFadeIn((it * 2f).roundToInt() / 2f) },
                        valueRange = 0f..5f,
                        steps = 9,
                        enabled = !vm.exporting
                    )

                    Text(stringResource(R.string.fade_out_label, oneDecimal(vm.fadeOutSec)))
                    Slider(
                        value = vm.fadeOutSec,
                        onValueChange = { vm.setFadeOut((it * 2f).roundToInt() / 2f) },
                        valueRange = 0f..5f,
                        steps = 9,
                        enabled = !vm.exporting
                    )

                    Text(stringResource(R.string.volume_label, vm.volumePercent))
                    Slider(
                        value = vm.volumePercent.toFloat(),
                        onValueChange = { vm.setVolume((it / 10f).roundToInt() * 10) },
                        valueRange = 50f..150f,
                        steps = 9,
                        enabled = !vm.exporting
                    )

                    Text(stringResource(R.string.label_format))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutputFormat.values().forEach { option ->
                            FilterChip(
                                selected = vm.format == option,
                                onClick = { vm.selectFormat(option) },
                                enabled = !vm.exporting,
                                label = { Text(stringResource(option.labelRes)) }
                            )
                        }
                    }
                    when (vm.format) {
                        OutputFormat.MP3 -> Text(
                            stringResource(R.string.format_hint_mp3),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutputFormat.FLAC -> Text(
                            stringResource(R.string.format_hint_flac),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutputFormat.WAV -> Text(
                            stringResource(R.string.format_hint_wav),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutputFormat.M4A -> {}
                    }

                    if (vm.format.usesBitrate) {
                        Text(stringResource(R.string.label_quality))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(64, 96, 128, 192).forEach { kbps ->
                                FilterChip(
                                    selected = vm.bitrateKbps == kbps,
                                    onClick = { vm.setBitrate(kbps) },
                                    enabled = !vm.exporting,
                                    label = { Text("$kbps") }
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(
                            R.string.est_size_fmt,
                            RingtoneMath.formatSize(
                                RingtoneMath.estimateOutputBytes(vm.format, vm.bitrateKbps, vm.clipMs)
                            )
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // 3b. Hiệu ứng âm thanh
            CollapsibleGlassCard(stringResource(R.string.section_effects), Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EqPreset.values().forEach { preset ->
                            FilterChip(
                                selected = vm.eqBands == preset.bands && vm.preampDb == preset.preampDb,
                                onClick = { vm.selectPreset(preset) },
                                enabled = !vm.exporting,
                                label = { Text(stringResource(preset.labelRes)) }
                            )
                        }
                    }

                    CustomPresetBar(vm)
                    EqCurve(vm.preampDb, vm.eqBands)

                    Text(stringResource(R.string.label_preamp, signedDb(vm.preampDb)))
                    Slider(
                        value = vm.preampDb,
                        onValueChange = { vm.changePreamp(it.roundToInt().toFloat()) },
                        valueRange = -EqConfig.MAX_DB..EqConfig.MAX_DB,
                        steps = 23,
                        enabled = !vm.exporting
                    )

                    for (i in 0 until EqConfig.COUNT) {
                        Text("${EqConfig.LABELS[i]}: ${signedDb(vm.eqBands[i])}")
                        Slider(
                            value = vm.eqBands[i],
                            onValueChange = { vm.changeBand(i, it.roundToInt().toFloat()) },
                            valueRange = -EqConfig.MAX_DB..EqConfig.MAX_DB,
                            steps = 23,
                            enabled = !vm.exporting
                        )
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = vm.normalizeOn,
                            onClick = { vm.toggleNormalize(!vm.normalizeOn) },
                            enabled = !vm.exporting,
                            label = { Text(stringResource(R.string.label_normalize)) }
                        )
                        FilterChip(
                            selected = vm.limiterOn,
                            onClick = { vm.toggleLimiter(!vm.limiterOn) },
                            enabled = !vm.exporting,
                            label = { Text(stringResource(R.string.label_limiter)) }
                        )
                    }
                    Text(
                        stringResource(R.string.effects_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 4. Xuất file
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.section_export), style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = { vm.export() },
                        enabled = !vm.exporting,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.btn_export)) }

                    if (vm.exporting) {
                        Text(
                            stringResource(R.string.exporting_pct, (vm.progress * 100).roundToInt()),
                            style = MaterialTheme.typography.bodySmall
                        )
                        LinearProgressIndicator(
                            progress = { vm.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = { vm.cancelExport() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.btn_cancel)) }
                    }
                }
            }
        }

        // 5. Kết quả
        val file = vm.outputFile
        if (file != null) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.section_result), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.result_name_fmt, file.name))
                    Text(stringResource(R.string.result_size_fmt, RingtoneMath.formatSize(file.length())))

                    OutlinedButton(
                        onClick = {
                            if (vm.playing == PlayKind.RESULT) vm.stopPlayback() else vm.playResult()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(
                                if (vm.playing == PlayKind.RESULT) R.string.btn_stop else R.string.btn_preview_result
                            )
                        )
                    }

                    Text(stringResource(R.string.label_use_as))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RingtoneTarget.values().forEach { option ->
                            FilterChip(
                                selected = vm.target == option,
                                onClick = { vm.selectTarget(option) },
                                label = { Text(stringResource(option.labelRes)) }
                            )
                        }
                    }

                    Button(
                        onClick = { requestSave(setDefault = true) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.btn_set_default)) }

                    OutlinedButton(
                        onClick = { requestSave(setDefault = false) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.btn_save)) }

                    OutlinedButton(
                        onClick = {
                            val contentUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = OutputFormat.fromExt(file.extension).mime
                                putExtra(Intent.EXTRA_STREAM, contentUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(share, context.getString(R.string.share_chooser))
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.btn_share)) }
                }
            }
        }

        // Thông báo trạng thái
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                val msg = vm.message
                Text(stringResource(msg.res, *msg.args))
            }
        }

        // Nguồn nhạc miễn phí bản quyền
        CollapsibleGlassCard(stringResource(R.string.section_sources), Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(stringResource(R.string.sources_note), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { openUrl("https://pixabay.com/music/") }) {
                        Text(stringResource(R.string.source_pixabay))
                    }
                    TextButton(onClick = { openUrl("https://www.jamendo.com/") }) {
                        Text(stringResource(R.string.source_jamendo))
                    }
                    TextButton(onClick = { openUrl("https://freemusicarchive.org/") }) {
                        Text(stringResource(R.string.source_fma))
                    }
                }
            }
        }

        Text(
            stringResource(R.string.tip_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun oneDecimal(value: Float): String = String.format(Locale.getDefault(), "%.1f", value)

private fun signedDb(value: Float): String =
    String.format(Locale.getDefault(), "%+d dB", value.roundToInt())

/** Hàng nút dịch điểm cắt theo từng bước nhỏ (mili giây). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NudgeRow(enabled: Boolean, onNudge: (Long) -> Unit) {
    val steps = listOf(
        "−1 s" to -1000L, "−0.1 s" to -100L, "−10 ms" to -10L,
        "+10 ms" to 10L, "+0.1 s" to 100L, "+1 s" to 1000L
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.forEach { (label, delta) ->
            OutlinedButton(
                onClick = { onNudge(delta) },
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) { Text(label) }
        }
    }
}

/** Sóng âm phóng to quanh một điểm cắt; vạch đứng là vị trí điểm cắt. */
@Composable
private fun ZoomWave(view: ZoomView?, insideAfterMarker: Boolean) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.outlineVariant
    val marker = MaterialTheme.colorScheme.tertiary
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        val peaks = view?.peaks.orEmpty()
        if (view == null || peaks.isEmpty()) {
            drawRoundRect(
                color = inactive,
                topLeft = Offset(0f, size.height / 2f - 2.dp.toPx()),
                size = Size(size.width, 4.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
            return@Canvas
        }
        val slot = size.width / peaks.size
        val barWidth = slot * 0.7f
        peaks.forEachIndexed { index, value ->
            val position = (index + 0.5f) / peaks.size
            val inside = if (insideAfterMarker) position >= view.marker else position <= view.marker
            val barHeight = (value.coerceIn(0f, 1f) * size.height).coerceAtLeast(3.dp.toPx())
            drawRoundRect(
                color = if (inside) active else inactive,
                topLeft = Offset(index * slot + (slot - barWidth) / 2f, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
        val x = view.marker * size.width
        drawLine(
            color = marker,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 2.dp.toPx()
        )
    }
}

/** Vẽ sóng âm dạng thanh; phần nằm trong đoạn đã chọn được tô màu nổi bật. */
@Composable
private fun Waveform(
    profile: List<Float>?,
    startMs: Long,
    endMs: Long,
    durationMs: Long
) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.outlineVariant
    val bars = remember(profile) { downsample(profile, 96) }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        if (bars.isEmpty() || durationMs <= 0L) {
            drawRoundRect(
                color = inactive,
                topLeft = Offset(0f, size.height / 2f - 2.dp.toPx()),
                size = Size(size.width, 4.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
            return@Canvas
        }
        val slot = size.width / bars.size
        val barWidth = slot * 0.62f
        bars.forEachIndexed { index, value ->
            val barTimeMs = (index + 0.5f) / bars.size * durationMs
            val inside = barTimeMs >= startMs && barTimeMs <= endMs
            val barHeight = (value.coerceIn(0f, 1f) * size.height).coerceAtLeast(4.dp.toPx())
            drawRoundRect(
                color = if (inside) active else inactive,
                topLeft = Offset(index * slot + (slot - barWidth) / 2f, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}

/** Gom danh sách năng lượng về [count] thanh bằng cách lấy giá trị lớn nhất trong mỗi nhóm. */
private fun downsample(profile: List<Float>?, count: Int): List<Float> {
    if (profile.isNullOrEmpty()) return emptyList()
    if (profile.size <= count) return profile
    return List(count) { i ->
        val from = (i.toLong() * profile.size / count).toInt()
        val to = (((i + 1).toLong() * profile.size / count).toInt()).coerceAtLeast(from + 1)
        var peak = 0f
        for (j in from until minOf(to, profile.size)) {
            if (profile[j] > peak) peak = profile[j]
        }
        peak
    }
}
