package com.jax.automation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.jax.automation.di.AppContainer
import com.jax.automation.models.Task
import com.jax.automation.ui.ScreenScaffold
import com.jax.automation.ui.StatusPill
import com.jax.automation.ui.taskStatusColor
import com.jax.automation.ui.viewmodel.JaxViewModelFactory
import com.jax.automation.ui.viewmodel.QueueViewModel

@Composable
fun QueueScreen(container: AppContainer, navController: NavController, projectId: String) {
    val vm: QueueViewModel = viewModel(factory = JaxViewModelFactory(container, projectId))
    val tasks by vm.tasks.collectAsState()
    val counts by vm.counts.collectAsState()

    LaunchedEffect(projectId) { vm.refresh() }

    ScreenScaffold("Queue", navController) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Pending ${counts.pending} · Done ${counts.done}/${counts.total} · Failed ${counts.failed}",
                style = MaterialTheme.typography.titleSmall
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.start() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("START")
                }
                OutlinedButton(onClick = { vm.pause() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Pause, contentDescription = null)
                    Text("PAUSE")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.resume() }, modifier = Modifier.weight(1f)) { Text("RESUME") }
                OutlinedButton(onClick = { vm.stop() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Text("STOP")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.retryFailed() }, modifier = Modifier.weight(1f)) {
                    Text("RETRY FAILED")
                }
                OutlinedButton(onClick = { vm.clearFinished() }, modifier = Modifier.weight(1f)) {
                    Text("CLEAR FINISHED")
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tasks, key = { it.taskId }) { task -> TaskRow(task) }
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    task.sceneId.ifBlank { task.taskId.take(8) },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(task.status.name, taskStatusColor(task.status))
            }
            Text(
                "Type: ${task.type} · Attempts: ${task.attemptCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!task.errorMessage.isNullOrBlank()) {
                Text(
                    task.errorMessage!!.take(160),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF5A5A)
                )
            }
            if (!task.resultPath.isNullOrBlank()) {
                Text(
                    "Result: ${task.resultPath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
