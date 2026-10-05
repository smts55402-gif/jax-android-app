package com.jax.automation.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.jax.automation.di.AppContainer
import com.jax.automation.models.Project
import com.jax.automation.models.StructuredTask
import com.jax.automation.models.Task
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * UI-level command preview. Mirrors the planner's CommandPreview fields so
 * the UI never depends on the ai module's data class layout.
 */
data class PreviewUi(
    val command: String,
    val target: String,
    val project: String,
    val scenes: String,
    val references: List<String>,
    val outputCount: Int,
    val qa: Boolean,
    val download: Boolean
)

class CommandViewModel(private val container: AppContainer) : ViewModel() {

    var input by mutableStateOf("")
    var busy by mutableStateOf(false)
    var needsProjectInput by mutableStateOf(false)
    var question by mutableStateOf<String?>(null)
    var projectNameInput by mutableStateOf("")
    var preview by mutableStateOf<PreviewUi?>(null)
    var error by mutableStateOf<String?>(null)

    private var pendingTasks: List<StructuredTask> = emptyList()

    fun interpret() {
        if (busy || input.isBlank()) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                val result = container.planner.plan(input.trim())
                pendingTasks = result.tasks
                if (result.needsUserInput) {
                    needsProjectInput = true
                    question = result.question
                    preview = null
                } else {
                    needsProjectInput = false
                    preview = result.preview?.let { p ->
                        PreviewUi(
                            command = input,
                            target = p.target,
                            project = p.project,
                            scenes = p.scenes,
                            references = p.references,
                            outputCount = p.outputCount,
                            qa = p.qa,
                            download = p.download
                        )
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: "Interpret failed"
            } finally {
                busy = false
            }
        }
    }

    /** User answered the planner's follow-up question with a project name. */
    fun answerProject(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val tasks = pendingTasks.map { it.copy(project = trimmed) }
        pendingTasks = tasks
        needsProjectInput = false
        projectNameInput = trimmed
        preview = PreviewUi(
            command = input,
            target = tasks.firstOrNull()?.target ?: "GOOGLE_FLOW",
            project = trimmed,
            scenes = tasks.map { it.sceneId }.distinct().joinToString(", "),
            references = tasks.flatMap { it.characters + it.locations + it.objects }.distinct(),
            outputCount = tasks.sumOf { it.variations },
            qa = tasks.any { it.qaRequired },
            download = tasks.any { it.download }
        )
    }

    /** Find-or-create the project, enqueue real Task entities, start execution. */
    fun execute(navController: NavController) {
        if (busy || pendingTasks.isEmpty()) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                val name = (preview?.project ?: projectNameInput).ifBlank { "Untitled" }
                val existing = container.projectDao.getByName(name)
                val pid = existing?.projectId ?: run {
                    val p = Project(name = name)
                    container.projectDao.upsert(p)
                    p.projectId
                }
                container.settings.update { it.copy(currentProjectId = pid) }
                val tasks = pendingTasks.map { t ->
                    Task(
                        projectId = pid,
                        sceneId = t.sceneId,
                        type = t.action,
                        prompt = container.promptBuilder.build(t, null),
                        references = t.characters + t.locations + t.objects,
                        metadata = JSONObject()
                            .put("sceneId", t.sceneId)
                            .put("startSec", 0)
                            .put("endSec", 0)
                            .put("tag", t.style)
                            .put("variations", t.variations)
                            .toString()
                    )
                }
                container.queue.enqueueAll(tasks)
                container.executor.start(pid)
                cancel()
                navController.navigate("queue/$pid")
            } catch (e: Exception) {
                error = e.message ?: "Execute failed"
            } finally {
                busy = false
            }
        }
    }

    fun cancel() {
        preview = null
        needsProjectInput = false
        pendingTasks = emptyList()
        projectNameInput = ""
        error = null
    }
}
