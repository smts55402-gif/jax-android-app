package com.jax.automation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.jax.automation.di.AppContainer
import com.jax.automation.models.AutomationMode
import com.jax.automation.ui.ScreenScaffold
import com.jax.automation.ui.viewmodel.JaxViewModelFactory
import com.jax.automation.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(container: AppContainer, navController: NavController) {
    val vm: SettingsViewModel = viewModel(factory = JaxViewModelFactory(container))
    val settings by vm.settingsState.collectAsState()

    ScreenScaffold("Settings", navController) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("AI", style = MaterialTheme.typography.titleLarge)
            Text(
                "Provider, model and key defaults live on the Providers screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { navController.navigate("providers") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open providers") }

            Text("Automation", style = MaterialTheme.typography.titleLarge)

            NumberSettingRow(
                label = "Max retry attempts",
                value = settings.maxRetryAttempts.toLong(),
                onSave = { v -> vm.update { s -> s.copy(maxRetryAttempts = v.toInt().coerceAtLeast(0)) } }
            )
            NumberSettingRow(
                label = "Default output count",
                value = settings.defaultOutputCount.toLong(),
                onSave = { v -> vm.update { s -> s.copy(defaultOutputCount = v.toInt().coerceAtLeast(1)) } }
            )
            TextSettingRow(
                label = "Default aspect ratio",
                value = settings.defaultAspectRatio,
                onSave = { v -> vm.update { s -> s.copy(defaultAspectRatio = v) } }
            )
            AutomationModeRow(
                current = settings.automationMode,
                onPick = { m -> vm.update { s -> s.copy(automationMode = m) } }
            )
            NumberSettingRow(
                label = "Default timeout (ms)",
                value = settings.defaultTimeoutMs,
                onSave = { v -> vm.update { s -> s.copy(defaultTimeoutMs = v.coerceAtLeast(1000L)) } }
            )
            TextSettingRow(
                label = "Download folder",
                value = settings.downloadFolder,
                onSave = { v -> vm.update { s -> s.copy(downloadFolder = v) } }
            )

            SwitchSettingRow(
                label = "Screenshot logging",
                checked = settings.screenshotLogging,
                onChange = { v -> vm.update { s -> s.copy(screenshotLogging = v) } }
            )
            SwitchSettingRow(
                label = "QA enabled",
                checked = settings.qaEnabled,
                onChange = { v -> vm.update { s -> s.copy(qaEnabled = v) } }
            )
            SwitchSettingRow(
                label = "Auto-resume after restart",
                checked = settings.autoResume,
                onChange = { v -> vm.update { s -> s.copy(autoResume = v) } }
            )
        }
    }
}

@Composable
private fun NumberSettingRow(label: String, value: Long, onSave: (Long) -> Unit) {
    var draft by remember(value) { mutableStateOf(value.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.filter { c -> c.isDigit() } },
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            keyboardActions = KeyboardActions(onDone = {
                draft.toLongOrNull()?.let(onSave)
            }),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = { draft.toLongOrNull()?.let(onSave) }) { Text("Apply") }
    }
}

@Composable
private fun TextSettingRow(label: String, value: String, onSave: (String) -> Unit) {
    var draft by remember(value) { mutableStateOf(value) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text(label) },
            keyboardActions = KeyboardActions(onDone = { onSave(draft.trim()) }),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = { onSave(draft.trim()) }) { Text("Apply") }
    }
}

@Composable
private fun SwitchSettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutomationModeRow(current: AutomationMode, onPick: (AutomationMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Automation mode", modifier = Modifier.weight(1f))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f)
        ) {
            TextField(
                value = current.name,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                singleLine = true
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                AutomationMode.values().forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.name) },
                        onClick = {
                            onPick(mode)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
