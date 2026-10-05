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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.jax.automation.di.AppContainer
import com.jax.automation.ui.ScreenScaffold
import com.jax.automation.ui.StatusPill
import com.jax.automation.ui.recoveryStateColor
import com.jax.automation.ui.viewmodel.DashboardViewModel
import com.jax.automation.ui.viewmodel.JaxViewModelFactory
import kotlinx.coroutines.launch

@Composable
private fun StatCard(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor
            )
        }
    }
}

@Composable
fun DashboardScreen(container: AppContainer, navController: NavController) {
    val vm: DashboardViewModel = viewModel(factory = JaxViewModelFactory(container))
    val accessibilityEnabled by vm.accessibilityEnabled.collectAsState()
    val automationBound by vm.automationBound.collectAsState()
    val chromeInstalled by vm.chromeInstalled.collectAsState()
    val plannerName by vm.plannerName.collectAsState()
    val currentProject by vm.currentProject.collectAsState()
    val counts by vm.queueCounts.collectAsState()
    val execState by vm.executorState.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.refresh() }

    ScreenScaffold("JAX", navController) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Status", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { vm.refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    Text("Refresh")
                }
            }

            StatCard(
                "Automation",
                if (automationBound) "CONNECTED" else "DISCONNECTED",
                if (automationBound) Color(0xFF00E5A0) else Color(0xFFFF5A5A)
            )
            StatCard(
                "Accessibility",
                if (accessibilityEnabled) "ENABLED" else "DISABLED",
                if (accessibilityEnabled) Color(0xFF00E5A0) else Color(0xFFFFA726)
            )
            StatCard(
                "Chrome",
                if (chromeInstalled) "CONNECTED" else "NOT CONNECTED",
                if (chromeInstalled) Color(0xFF00E5A0) else Color(0xFFFFA726)
            )
            StatCard(
                "AI Planner",
                plannerName,
                if (plannerName == "NOT CONFIGURED") Color(0xFFFFA726) else Color(0xFF00E5A0)
            )
            StatCard("Current Project", currentProject?.name ?: "—")
            StatCard("Queue", "${counts.done}/${counts.total} done · ${counts.pending} pending · ${counts.failed} failed")
            StatCard("Current Task", execState.currentTaskId ?: "—")

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Engine", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                StatusPill(execState.status.name, recoveryStateColor(execState.status))
            }
            if (execState.message.isNotBlank()) {
                Text(execState.message, style = MaterialTheme.typography.bodySmall)
            }

            Text("Controls", style = MaterialTheme.typography.titleLarge)
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
                OutlinedButton(
                    onClick = { scope.launch { vm.retryFailed(); vm.refresh() } },
                    modifier = Modifier.weight(1f)
                ) { Text("RETRY FAILED") }
                OutlinedButton(
                    onClick = { navController.navigate("logs") },
                    modifier = Modifier.weight(1f)
                ) { Text("OPEN LOGS") }
            }

            Text("Navigate", style = MaterialTheme.typography.titleLarge)
            val currentPid = currentProject?.projectId
            val navItems = listOf(
                "Command" to "command",
                "Projects" to "projects",
                "Queue" to if (currentPid != null) "queue/$currentPid" else "projects",
                "References" to "references",
                "Providers" to "providers",
                "Settings" to "settings",
                "Setup" to "setup"
            )
            navItems.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, route) ->
                        OutlinedButton(
                            onClick = { navController.navigate(route) },
                            modifier = Modifier.weight(1f)
                        ) { Text(label) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
