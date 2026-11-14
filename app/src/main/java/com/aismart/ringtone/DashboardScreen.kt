package com.aismart.ringtone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as DayStyle
import java.util.Date
import java.util.Locale

private const val CHART_DAYS = 7

@Composable
fun DashboardScreen(
    vm: RingtoneViewModel,
    onCreate: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDiscover: () -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = vm.historyEntries
    val zone = remember { ZoneId.systemDefault() }
    val counts = remember(entries) {
        StatsMath.dailyCounts(entries.map { it.createdAt }, System.currentTimeMillis(), zone, CHART_DAYS)
    }
    val labels = remember {
        val today = LocalDate.now(zone)
        (0 until CHART_DAYS).map { i ->
            today.minusDays((CHART_DAYS - 1 - i).toLong())
                .dayOfWeek.getDisplayName(DayStyle.SHORT, Locale.getDefault())
        }
    }

    val total = entries.size
    val totalMs = entries.sumOf { it.clipMs }
    val totalBytes = entries.sumOf { it.sizeBytes }
    val avgMs = if (total > 0) totalMs / total else 0L

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(stringResource(R.string.dash_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.dash_subtitle), style = MaterialTheme.typography.bodyMedium)

        GlassCard(Modifier.fillMaxWidth()) {
            DeskComputer(
                onCreate = onCreate,
                onDiscover = onOpenDiscover,
                onHistory = onOpenHistory,
                modifier = Modifier.padding(12.dp)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                Modifier.weight(1f),
                stringResource(R.string.stat_count),
                "$total",
                stringResource(R.string.stat_week_fmt, counts.sum())
            )
            StatCard(
                Modifier.weight(1f),
                stringResource(R.string.stat_duration),
                RingtoneMath.formatDuration(totalMs),
                null
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                Modifier.weight(1f),
                stringResource(R.string.stat_size),
                RingtoneMath.formatSize(totalBytes),
                null
            )
            StatCard(
                Modifier.weight(1f),
                stringResource(R.string.stat_avg),
                RingtoneMath.formatDuration(avgMs),
                null
            )
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.chart_title), style = MaterialTheme.typography.titleMedium)
                WeekChart(counts, labels)
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.format_title), style = MaterialTheme.typography.titleMedium)
                OutputFormat.values().forEach { option ->
                    val count = entries.count { it.format == option }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(option.ext.uppercase(), Modifier.width(84.dp))
                        LinearProgressIndicator(
                            progress = { if (total == 0) 0f else count.toFloat() / total },
                            modifier = Modifier.weight(1f)
                        )
                        Text("$count", Modifier.width(36.dp), textAlign = TextAlign.End)
                    }
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.bitrate_title), style = MaterialTheme.typography.titleMedium)
                listOf(64, 96, 128, 192).forEach { kbps ->
                    val count = entries.count { it.bitrateKbps == kbps }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$kbps kbps", Modifier.width(84.dp))
                        LinearProgressIndicator(
                            progress = { if (total == 0) 0f else count.toFloat() / total },
                            modifier = Modifier.weight(1f)
                        )
                        Text("$count", Modifier.width(36.dp), textAlign = TextAlign.End)
                    }
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.recent_title), style = MaterialTheme.typography.titleMedium)
                    if (entries.isNotEmpty()) {
                        TextButton(onClick = onOpenHistory) { Text(stringResource(R.string.btn_view_all)) }
                    }
                }
                if (entries.isEmpty()) {
                    Text(stringResource(R.string.empty_history), style = MaterialTheme.typography.bodyMedium)
                } else {
                    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
                    entries.take(3).forEach { entry ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    format.format(Date(entry.createdAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(RingtoneMath.formatTime(entry.clipMs))
                        }
                    }
                }
            }
        }

        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.btn_create_new))
        }
        OutlinedButton(onClick = onOpenDiscover, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.btn_discover_music))
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, label: String, value: String, sub: String?) {
    GlassCard(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun WeekChart(counts: List<Int>, labels: List<String>) {
    val maxCount = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val brush = Brush.verticalGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(150.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        counts.forEachIndexed { index, count ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text("$count", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                val barHeight = if (count == 0) 4.dp else (84.dp * (count.toFloat() / maxCount)).coerceAtLeast(10.dp)
                Box(
                    Modifier
                        .width(22.dp)
                        .height(barHeight)
                        .background(brush, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                )
                Spacer(Modifier.height(4.dp))
                Text(labels[index], style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
