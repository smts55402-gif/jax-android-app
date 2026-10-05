package com.jax.automation.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jax.automation.ai.ProviderInfo
import com.jax.automation.ai.ProviderRole
import com.jax.automation.di.AppContainer
import com.jax.automation.settings.JaxSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProvidersViewModel(private val container: AppContainer) : ViewModel() {

    val info = MutableStateFlow<List<ProviderInfo>>(emptyList())

    /** Per-provider last test result text, keyed by provider id. */
    val testStatus = MutableStateFlow<Map<String, String>>(emptyMap())

    val settings: StateFlow<JaxSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, JaxSettings())

    fun refresh() {
        viewModelScope.launch {
            info.value = runCatching { container.registry.providerInfo() }
                .getOrDefault(emptyList())
        }
    }

    fun setEnabled(id: String, enabled: Boolean) = viewModelScope.launch {
        container.registry.setEnabled(id, enabled)
        refresh()
    }

    fun saveKey(id: String, key: String) = viewModelScope.launch {
        if (key.isNotBlank()) container.registry.saveApiKey(id, key.trim())
        refresh()
    }

    fun clearKey(id: String) = viewModelScope.launch {
        container.registry.deleteApiKey(id)
        refresh()
    }

    fun test(id: String) = viewModelScope.launch {
        testStatus.value = testStatus.value + (id to "Testing…")
        val ok = runCatching { container.registry.testConnection(id) }.getOrDefault(false)
        testStatus.value = testStatus.value + (id to if (ok) "OK — connection works" else "FAILED — check key/model")
    }

    fun setDefault(role: ProviderRole, id: String) = viewModelScope.launch {
        container.registry.setDefault(role, id)
        refresh()
    }

    fun saveModel(role: ProviderRole, model: String) = viewModelScope.launch {
        val trimmed = model.trim()
        if (trimmed.isNotEmpty()) container.registry.saveModel(role, trimmed)
        refresh()
    }

    fun saveBaseUrl(id: String, url: String) = viewModelScope.launch {
        container.registry.saveCustomBaseUrl(id, url.trim())
        refresh()
    }
}
