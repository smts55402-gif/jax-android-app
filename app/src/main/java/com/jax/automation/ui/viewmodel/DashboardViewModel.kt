package com.jax.automation.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jax.automation.accessibility.JaxAccessibilityService
import com.jax.automation.di.AppContainer
import com.jax.automation.models.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Plain count holder so the UI never depends on the tasks module's data class layout. */
data class QueueCountsUi(val total: Int, val pending: Int, val done: Int, val failed: Int)

class DashboardViewModel(private val container: AppContainer) : ViewModel() {

    val accessibilityEnabled = MutableStateFlow(false)
    val automationBound = MutableStateFlow(false)
    val chromeInstalled = MutableStateFlow(false)
    val plannerName = MutableStateFlow("—")
    val queueCounts = MutableStateFlow(QueueCountsUi(0, 0, 0, 0))

    /** Type inferred from TaskExecutor.state — no dependency on its data class layout. */
    val executorState = container.executor.state

    val currentProject: StateFlow<Project?> =
        combine(container.settings.settings, container.projectDao.observeAll()) { s, list ->
            list.firstOrNull { it.projectId == s.currentProjectId }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private suspend fun currentProjectId(): String =
        container.settings.settings.first().currentProjectId

    fun refresh() {
        viewModelScope.launch {
            accessibilityEnabled.value = JaxAccessibilityService.isEnabled(container.app)
            automationBound.value = JaxAccessibilityService.instance != null
            chromeInstalled.value = container.chrome.isChromeInstalled()
            plannerName.value = runCatching {
                container.registry.providerInfo()
                    .firstOrNull { it.isDefaultPlanner }?.displayName
            }.getOrNull() ?: "NOT CONFIGURED"
            val pid = currentProjectId()
            queueCounts.value = if (pid.isNotBlank()) {
                val c = container.queue.counts(pid)
                QueueCountsUi(c.total, c.pending, c.done, c.failed)
            } else {
                QueueCountsUi(0, 0, 0, 0)
            }
        }
    }

    fun start() = viewModelScope.launch {
        val pid = currentProjectId()
        if (pid.isNotBlank()) container.executor.start(pid)
    }

    fun pause() = container.executor.pause()
    fun resume() = container.executor.resume()
    fun stop() = container.executor.stop()

    suspend fun retryFailed() {
        val pid = currentProjectId()
        if (pid.isNotBlank()) container.executor.retryFailed(pid)
    }
}
