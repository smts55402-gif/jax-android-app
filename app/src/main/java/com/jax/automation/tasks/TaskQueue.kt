package com.jax.automation.tasks

import com.jax.automation.database.TaskDao
import com.jax.automation.logging.JaxLogger
import com.jax.automation.models.Task
import com.jax.automation.models.TaskStatus
import com.jax.automation.models.now
import kotlinx.coroutines.flow.Flow

/**
 * Queue-level rollups for a project.
 */
data class QueueCounts(
    val total: Int,
    val pending: Int,
    val done: Int,
    val failed: Int
)

/**
 * Thin, intention-revealing wrapper over [TaskDao]. Owns enqueue
 * deduplication, attempt bookkeeping and failed-task recovery.
 */
class TaskQueue(
    private val taskDao: TaskDao,
    private val logger: JaxLogger
) {
    /**
     * Enqueues [task], returning the id to track. When an active or completed
     * task already covers the same project + scene + type, the existing id is
     * returned and nothing is inserted.
     */
    suspend fun enqueue(task: Task): String {
        val existing = taskDao.findActive(task.projectId, task.sceneId, task.type)
        if (existing != null) {
            logger.i(
                "TaskQueue",
                "Duplicate task ignored; reusing ${existing.taskId} (status=${existing.status})",
                existing.taskId
            )
            return existing.taskId
        }
        taskDao.upsert(task)
        logger.d("TaskQueue", "Enqueued ${task.taskId} (${task.type}) for scene ${task.sceneId}", task.taskId)
        return task.taskId
    }

    suspend fun enqueueAll(tasks: List<Task>): List<String> = tasks.map { enqueue(it) }

    fun observeProjectTasks(projectId: String): Flow<List<Task>> =
        taskDao.observeByProject(projectId)

    fun observeTask(taskId: String): Flow<Task?> =
        taskDao.observeById(taskId)

    suspend fun getTask(taskId: String): Task? =
        taskDao.getById(taskId)

    suspend fun setStatus(taskId: String, status: TaskStatus, errorMessage: String? = null) {
        taskDao.updateStatus(taskId, status, errorMessage)
    }

    /** Increments the attempt counter and returns the new count. */
    suspend fun incrementAttempt(taskId: String): Int {
        taskDao.incrementAttempt(taskId)
        return taskDao.getById(taskId)?.attemptCount ?: 0
    }

    suspend fun setResult(taskId: String, resultPath: String) {
        taskDao.setResult(taskId, resultPath)
    }

    /** Replaces the stored prompt (used when QA corrections are appended). */
    suspend fun updatePrompt(taskId: String, prompt: String) {
        taskDao.getById(taskId)
            ?.copy(prompt = prompt, updatedAt = now())
            ?.let { taskDao.upsert(it) }
    }

    suspend fun nextPending(projectId: String): Task? =
        taskDao.nextPending(projectId)

    suspend fun counts(projectId: String): QueueCounts = QueueCounts(
        total = taskDao.countAll(projectId),
        pending = taskDao.countPending(projectId),
        done = taskDao.countDone(projectId),
        failed = taskDao.countFailed(projectId)
    )

    suspend fun failedTasks(projectId: String): List<Task> =
        taskDao.failedTasks(projectId)

    /** Resets every FAILED task of the project back to PENDING. Returns how many. */
    suspend fun retryFailed(projectId: String): Int =
        taskDao.resetFailed(projectId)

    /** Removes finished tasks (DOWNLOADED / APPROVED / SKIPPED) for the project. */
    suspend fun clearFinished(projectId: String) {
        taskDao.clearFinished(projectId)
    }
}
