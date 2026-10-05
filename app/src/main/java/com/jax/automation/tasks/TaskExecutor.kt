package com.jax.automation.tasks

import android.content.Context
import com.jax.automation.automation.AutomationAction
import com.jax.automation.automation.AutomationAdapter
import com.jax.automation.automation.AutomationEngine
import com.jax.automation.automation.PlannedAction
import com.jax.automation.automation.PlannedActionParser
import com.jax.automation.browser.ChromeAdapter
import com.jax.automation.database.ProjectDao
import com.jax.automation.database.SceneDao
import com.jax.automation.downloads.DownloadManager
import com.jax.automation.downloads.FileNaming
import com.jax.automation.flow.GoogleFlowAdapter
import com.jax.automation.logging.JaxLogger
import com.jax.automation.models.QaStatus
import com.jax.automation.models.RecoveryState
import com.jax.automation.models.Task
import com.jax.automation.models.TaskStatus
import com.jax.automation.models.TaskType
import com.jax.automation.qa.VisionQA
import com.jax.automation.settings.SettingsRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Observable executor state. Active automation is reported as RUNNING,
 * cooperative pause gates as PAUSED, and genuine user-wait gates
 * (ASK_USER / security challenges / QA exhaustion) as WAITING_USER;
 * the [message] field carries the human-readable detail.
 */
data class ExecutorState(
    val status: RecoveryState,
    val currentTaskId: String?,
    val total: Int,
    val done: Int,
    val message: String = ""
)

/**
 * Orchestrates the task queue: picks the next PENDING task, runs it through
 * generation -> QA -> download (or a custom automation plan / URL), and
 * advances. Pause takes effect between tasks and at user gates; a task
 * mid-generation is never preempted unsafely.
 */
