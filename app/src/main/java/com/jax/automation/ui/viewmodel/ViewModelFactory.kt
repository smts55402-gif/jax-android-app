package com.jax.automation.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jax.automation.di.AppContainer

/**
 * Manual factory. [projectId] is only used by [QueueViewModel]; other
 * ViewModels ignore it.
 */
class JaxViewModelFactory(
    private val container: AppContainer,
    private val projectId: String = ""
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
            DashboardViewModel(container)
        modelClass.isAssignableFrom(CommandViewModel::class.java) ->
            CommandViewModel(container)
        modelClass.isAssignableFrom(QueueViewModel::class.java) ->
            QueueViewModel(container, projectId)
        modelClass.isAssignableFrom(ProjectsViewModel::class.java) ->
            ProjectsViewModel(container)
        modelClass.isAssignableFrom(ReferencesViewModel::class.java) ->
            ReferencesViewModel(container)
        modelClass.isAssignableFrom(ProvidersViewModel::class.java) ->
            ProvidersViewModel(container)
        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(container)
        modelClass.isAssignableFrom(LogsViewModel::class.java) ->
            LogsViewModel(container)
        modelClass.isAssignableFrom(SetupViewModel::class.java) ->
            SetupViewModel(container)
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    } as T
}
