package com.jax.automation.tasks

import com.jax.automation.models.Task
import com.jax.automation.models.TaskStatus

/**
 * Deduplication rules for the task queue.
 *
 * A candidate task is a duplicate when another task already exists for the
 * same project + scene + type AND that existing task is not in a terminal
 * "retryable" state. FAILED and SKIPPED tasks are deliberately re-enqueueable;
 * everything else (PENDING, RUNNING, GENERATING, ..., DOWNLOADED, APPROVED,
 * PAUSED) blocks a duplicate so completed/active work is never redone.
 *
 * Pure Kotlin — safe to run in JVM unit tests (no Android imports).
 */
object QueueRules {
    private val TERMINAL_FOR_DEDUPE = setOf(TaskStatus.FAILED, TaskStatus.SKIPPED)

    /**
     * Returns the existing task that [candidate] duplicates, or null when the
     * candidate is safe to enqueue.
     */
    fun findDuplicate(existing: List<Task>, candidate: Task): Task? {
        return existing.firstOrNull { other ->
            other.projectId == candidate.projectId &&
                other.sceneId == candidate.sceneId &&
                other.type == candidate.type &&
                other.status !in TERMINAL_FOR_DEDUPE
        }
    }
}
