package com.jax.automation.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jax.automation.di.AppContainer
import com.jax.automation.files.ScriptImporter
import com.jax.automation.models.Project
import com.jax.automation.models.Scene
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectsViewModel(private val container: AppContainer) : ViewModel() {

    val projects: StateFlow<List<Project>> = container.projectDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun createProject(name: String) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            container.projectDao.upsert(Project(name = trimmed))
        }
    }

    fun deleteProject(project: Project) = viewModelScope.launch {
        container.projectDao.delete(project)
    }

    fun importScript(projectId: String, text: String, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val scenes = ScriptImporter.parse(projectId, text)
            container.sceneDao.upsertAll(scenes)
            onDone(scenes.size)
        }
    }

    fun projectScenes(projectId: String): Flow<List<Scene>> =
        container.sceneDao.observeByProject(projectId)

    fun projectById(projectId: String): StateFlow<Project?> =
        container.projectDao.observeAll()
            .map { list -> list.firstOrNull { it.projectId == projectId } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
