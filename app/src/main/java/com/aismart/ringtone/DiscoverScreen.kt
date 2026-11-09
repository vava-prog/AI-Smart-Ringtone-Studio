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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.net.URLEncoder

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(vm: RingtoneViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val d = vm.discover

    LaunchedEffect(Unit) {
        if (d.trending.isEmpty()) d.loadTrending()
    }

    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.discover_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.license_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = d.query,
                        onValueChange = { d.updateQuery(it) },
                        label = { Text(stringResource(R.string.label_search)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { d.search() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OnlineSource.values().forEach { option ->
                            if (option != OnlineSource.JAMENDO || vm.jamendoClientId.isNotBlank()) {
                                FilterChip(
                                    selected = d.source == option,
                                    onClick = { d.chooseSource(option) },
                                    label = { Text(stringResource(option.labelRes)) }
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { d.search() },
                        enabled = !d.loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(if (d.loading) R.string.discover_loading else R.string.btn_search))
                    }
                    d.error?.let {
                        Text(
                            stringResource(it.res, *it.args),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        if (d.searched && d.results.isEmpty() && !d.loading) {
            item { Text(stringResource(R.string.discover_empty)) }
        }

        items(d.results, key = { it.id }) { track ->
            val key = "online:${track.id}"
            val isPlaying = vm.playingHistoryId == key
            val busy = d.busyId == track.id
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = listOfNotNull(
                        track.artist.takeIf { it.isNotBlank() },
                        track.license,
                        track.durationMs.takeIf { it > 0 }?.let { RingtoneMath.formatDuration(it) },
                        stringResource(track.source.labelRes)
                    ).joinToString(" · ")
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { vm.previewOnline(track) }, enabled = !busy) {
                            Text(stringResource(if (isPlaying) R.string.btn_stop else R.string.btn_play))
                        }
                        OutlinedButton(
                            onClick = { vm.useOnlineTrack(track) },
                            enabled = track.canUse && d.busyId == null
                        ) {
                            Text(
                                if (busy) {
                                    stringResource(R.string.discover_downloading, (d.downloadProgress * 100).toInt())
                                } else {
                                    stringResource(R.string.btn_use_track)
                                }
                            )
                        }
                        TextButton(onClick = { open(track.pageUrl) }) {
                            Text(stringResource(R.string.btn_original_page))
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.trending_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.trending_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                d.trendingError?.let {
                    Text(
                        stringResource(it.res, *it.args),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = { d.loadTrending() }) { Text(stringResource(R.string.btn_retry)) }
                }
                if (d.trendingLoading) Text(stringResource(R.string.discover_loading))
            }
        }

        items(d.trending, key = { it.title + "|" + it.artist }) { track ->
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val search = URLEncoder.encode("${track.title} ${track.artist}".trim(), "UTF-8")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { d.searchForTrend(track) }) {
                            Text(stringResource(R.string.btn_find_free))
                        }
                        TextButton(onClick = { open("https://www.youtube.com/results?search_query=$search") }) {
                            Text(stringResource(R.string.platform_youtube))
                        }
                        TextButton(onClick = { open("https://www.tiktok.com/search?q=$search") }) {
                            Text(stringResource(R.string.platform_tiktok))
                        }
                    }
                }
            }
        }
    }
}
