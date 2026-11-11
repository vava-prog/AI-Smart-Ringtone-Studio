package com.aismart.ringtone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Trợ lý AI chạy hoàn toàn trên máy: gõ lệnh, trợ lý chỉnh các tùy chọn giúp bạn. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentCard(vm: RingtoneViewModel) {
    var input by rememberSaveable { mutableStateOf("") }
    val examples = listOf(
        stringResource(R.string.agent_ex1),
        stringResource(R.string.agent_ex2),
        stringResource(R.string.agent_ex3),
        stringResource(R.string.agent_ex4)
    )

    CollapsibleGlassCard(stringResource(R.string.agent_title), Modifier.fillMaxWidth(), initiallyExpanded = true) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.agent_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(stringResource(R.string.agent_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.runAgent(input) }),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { vm.runAgent(input) },
                enabled = input.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.btn_agent_run)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                examples.forEach { example ->
                    AssistChip(
                        onClick = {
                            input = example
                            vm.runAgent(example)
                        },
                        label = { Text(example) }
                    )
                }
            }
            if (vm.agentSuggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.agentSuggestions.forEach { candidate ->
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
            vm.agentLog.forEach { line ->
                Text(
                    "• " + stringResource(line.res, *line.args),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
