package com.jax.automation.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.jax.automation.di.AppContainer
import com.jax.automation.ui.ScreenScaffold
import com.jax.automation.ui.viewmodel.JaxViewModelFactory
import com.jax.automation.ui.viewmodel.SetupViewModel

private val stepTitles = listOf(
    "Welcome", "Accessibility", "Notifications", "Storage",
    "AI Provider", "Test Automation", "Chrome Test", "Done"
)

@Composable
fun SetupWizardScreen(container: AppContainer, navController: NavController) {
    val vm: SetupViewModel = viewModel(factory = JaxViewModelFactory(container))
    val context = LocalContext.current
    var step by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { vm.checkAll() }

    ScreenScaffold("Setup", navController) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                "Step ${step + 1} of ${stepTitles.size}: ${stepTitles[step]}",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (step) {
                    0 -> {
                        Text("Welcome to JAX — your on-device automation engine.", style = MaterialTheme.typography.bodyLarge)
                        Text("This wizard checks every requirement: accessibility service, notifications, AI providers, and Chrome.")
                    }
                    1 -> {
                        Text("JAX drives apps through Android's accessibility service. Grant access below, then come back here.")
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    if (vm.accessibilityOn) "Accessibility: ENABLED" else "Accessibility: DISABLED",
                                    color = if (vm.accessibilityOn) Color(0xFF00E5A0) else Color(0xFFFFA726)
                                )
                            }
                        }
                        Button(onClick = { vm.openAccessibilitySettings(context) }) {
                            Text("Open accessibility settings")
                        }
                        OutlinedButton(onClick = { vm.checkAll() }) { Text("Re-check") }
                    }
                    2 -> NotificationStep()
                    3 -> {
                        Text("JAX uses app-specific storage only — no storage permission is needed on modern Android.")
                        Text("Projects, downloads and logs live inside the app's private directories.")
                    }
                    4 -> {
                        Text("Pick your AI providers and save API keys. The planner needs a configured provider before commands can be interpreted.")
                        Button(onClick = { navController.navigate("providers") }) { Text("Open providers") }
                    }
                    5 -> {
                        Text("Probe the accessibility automation layer (reads the current screen text).")
                        Button(onClick = { vm.testAutomation() }, enabled = !vm.busy) { Text("Run test") }
                        if (vm.busy) CircularProgressIndicator()
                        vm.automationProbe?.let { Text(it) }
                    }
                    6 -> {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    if (vm.chromeOk) "Chrome: INSTALLED" else "Chrome: NOT FOUND",
                                    color = if (vm.chromeOk) Color(0xFF00E5A0) else Color(0xFFFF5A5A)
                                )
                            }
                        }
                        Button(onClick = { vm.testChrome() }, enabled = vm.chromeOk && !vm.busy) {
                            Text("Open Chrome test")
                        }
                        vm.chromeProbe?.let { Text(it) }
                    }
                    7 -> {
                        Text("Setup complete.", style = MaterialTheme.typography.titleMedium)
                        Text("Accessibility: ${if (vm.accessibilityOn) "enabled" else "NOT enabled"}")
                        Text("Chrome: ${if (vm.chromeOk) "installed" else "NOT installed"}")
                        Text("Automation probe: ${vm.automationProbe ?: "not run"}")
                        Button(onClick = {
                            navController.navigate("dashboard") {
                                popUpTo("setup") { inclusive = true }
                            }
                        }) { Text("Open dashboard") }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { if (step > 0) step-- },
                    enabled = step > 0,
                    modifier = Modifier.weight(1f)
                ) { Text("Back") }
                Button(
                    onClick = { if (step < stepTitles.size - 1) step++ },
                    modifier = Modifier.weight(1f)
                ) { Text(if (step == stepTitles.size - 1) "Finish" else "Next") }
            }
        }
    }
}

@Composable
private fun NotificationStep() {
    var status by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> status = if (granted) "Granted" else "Denied" }

    Text("JAX posts progress notifications while automation runs.")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Button(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
            Text("Request permission")
        }
        if (status.isNotEmpty()) Text(status)
    } else {
        Text("Not required on this Android version.")
    }
}
