package com.jax.automation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.jax.automation.di.AppContainer
import com.jax.automation.ui.ScreenScaffold
import com.jax.automation.ui.viewmodel.CommandViewModel
import com.jax.automation.ui.viewmodel.JaxViewModelFactory
import com.jax.automation.ui.viewmodel.PreviewUi

@Composable
fun CommandScreen(container: AppContainer, navController: NavController) {
    val vm: CommandViewModel = viewModel(factory = JaxViewModelFactory(container))

    ScreenScaffold("Command", navController) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = vm.input,
                onValueChange = { vm.input = it },
                label = { Text("What should JAX do?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Button(
                onClick = { vm.interpret() },
                enabled = !vm.busy && vm.input.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Interpret") }

            if (vm.busy) CircularProgressIndicator()

            vm.error?.let { Text(it, color = Color(0xFFFF5A5A)) }

            if (vm.needsProjectInput) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(vm.question ?: "Which project?", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = vm.projectNameInput,
                            onValueChange = { vm.projectNameInput = it },
                            label = { Text("Project name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { vm.answerProject(vm.projectNameInput) },
                                enabled = vm.projectNameInput.isNotBlank()
                            ) { Text("Continue") }
                            OutlinedButton(onClick = { vm.cancel() }) { Text("Cancel") }
                        }
                    }
                }
            }

            vm.preview?.let { p ->
                CommandPreviewCard(p)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.execute(navController) },
                        enabled = !vm.busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("EXECUTE") }
                    OutlinedButton(
                        onClick = { vm.preview = null },
                        modifier = Modifier.weight(1f)
                    ) { Text("EDIT") }
                    OutlinedButton(
                        onClick = { vm.cancel() },
                        modifier = Modifier.weight(1f)
                    ) { Text("CANCEL") }
                }
            }
        }
    }
}

@Composable
private fun CommandPreviewCard(p: PreviewUi) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Plan preview", style = MaterialTheme.typography.titleMedium)
            PreviewRow("Command", p.command)
            PreviewRow("Target", p.target)
            PreviewRow("Project", p.project)
            PreviewRow("Scenes", p.scenes.ifBlank { "—" })
            PreviewRow("References", p.references.joinToString(", ").ifBlank { "—" })
            PreviewRow("Output count", p.outputCount.toString())
            PreviewRow("QA", if (p.qa) "Yes" else "No")
            PreviewRow("Download", if (p.download) "Yes" else "No")
        }
    }
}

@Composable
private fun PreviewRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(2.dp))
    }
}
