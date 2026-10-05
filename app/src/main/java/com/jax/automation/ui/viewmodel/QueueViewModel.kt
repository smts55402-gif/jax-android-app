package com.jax.automation.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jax.automation.di.AppContainer
import com.jax.automation.models.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QueueViewModel(
    private val container: AppContainer,
    private val projectId: String
) : ViewModel() {

    val tasks: StateFlow<List<Task>> = container.queue.observeProjectTasks(projectId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val counts = MutableStateFlow(QueueCountsUi(0, 0, 0, 0))

    /** Type inferred from TaskExecutor.state. */
    val executorState = container.executor.state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val c = container.queue.counts(projectId)
            counts.value = QueueCountsUi(c.total, c.pending, c.done, c.failed)
        }
    }

    fun retryFailed() = viewModelScope.launch {
        container.queue.retryFailed(projectId)
        refresh()
    }

    fun clearFinished() = viewModelScope.launch {
        container.queue.clearFinished(projectId)
        refresh()
    }

    fun start() = container.executor.start(projectId)
    fun pause() = container.executor.pause()
    fun resume() = container.executor.resume()
    fun stop() = container.executor.stop()
}
