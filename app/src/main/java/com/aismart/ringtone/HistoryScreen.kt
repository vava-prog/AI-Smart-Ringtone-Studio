package com.aismart.ringtone

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(vm: RingtoneViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val saveRequester = rememberSaveRequester(vm)
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    var setAsEntry by remember { mutableStateOf<HistoryEntry?>(null) }
    var deleteEntry by remember { mutableStateOf<HistoryEntry?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.history_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                if (vm.historyEntries.isEmpty()) {
                    Text(stringResource(R.string.empty_history), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        items(vm.historyEntries, key = { it.id }) { entry ->
            val isPlaying = vm.playingHistoryId == entry.id
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        dateFormat.format(Date(entry.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(
                            R.string.history_meta_fmt,
                            RingtoneMath.formatTime(entry.clipMs),
                            if (entry.bitrateKbps > 0) {
                                "${entry.format.ext.uppercase()} ${entry.bitrateKbps} kbps"
                            } else {
                                entry.format.ext.uppercase()
                            },
                            RingtoneMath.formatSize(entry.sizeBytes)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            if (isPlaying) vm.stopPlayback() else vm.openHistoryInCreate(entry)
                        }) {
                            Text(stringResource(if (isPlaying) R.string.btn_stop else R.string.btn_play_edit))
                        }
                        TextButton(onClick = { setAsEntry = entry }) {
                            Text(stringResource(R.string.btn_set_as))
                        }
                        TextButton(onClick = {
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    vm.historyFile(entry)
                                )
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = entry.format.mime
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(share, context.getString(R.string.share_chooser))
                                )
                            } catch (_: ActivityNotFoundException) {
                            }
                        }) { Text(stringResource(R.string.btn_share)) }
                        entry.sourceUrl?.let { url ->
                            TextButton(onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (_: ActivityNotFoundException) {
                                }
                            }) { Text(stringResource(R.string.btn_source)) }
                        }
                        TextButton(onClick = { deleteEntry = entry }) {
                            Text(stringResource(R.string.btn_delete))
                        }
                    }
                }
            }
        }
    }

    setAsEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { setAsEntry = null },
            title = { Text(stringResource(R.string.dialog_set_title)) },
            text = {
                Column {
                    RingtoneTarget.values().forEach { option ->
                        TextButton(onClick = {
                            saveRequester(
                                SaveRequest(vm.historyFile(entry), RingtoneMath.safeTitle(entry.title), option, true)
                            )
                            setAsEntry = null
                        }) { Text(stringResource(option.labelRes)) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { setAsEntry = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    deleteEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteEntry = null },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = { Text(stringResource(R.string.dialog_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteHistory(entry)
                    deleteEntry = null
                }) { Text(stringResource(R.string.btn_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteEntry = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}