class TaskExecutor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val adapter: AutomationAdapter,
    private val queue: TaskQueue,
    private val chrome: ChromeAdapter,
    private val flow: GoogleFlowAdapter,
    private val qa: VisionQA,
    private val downloads: DownloadManager,
    private val projectDao: ProjectDao,
    private val sceneDao: SceneDao,
    private val recovery: RecoveryManager,
    private val settings: SettingsRepository,
    private val logger: JaxLogger
) {
    private val engine = AutomationEngine(adapter, logger, onAskUser = { msg -> enterWaitingUser(msg) })

    private var job: Job? = null

    @Volatile
    private var paused = false

    private var gate: CompletableDeferred<Unit>? = null
    private var lastProjectId: String? = null

    private val _state = MutableStateFlow(ExecutorState(RecoveryState.IDLE, null, 0, 0))
    val state: StateFlow<ExecutorState> = _state.asStateFlow()

    private fun setState(
        status: RecoveryState,
        taskId: String?,
        total: Int,
        done: Int,
        message: String = ""
    ) {
        _state.value = ExecutorState(status, taskId, total, done, message)
        logger.d("TaskExecutor", "state=$status task=$taskId done=$done/$total $message", taskId)
    }

    // ------------------------------------------------------------------
    // Public controls
    // ------------------------------------------------------------------

    fun start(projectId: String) {
        if (job?.isActive == true) {
            logger.i("TaskExecutor", "Queue already running; ignoring start for $projectId")
            return
        }
        lastProjectId = projectId
        paused = false
        gate = null
        job = scope.launch { runLoop(projectId) }
    }

    /** Cooperative pause: takes effect between tasks and at user gates. */
    fun pause() {
        paused = true
        logger.i("TaskExecutor", "Pause requested; will pause between tasks")
    }

    fun resume() {
        paused = false
        gate?.complete(Unit)
        gate = null
        logger.i("TaskExecutor", "Resume requested")
        val s = _state.value.status
        val pid = lastProjectId
        if (job?.isActive != true && pid != null &&
            (s == RecoveryState.WAITING_USER || s == RecoveryState.PAUSED || s == RecoveryState.IDLE)
        ) {
            scope.launch {
                if (queue.counts(pid).pending > 0) {
                    start(pid)
                } else {
                    logger.i("TaskExecutor", "Resume requested; nothing pending for $pid")
                }
            }
        }
    }

    fun stop() {
        paused = false
        gate?.complete(Unit)
        gate = null
        job?.cancel()
        job = null
        scope.launch { recovery.clear() }
        setState(RecoveryState.IDLE, null, 0, 0, "Stopped")
        logger.i("TaskExecutor", "Stopped")
    }

    suspend fun retryFailed(projectId: String) {
        val n = queue.retryFailed(projectId)
        logger.i("TaskExecutor", "Reset $n failed tasks to PENDING for $projectId")
        start(projectId)
    }

    /**
     * Called on app start (see [QueueResumeWorker]). Never assumes a task
     * succeeded: an interrupted in-flight task is marked PAUSED for review.
     */
    suspend fun restoreAfterRestart(): Boolean {
        val cfg = settings.settings.first()
        if (!cfg.autoResume) {
            logger.i("TaskExecutor", "Auto-resume disabled; skipping recovery")
            return false
        }
        val snap = recovery.load() ?: return false
        val cur = snap.currentTaskId ?: return false
        val task = queue.getTask(cur) ?: return false
        val interrupted = listOf(
            TaskStatus.RUNNING, TaskStatus.PLANNING, TaskStatus.WAITING,
            TaskStatus.GENERATING, TaskStatus.RESULT_FOUND, TaskStatus.QA,
            TaskStatus.DOWNLOADING
        )
        if (task.status in interrupted) {
            queue.setStatus(cur, TaskStatus.PAUSED, "Interrupted by app restart — review and resume")
            logger.w(
                "TaskExecutor",
                "Recovered interrupted task ${task.sceneId}; marked PAUSED (never assuming success)",
                cur
            )
        }
        lastProjectId = snap.projectId
        setState(RecoveryState.IDLE, null, 0, 0, "Recovered after restart")
        return true
    }

    /**
     * Called by engine/flow callbacks when the automation needs the user.
     * Sets WAITING_USER and suspends until [resume] (or [stop]) releases
     * the gate.
     */
    suspend fun enterWaitingUser(message: String) {
        val s = _state.value
        setState(RecoveryState.WAITING_USER, s.currentTaskId, s.total, s.done, message)
        logger.w("TaskExecutor", "Waiting for user: $message", s.currentTaskId)
        val g = CompletableDeferred<Unit>()
        gate = g
        try {
            g.await()
        } finally {
            if (gate === g) gate = null
        }
        logger.i("TaskExecutor", "User gate released", s.currentTaskId)
    }

    // ------------------------------------------------------------------
    // Run loop
    // ------------------------------------------------------------------

    private suspend fun runLoop(projectId: String) {
        try {
            var counts = queue.counts(projectId)
            setState(RecoveryState.RUNNING, null, counts.total, counts.done, "Queue started")
            logger.i("TaskExecutor", "Run loop started for $projectId (${counts.total} tasks)")
            while (coroutineContext.isActive) {
                if (paused) awaitGate("Paused")
                val task = queue.nextPending(projectId) ?: break
                counts = queue.counts(projectId)
                runTask(task, projectId, counts)
                counts = queue.counts(projectId)
                setState(RecoveryState.RUNNING, null, counts.total, counts.done, "Queue running")
            }
            counts = queue.counts(projectId)
            setState(RecoveryState.COMPLETE, null, counts.total, counts.done, "Queue complete")
            logger.i("TaskExecutor", "Queue complete for $projectId")
        } catch (e: CancellationException) {
            if (_state.value.status != RecoveryState.IDLE) {
                setState(RecoveryState.IDLE, null, 0, 0, "Cancelled")
            }
            throw e
        }
    }

    private suspend fun awaitGate(reason: String) {
        val s = _state.value
        setState(RecoveryState.PAUSED, s.currentTaskId, s.total, s.done, reason)
        val g = CompletableDeferred<Unit>()
        gate = g
        try {
            g.await()
        } finally {
            if (gate === g) gate = null
        }
    }

    private suspend fun runTask(task: Task, projectId: String, counts: QueueCounts) {
        setState(RecoveryState.RUNNING, task.taskId, counts.total, counts.done, "Running ${task.sceneId}")
        queue.setStatus(task.taskId, TaskStatus.RUNNING)
        recovery.save(RecoverySnapshot(projectId, null, task.taskId, "start", task.attemptCount, null))
        logger.i("TaskExecutor", "Running task ${task.sceneId} (${task.type})", task.taskId)
        try {
            when (task.type) {
                TaskType.GENERATE_IMAGE.name, TaskType.BATCH_GENERATE.name ->
                    generateScene(task, projectId)
                TaskType.OPEN_URL.name ->
                    runCustomUrl(task)
                else ->
                    runCustomPlan(task)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("TaskExecutor", "Task ${task.taskId} failed: ${e.message}", task.taskId, e)
            queue.setStatus(task.taskId, TaskStatus.FAILED, e.message)
            setState(RecoveryState.ERROR, task.taskId, counts.total, counts.done, e.message ?: "Task failed")
        }
    }

    // ------------------------------------------------------------------
    // Custom plan / URL tasks
    // ------------------------------------------------------------------

    private suspend fun runCustomPlan(task: Task) {
        val plan = try {
            PlannedActionParser.parse(task.metadata)
        } catch (e: IllegalArgumentException) {
            logger.e("TaskExecutor", "Invalid plan JSON for ${task.taskId}", task.taskId, e)
            queue.setStatus(task.taskId, TaskStatus.FAILED, "Invalid plan JSON")
            setState(RecoveryState.ERROR, task.taskId, _state.value.total, _state.value.done, "Invalid plan JSON")
            return
        }
        val result = engine.execute(task.taskId, plan)
        if (result.ok) {
            queue.setStatus(task.taskId, TaskStatus.APPROVED)
            logger.i("TaskExecutor", "Custom plan completed for ${task.taskId}", task.taskId)
        } else {
            val msg = result.error?.message ?: "Automation engine failed"
            queue.setStatus(task.taskId, TaskStatus.FAILED, msg)
            setState(RecoveryState.ERROR, task.taskId, _state.value.total, _state.value.done, msg)
        }
    }

    private suspend fun runCustomUrl(task: Task) {
        val url = try {
            JSONObject(task.metadata).optString("url").trim()
        } catch (e: Exception) {
            ""
        }
        if (url.isBlank()) {
            queue.setStatus(task.taskId, TaskStatus.FAILED, "Missing url in task metadata")
            setState(
                RecoveryState.ERROR, task.taskId, _state.value.total, _state.value.done,
                "Missing url in task metadata"
            )
            return
        }
        val result = engine.execute(task.taskId, listOf(PlannedAction(AutomationAction.OPEN_URL, url = url)))
        if (result.ok) {
            queue.setStatus(task.taskId, TaskStatus.APPROVED)
            logger.i("TaskExecutor", "Opened URL for ${task.taskId}", task.taskId)
        } else {
            val msg = result.error?.message ?: "Failed to open URL"
            queue.setStatus(task.taskId, TaskStatus.FAILED, msg)
            setState(RecoveryState.ERROR, task.taskId, _state.value.total, _state.value.done, msg)
        }
    }

    // ------------------------------------------------------------------
    // Scene generation: generate -> QA -> download
    // ------------------------------------------------------------------

    private suspend fun generateScene(task: Task, projectId: String) {
        try {
            val projectName = projectDao.getById(projectId)?.name ?: projectId
            val cfg = settings.settings.first()
            val retryEngine = RetryEngine()

            queue.setStatus(task.taskId, TaskStatus.GENERATING)
            setState(
                RecoveryState.GENERATING, task.taskId,
                _state.value.total, _state.value.done, "Generating ${task.sceneId}"
            )
            val workDir = downloads.sceneDir(projectName, task.sceneId)

            var prompt = task.prompt
            var attempt = task.attemptCount
            while (true) {
                attempt++
                queue.incrementAttempt(task.taskId)
                recovery.save(RecoverySnapshot(projectId, null, task.taskId, "generating", attempt, null))
                logger.i("TaskExecutor", "Generation attempt $attempt for ${task.sceneId}", task.taskId)

                val gen = flow.generateSingle(
                    task, prompt, workDir, cfg.defaultAspectRatio, cfg.defaultOutputCount
                )
                if (!gen.ok) {
                    val msg = gen.error?.message ?: "Generation failed"
                    queue.setStatus(task.taskId, TaskStatus.FAILED, msg)
                    setState(RecoveryState.ERROR, task.taskId, _state.value.total, _state.value.done, msg)
                    recovery.save(RecoverySnapshot(projectId, null, task.taskId, "failed", attempt, null))
                    return
                }
                val imageFile = gen.imageFile
                if (imageFile == null) {
                    queue.setStatus(task.taskId, TaskStatus.FAILED, "Generation returned no image file")
                    setState(
                        RecoveryState.ERROR, task.taskId, _state.value.total, _state.value.done,
                        "Generation returned no image file"
                    )
                    return
                }
                queue.setStatus(task.taskId, TaskStatus.RESULT_FOUND)
                recovery.save(RecoverySnapshot(projectId, null, task.taskId, "result_found", attempt, null))

                if (cfg.qaEnabled) {
                    queue.setStatus(task.taskId, TaskStatus.QA)
                    setState(
                        RecoveryState.QA, task.taskId,
                        _state.value.total, _state.value.done, "QA ${task.sceneId}"
                    )
                    val qaResult = qa.evaluate(task, imageFile, prompt)
                    if (qaResult.status == QaStatus.FAIL) {
                        val outcome = retryEngine.decide(qaResult, attempt, cfg.maxRetryAttempts)
                        if (outcome.decision == RetryDecision.RETRY) {
                            prompt = prompt + "\n\n" + (outcome.correctionInstruction ?: "")
                            queue.updatePrompt(task.taskId, prompt)
                            logger.i(
                                "TaskExecutor",
                                "QA failed for ${task.sceneId} (attempt $attempt); retrying with corrections",
                                task.taskId
                            )
                            continue
                        }
                        val reasons = qaResult.reasons.joinToString("; ")
                        queue.setStatus(
                            task.taskId, TaskStatus.PAUSED,
                            "QA failed after $attempt attempts: $reasons"
                        )
                        recovery.save(RecoverySnapshot(projectId, null, task.taskId, "qa_paused", attempt, null))
                        enterWaitingUser(
                            "Scene ${task.sceneId}: QA failed after $attempt attempts ($reasons). " +
                                "Fix the prompt or skip, then Resume."
                        )
                        return
                    }
                    logger.i("TaskExecutor", "QA passed for ${task.sceneId}", task.taskId)
                }

                queue.setStatus(task.taskId, TaskStatus.APPROVED)
                queue.setStatus(task.taskId, TaskStatus.DOWNLOADING)
                setState(
                    RecoveryState.DOWNLOADING, task.taskId,
                    _state.value.total, _state.value.done, "Downloading ${task.sceneId}"
                )

                val meta = try {
                    JSONObject(task.metadata)
                } catch (e: Exception) {
                    JSONObject()
                }
                val sceneIndex = meta.optInt(
                    "sceneIndex",
                    task.sceneId.filter { it.isDigit() }.toIntOrNull() ?: 1
                )
                val startSec = meta.optInt("startSec", 0)
                val endSec = meta.optInt("endSec", startSec)
                val tag = meta.optString("tag", "Scene").ifBlank { "Scene" }
                val fileName = FileNaming.sceneFileName(sceneIndex, startSec, endSec, tag, attempt)

                val saved = downloads.saveApproved(imageFile, projectName, task.sceneId, fileName, task.taskId)
                if (downloads.verifyExists(saved.absolutePath)) {
                    queue.setResult(task.taskId, saved.absolutePath)
                    queue.setStatus(task.taskId, TaskStatus.DOWNLOADED)
                    recovery.save(RecoverySnapshot(projectId, task.taskId, null, "downloaded", attempt, null))
                    logger.i("TaskExecutor", "Task ${task.sceneId} complete -> ${saved.absolutePath}", task.taskId)
                    return
                }
                queue.setStatus(task.taskId, TaskStatus.FAILED, "Downloaded file verification failed")
                setState(
                    RecoveryState.ERROR, task.taskId, _state.value.total, _state.value.done,
                    "Downloaded file verification failed"
                )
                recovery.save(RecoverySnapshot(projectId, null, task.taskId, "failed", attempt, null))
                return
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("TaskExecutor", "generateScene failed: ${e.message}", task.taskId, e)
            queue.setStatus(task.taskId, TaskStatus.FAILED, e.message)
            setState(RecoveryState.ERROR, task.taskId, _state.value.total, _state.value.done, e.message ?: "Generation failed")
        }
    }
}
