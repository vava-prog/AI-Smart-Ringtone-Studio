package com.aismart.ringtone

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RingtoneTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingtoneApp(vm: RingtoneViewModel) {
    RingtoneTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }
        ) { padding ->
            RingtoneContent(vm, Modifier.padding(padding))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RingtoneContent(vm: RingtoneViewModel, modifier: Modifier) {
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

    // Android 9 trở xuống cần xin quyền ghi bộ nhớ trước khi lưu vào thư mục Ringtones
    var pendingSetDefault by remember { mutableStateOf<Boolean?>(null) }
    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val setDefault = pendingSetDefault
        pendingSetDefault = null
        if (granted && setDefault != null) {
            vm.saveToDevice(setDefault)
        } else {
            vm.showMessage(R.string.msg_perm_denied)
        }
    }

    fun requestSave(setDefault: Boolean) {
        if (setDefault && !RingtoneSaver.canWriteSettings(context)) {
            vm.showMessage(R.string.msg_need_write_settings)
            try {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            } catch (_: ActivityNotFoundException) {
            }
            return
        }
        val needsStoragePermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        if (needsStoragePermission) {
            pendingSetDefault = setDefault
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        vm.saveToDevice(setDefault)
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

        // 1. File nguồn
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.section_source), style = MaterialTheme.typography.titleMedium)
                Text(vm.sourceName ?: stringResource(R.string.no_file))
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
            Card(Modifier.fillMaxWidth()) {
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

                    OutlinedButton(
                        onClick = { vm.smartPick() },
                        enabled = vm.profile != null && !vm.exporting,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.btn_smart_pick)) }

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
            Card(Modifier.fillMaxWidth()) {
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
                    Text(
                        stringResource(
                            R.string.est_size_fmt,
                            RingtoneMath.formatSize(
                                RingtoneMath.estimateSizeBytes(vm.bitrateKbps, vm.clipMs)
                            )
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // 4. Xuất file
            Card(Modifier.fillMaxWidth()) {
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
            Card(Modifier.fillMaxWidth()) {
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
                                type = "audio/mp4"
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
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                val msg = vm.message
                Text(stringResource(msg.res, *msg.args))
            }
        }

        // Nguồn nhạc miễn phí bản quyền
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.section_sources), style = MaterialTheme.typography.titleMedium)
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
