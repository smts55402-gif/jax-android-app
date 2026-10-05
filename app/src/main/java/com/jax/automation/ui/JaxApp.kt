package com.jax.automation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jax.automation.di.AppContainer
import com.jax.automation.models.RecoveryState
import com.jax.automation.models.TaskStatus
import com.jax.automation.ui.screens.CommandScreen
import com.jax.automation.ui.screens.DashboardScreen
import com.jax.automation.ui.screens.LogsScreen
import com.jax.automation.ui.screens.ProjectDetailScreen
import com.jax.automation.ui.screens.ProjectsScreen
import com.jax.automation.ui.screens.ProvidersScreen
import com.jax.automation.ui.screens.QueueScreen
import com.jax.automation.ui.screens.ReferencesScreen
import com.jax.automation.ui.screens.SettingsScreen
import com.jax.automation.ui.screens.SetupWizardScreen

@Composable
fun JaxApp(container: AppContainer) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "dashboard") {
        composable("dashboard") { DashboardScreen(container, navController) }
        composable("setup") { SetupWizardScreen(container, navController) }
        composable("command") { CommandScreen(container, navController) }
        composable("projects") { ProjectsScreen(container, navController) }
        composable(
            "project/{projectId}",
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { entry ->
            ProjectDetailScreen(
                container,
                navController,
                entry.arguments?.getString("projectId").orEmpty()
            )
        }
        composable(
            "queue/{projectId}",
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { entry ->
            QueueScreen(
                container,
                navController,
                entry.arguments?.getString("projectId").orEmpty()
            )
        }
        composable("references") { ReferencesScreen(container, navController) }
        composable("providers") { ProvidersScreen(container, navController) }
        composable("settings") { SettingsScreen(container, navController) }
        composable("logs") { LogsScreen(container, navController) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    navController: NavController,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (navController.previousBackStackEntry != null) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { padding -> content(padding) }
}

@Composable
fun StatusPill(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

fun taskStatusColor(status: TaskStatus): Color = when (status) {
    TaskStatus.PENDING -> Color(0xFF9AA3B2)
    TaskStatus.RUNNING, TaskStatus.GENERATING -> Color(0xFF4DA3FF)
    TaskStatus.QA -> Color(0xFFB388FF)
    TaskStatus.APPROVED, TaskStatus.DOWNLOADED -> Color(0xFF00E5A0)
    TaskStatus.FAILED -> Color(0xFFFF5A5A)
    TaskStatus.PAUSED -> Color(0xFFFFA726)
    else -> Color(0xFF9AA3B2)
}

fun recoveryStateColor(state: RecoveryState): Color = when (state) {
    RecoveryState.IDLE -> Color(0xFF9AA3B2)
    RecoveryState.ERROR -> Color(0xFFFF5A5A)
    RecoveryState.WAITING_USER -> Color(0xFFFFA726)
    RecoveryState.COMPLETE -> Color(0xFF00E5A0)
    else -> Color(0xFF4DA3FF)
}
