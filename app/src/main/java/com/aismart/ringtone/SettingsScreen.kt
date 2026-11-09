package com.aismart.ringtone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: RingtoneViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember {
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.settings_defaults), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_defaults_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(stringResource(R.string.fade_in_label, oneDecimal(vm.defaultFadeIn)))
                Slider(
                    value = vm.defaultFadeIn,
                    onValueChange = { vm.updateDefaultFadeIn((it * 2f).roundToInt() / 2f) },
                    valueRange = 0f..5f,
                    steps = 9
                )

                Text(stringResource(R.string.fade_out_label, oneDecimal(vm.defaultFadeOut)))
                Slider(
                    value = vm.defaultFadeOut,
                    onValueChange = { vm.updateDefaultFadeOut((it * 2f).roundToInt() / 2f) },
                    valueRange = 0f..5f,
                    steps = 9
                )

                Text(stringResource(R.string.volume_label, vm.defaultVolume))
                Slider(
                    value = vm.defaultVolume.toFloat(),
                    onValueChange = { vm.updateDefaultVolume((it / 10f).roundToInt() * 10) },
                    valueRange = 50f..150f,
                    steps = 9
                )

                Text(stringResource(R.string.label_format))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutputFormat.values().forEach { option ->
                        FilterChip(
                            selected = vm.defaultFormat == option,
                            onClick = { vm.chooseDefaultFormat(option) },
                            label = { Text(stringResource(option.labelRes)) }
                        )
                    }
                }

                Text(stringResource(R.string.label_quality))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(64, 96, 128, 192).forEach { kbps ->
                        FilterChip(
                            selected = vm.defaultBitrate == kbps,
                            onClick = { vm.updateDefaultBitrate(kbps) },
                            label = { Text("$kbps") }
                        )
                    }
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        0 to R.string.theme_system,
                        1 to R.string.theme_light,
                        2 to R.string.theme_dark
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = vm.themeMode == mode,
                            onClick = { vm.selectThemeMode(mode) },
                            label = { Text(stringResource(label)) }
                        )
                    }
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.jamendo_title), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = vm.jamendoClientId,
                    onValueChange = { vm.updateJamendoId(it) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.jamendo_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.jamendo_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.app_name))
                Text(stringResource(R.string.about_version_fmt, version))
                Text(
                    stringResource(R.string.about_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun oneDecimal(value: Float): String = String.format(Locale.getDefault(), "%.1f", value)
