package com.jax.automation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.jax.automation.di.AppContainer
import com.jax.automation.ui.ScreenScaffold
import com.jax.automation.ui.viewmodel.JaxViewModelFactory
import com.jax.automation.ui.viewmodel.ProjectsViewModel

@Composable
fun ProjectDetailScreen(
    container: AppContainer,
    navController: NavController,
    projectId: String
) {
    val vm: ProjectsViewModel = viewModel(factory = JaxViewModelFactory(container))
    val project by vm.projectById(projectId).collectAsState()
    val scenes by vm.projectScenes(projectId).collectAsState(initial = emptyList())
    var showImport by remember { mutableStateOf(false) }
    var scriptText by remember { mutableStateOf("") }
    var importMsg by remember { mutableStateOf<String?>(null) }

    ScreenScaffold("Project", navController) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(project?.name ?: "…", style = MaterialTheme.typography.headlineSmall)
            Text(
                "${scenes.size} scenes imported",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            importMsg?.let { Text(it) }

            Button(
                onClick = { showImport = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("IMPORT SCRIPT") }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { navController.navigate("queue/$projectId") },
                    modifier = Modifier.weight(1f)
                ) { Text("START QUEUE") }
                OutlinedButton(
                    onClick = { container.executor.pause() },
                    modifier = Modifier.weight(1f)
                ) { Text("PAUSE QUEUE") }
            }
            OutlinedButton(
                onClick = { navController.navigate("queue/$projectId") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("OPEN QUEUE") }
        }
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("Import script") },
            text = {
                OutlinedTextField(
                    value = scriptText,
                    onValueChange = { scriptText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    minLines = 8,
                    placeholder = { Text("Paste the script text…") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.importScript(projectId, scriptText) { n ->
                        importMsg = "Imported $n scenes"
                        showImport = false
                    }
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { showImport = false }) { Text("Cancel") }
            }
        )
    }
}
