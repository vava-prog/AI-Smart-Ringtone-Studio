package com.aismart.ringtone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.ln
import kotlin.math.pow

/** Đường cong đáp ứng tần số của equalizer (20 Hz - 20 kHz, ±15 dB). */
@Composable
fun EqCurve(preampDb: Float, bands: List<Float>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val dotColor = MaterialTheme.colorScheme.secondary
    Canvas(
        modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        val minF = 20.0
        val maxF = 20000.0
        val range = 15f
        fun xOf(f: Double): Float = (ln(f / minF) / ln(maxF / minF)).toFloat() * size.width
        fun yOf(db: Double): Float {
            val clamped = db.toFloat().coerceIn(-range, range)
            return size.height / 2f - clamped / range * (size.height / 2f)
        }
        for (db in listOf(-12f, -6f, 0f, 6f, 12f)) {
            val y = yOf(db.toDouble())
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = if (db == 0f) 2.dp.toPx() else 1.dp.toPx()
            )
        }
        val path = Path()
        val steps = 120
        for (i in 0..steps) {
            val f = minF * (maxF / minF).pow(i.toDouble() / steps)
            val x = xOf(f)
            val y = yOf(EqResponse.gainDb(f, preampDb, bands))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        EqConfig.FREQS.forEach { f ->
            drawCircle(
                color = dotColor,
                radius = 4.dp.toPx(),
                center = Offset(xOf(f.toDouble()), yOf(EqResponse.gainDb(f.toDouble(), preampDb, bands)))
            )
        }
    }
}

/** Lưu, áp dụng và xóa các preset equalizer do người dùng đặt tên. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomPresetBar(vm: RingtoneViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (vm.customPresets.isNotEmpty()) {
            Text(stringResource(R.string.custom_presets_title), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.customPresets.forEach { preset ->
                    FilterChip(
                        selected = vm.eqBands == preset.bands && vm.preampDb == preset.preampDb,
                        onClick = { vm.applyCustomPreset(preset) },
                        enabled = !vm.exporting,
                        label = { Text(preset.name) }
                    )
                }
            }
            val selected = vm.customPresets.firstOrNull {
                vm.eqBands == it.bands && vm.preampDb == it.preampDb
            }
            if (selected != null) {
                TextButton(onClick = { vm.deleteCustomPreset(selected) }) {
                    Text(stringResource(R.string.btn_delete_preset, selected.name))
                }
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(CustomPresets.MAX_NAME) },
            singleLine = true,
            label = { Text(stringResource(R.string.preset_name_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = {
                vm.saveCustomPreset(name)
                name = ""
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.btn_save_preset)) }
    }
}
