package com.jax.automation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.jax.automation.di.AppContainer
import com.jax.automation.models.AutomationLogEntry
import com.jax.automation.ui.ScreenScaffold
import com.jax.automation.ui.viewmodel.JaxViewModelFactory
import com.jax.automation.ui.viewmodel.LogsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val logTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

private fun levelColor(level: String): Color = when (level.uppercase()) {
    "ERROR", "E" -> Color(0xFFFF5A5A)
    "WARN", "W" -> Color(0xFFFFA726)
    "DEBUG", "D" -> Color(0xFF9AA3B2)
    else -> Color(0xFF00E5A0)
}

@Composable
fun LogsScreen(container: AppContainer, navController: NavController) {
    val vm: LogsViewModel = viewModel(factory = JaxViewModelFactory(container))
    val logs by vm.logs.collectAsState()

    ScreenScaffold("Logs", navController) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            items(logs, key = { it.id }) { entry ->
                LogRow(entry)
                Divider()
            }
        }
    }
}

@Composable
private fun LogRow(entry: AutomationLogEntry) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row {
            Text(
                logTimeFormat.format(Date(entry.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = levelColor(entry.level)
            )
            Text(
                "  ${entry.level}  ${entry.tag}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(entry.message, style = MaterialTheme.typography.bodySmall)
    }
}
