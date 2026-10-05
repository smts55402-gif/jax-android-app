package com.jax.automation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.jax.automation.ai.ProviderInfo
import com.jax.automation.ai.ProviderRole
import com.jax.automation.di.AppContainer
import com.jax.automation.ui.ScreenScaffold
import com.jax.automation.ui.viewmodel.JaxViewModelFactory
import com.jax.automation.ui.viewmodel.ProvidersViewModel

@Composable
fun ProvidersScreen(container: AppContainer, navController: NavController) {
    val vm: ProvidersViewModel = viewModel(factory = JaxViewModelFactory(container))
    val info by vm.info.collectAsState()
    val settings by vm.settings.collectAsState()

    LaunchedEffect(Unit) { vm.refresh() }

    ScreenScaffold("AI Providers", navController) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Roles", style = MaterialTheme.typography.titleLarge)
                ProviderRoleRow(
                    label = "Planner",
                    role = ProviderRole.PLANNER,
                    currentProviderId = settings.plannerProviderId,
                    currentModel = settings.plannerModel,
                    info = info,
                    onPickProvider = { vm.setDefault(ProviderRole.PLANNER, it) },
                    onSaveModel = { vm.saveModel(ProviderRole.PLANNER, it) }
                )
                ProviderRoleRow(
                    label = "Prompt",
                    role = ProviderRole.PROMPT,
                    currentProviderId = settings.promptProviderId,
                    currentModel = settings.promptModel,
                    info = info,
                    onPickProvider = { vm.setDefault(ProviderRole.PROMPT, it) },
                    onSaveModel = { vm.saveModel(ProviderRole.PROMPT, it) }
                )
                ProviderRoleRow(
                    label = "QA",
                    role = ProviderRole.QA,
                    currentProviderId = settings.qaProviderId,
                    currentModel = settings.qaModel,
                    info = info,
                    onPickProvider = { vm.setDefault(ProviderRole.QA, it) },
                    onSaveModel = { vm.saveModel(ProviderRole.QA, it) }
                )
            }

            item { Text("Providers", style = MaterialTheme.typography.titleLarge) }

            items(info, key = { it.id }) { p ->
                ProviderCard(p, vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderRoleRow(
    label: String,
    role: ProviderRole,
    currentProviderId: String,
    currentModel: String,
    info: List<ProviderInfo>,
    onPickProvider: (String) -> Unit,
    onSaveModel: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var modelDraft by remember(currentModel) { mutableStateOf(currentModel) }

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.weight(1f)
            ) {
                TextField(
                    value = info.firstOrNull { it.id == currentProviderId }?.displayName
                        ?: currentProviderId.ifBlank { "Select" },
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
                    info.forEach { p ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    p.displayName +
                                        if (!p.hasApiKey) " (no key)"
                                        else if (!p.enabled) " (disabled)"
                                        else ""
                                )
                            },
                            onClick = {
                                onPickProvider(p.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = modelDraft,
                onValueChange = { modelDraft = it },
                label = { Text("Model") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onSaveModel(modelDraft) }) { Text("Save") }
        }
    }
}

@Composable
private fun ProviderCard(p: ProviderInfo, vm: ProvidersViewModel) {
    val testStatus by vm.testStatus.collectAsState()
    var keyDraft by remember(p.id) { mutableStateOf("") }
    var urlDraft by remember(p.id, p.customBaseUrl) { mutableStateOf(p.customBaseUrl ?: "") }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    p.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = p.enabled,
                    onCheckedChange = { vm.setEnabled(p.id, it) }
                )
            }
            Text(
                "Model: ${p.defaultModel.ifBlank { "—" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                label = { Text("API key") },
                placeholder = {
                    if (p.hasApiKey) Text("Saved — enter a new key to replace")
                },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.saveKey(p.id, keyDraft); keyDraft = "" },
                    enabled = keyDraft.isNotBlank()
                ) { Text("Save key") }
                if (p.hasApiKey) {
                    OutlinedButton(onClick = { vm.clearKey(p.id) }) { Text("Clear key") }
                }
                OutlinedButton(onClick = { vm.test(p.id) }) { Text("Test") }
            }
            testStatus[p.id]?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            if (p.id == "other") {
                OutlinedTextField(
                    value = urlDraft,
                    onValueChange = { urlDraft = it },
                    label = { Text("Custom base URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(onClick = { vm.saveBaseUrl(p.id, urlDraft) }) { Text("Save base URL") }
            }
        }
    }
}
