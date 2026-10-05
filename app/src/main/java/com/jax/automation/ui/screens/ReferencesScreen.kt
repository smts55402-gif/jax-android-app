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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.jax.automation.di.AppContainer
import com.jax.automation.models.ReferenceCategory
import com.jax.automation.models.ReferenceItem
import com.jax.automation.ui.ScreenScaffold
import com.jax.automation.ui.viewmodel.JaxViewModelFactory
import com.jax.automation.ui.viewmodel.ReferencesViewModel

@Composable
fun ReferencesScreen(container: AppContainer, navController: NavController) {
    val vm: ReferencesViewModel = viewModel(factory = JaxViewModelFactory(container))
    val category by vm.category.collectAsState()
    val items by vm.items.collectAsState()
    val categories = remember { ReferenceCategory.entries.toList() }
    var dialogItem by remember { mutableStateOf<ReferenceItem?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    ScreenScaffold("References", navController) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            ScrollableTabRow(selectedTabIndex = categories.indexOf(category)) {
                categories.forEach { cat ->
                    Tab(
                        selected = cat == category,
                        onClick = { vm.setCategory(cat) },
                        text = { Text(cat.name) }
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = { dialogItem = null; showDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Add")
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall)
                                if (item.description.isNotBlank()) {
                                    Text(
                                        item.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    "v${item.version}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (item.promptBlock.isNotBlank()) {
                                    Text(
                                        item.promptBlock.take(140),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            IconButton(onClick = { dialogItem = item; showDialog = true }) {
                                Text("Edit", style = MaterialTheme.typography.labelMedium)
                            }
                            IconButton(onClick = { vm.delete(item.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        ReferenceDialog(
            category = category,
            existing = dialogItem,
            onDismiss = { showDialog = false },
            onSave = { item -> vm.upsert(item); showDialog = false }
        )
    }
}

@Composable
private fun ReferenceDialog(
    category: ReferenceCategory,
    existing: ReferenceItem?,
    onDismiss: () -> Unit,
    onSave: (ReferenceItem) -> Unit
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var description by remember(existing) { mutableStateOf(existing?.description ?: "") }
    var promptBlock by remember(existing) { mutableStateOf(existing?.promptBlock ?: "") }
    var negativeRules by remember(existing) {
        mutableStateOf(existing?.negativeRules?.joinToString(", ") ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add reference" else "Edit reference") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") }
                )
                OutlinedTextField(
                    value = promptBlock,
                    onValueChange = { promptBlock = it },
                    label = { Text("Prompt block") },
                    minLines = 3
                )
                OutlinedTextField(
                    value = negativeRules,
                    onValueChange = { negativeRules = it },
                    label = { Text("Negative rules (comma-separated)") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val base = existing ?: ReferenceItem(category = category, name = name.trim())
                    onSave(
                        base.copy(
                            category = category,
                            name = name.trim(),
                            description = description.trim(),
                            promptBlock = promptBlock.trim(),
                            negativeRules = negativeRules.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() },
                            version = (existing?.version ?: 0) + 1
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
