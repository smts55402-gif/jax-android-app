package com.jax.automation.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jax.automation.di.AppContainer
import com.jax.automation.models.AutomationLogEntry
import com.jax.automation.settings.JaxSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settingsState: StateFlow<JaxSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, JaxSettings())

    fun update(transform: (JaxSettings) -> JaxSettings) = viewModelScope.launch {
        container.settings.update(transform)
    }
}

class LogsViewModel(container: AppContainer) : ViewModel() {
    val logs: StateFlow<List<AutomationLogEntry>> = container.logger.liveLogs
}
