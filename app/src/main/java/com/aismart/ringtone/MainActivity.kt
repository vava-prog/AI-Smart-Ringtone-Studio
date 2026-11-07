package com.aismart.ringtone

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { RingtoneHome() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RingtoneHome() {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf("Chưa chọn file") }
    var startSeconds by remember { mutableStateOf("0") }
    var endSeconds by remember { mutableStateOf("30") }
    var status by remember { mutableStateOf("Chọn file nhạc để bắt đầu.") }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var exporting by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedName = uri.lastPathSegment ?: "Tệp âm thanh đã chọn"
            outputFile = null
            status = "Đã chọn file. Nhập khoảng thời gian rồi xuất AAC."
        }
    }

    fun exportAudio() {
        val uri = selectedUri ?: run {
            status = "Bạn chưa chọn file âm thanh."
            return
        }
        val start = startSeconds.toDoubleOrNull()
        val end = endSeconds.toDoubleOrNull()
        if (start == null || end == null || start < 0 || end <= start) {
            status = "Khoảng thời gian không hợp lệ. Điểm kết thúc phải lớn hơn điểm bắt đầu."
            return
        }
        if (end - start > 240) {
            status = "Bản MVP giới hạn mỗi đoạn tối đa 240 giây."
            return
        }

        val out = File(context.cacheDir, "ringtone_${System.currentTimeMillis()}.m4a")
        outputFile = null
        exporting = true
        status = "Đang xử lý âm thanh…"

        try {
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs((start * 1000).toLong())
                        .setEndPositionMs((end * 1000).toLong())
                        .build()
                )
                .build()

            val edited = EditedMediaItem.Builder(mediaItem)
                .setRemoveVideo(true)
                .build()

            val transformer = Transformer.Builder(context)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(
                        composition: androidx.media3.transformer.Composition,
                        exportResult: ExportResult
                    ) {
                        exporting = false
                        outputFile = out
                        val kb = out.length() / 1024.0
                        status = String.format(
                            Locale.getDefault(),
                            "Hoàn tất! File M4A/AAC: %.1f KB. Hãy nghe thử trước khi đặt làm nhạc chuông.",
                            kb
                        )
                    }

                    override fun onError(
                        composition: androidx.media3.transformer.Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        exporting = false
                        status = "Không thể xuất file: ${exportException.message ?: "Lỗi codec hoặc định dạng nguồn"}"
                    }
                })
                .build()

            transformer.start(edited, out.absolutePath)
        } catch (e: Exception) {
            exporting = false
            status = "Lỗi khởi tạo xử lý: ${e.message ?: "Không xác định"}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Ringtone Studio") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Tạo nhạc chuông trên điện thoại", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Bản MVP: chọn file, cắt đoạn và xuất âm thanh AAC trong container M4A. Xử lý diễn ra trên thiết bị.",
                style = MaterialTheme.typography.bodyMedium
            )

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. File nguồn", style = MaterialTheme.typography.titleMedium)
                    Text(selectedName)
                    Button(
                        onClick = { picker.launch(arrayOf("audio/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Chọn file nhạc") }
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("2. Chọn đoạn cần cắt (giây)", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = startSeconds,
                        onValueChange = { startSeconds = it },
                        label = { Text("Bắt đầu") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = endSeconds,
                        onValueChange = { endSeconds = it },
                        label = { Text("Kết thúc") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Mẹo: nhạc chuông thường dài 20–40 giây.")
                }
            }

            Button(
                onClick = { exportAudio() },
                enabled = selectedUri != null && !exporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (exporting) "Đang xử lý…" else "Cắt và xuất M4A")
            }

            if (exporting) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("3. Kết quả", style = MaterialTheme.typography.titleMedium)
                    Text(status)
                    outputFile?.let { file ->
                        Text("Tên file: ${file.name}")
                        Text("Dung lượng: ${file.length() / 1024} KB")
                        OutlinedButton(
                            onClick = {
                                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "audio/mp4"
                                    putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(share, "Chia sẻ nhạc chuông"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Chia sẻ file") }
                    }
                }
            }

            Text(
                "Lưu ý: AAC/M4A là định dạng đầu ra MVP. Dung lượng dưới 1 MB phụ thuộc thời lượng và bitrate thực tế. Bản này chưa tích hợp tìm kiếm Internet, MP3 encoder hoặc AI tự chọn đoạn nhạc.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
