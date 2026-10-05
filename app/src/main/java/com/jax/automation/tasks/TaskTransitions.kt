package com.jax.automation.tasks

import com.jax.automation.models.TaskStatus

/**
 * Events the executor and UI can raise against a task.
 */
enum class TaskEvent {
    START, PLAN_DONE, RESULT_FOUND, QA_PASS, QA_FAIL, APPROVED,
    DOWNLOAD_DONE, FAIL, PAUSE, RESUME, SKIP, RETRY, WAIT
}

/**
 * Total state-transition function for [TaskStatus].
 *
 * Guardrails (checked first):
 * - FAIL moves anything to FAILED except DOWNLOADED, which stays DOWNLOADED.
 * - PAUSE moves anything to PAUSED except the terminal states
 *   DOWNLOADED / FAILED / SKIPPED, which stay put.
 * - SKIP moves anything to SKIPPED except DOWNLOADED, which stays put.
 * - RESUME moves PAUSED back to PENDING.
 * - RETRY moves FAILED back to PENDING.
 *
 * Forward pipeline:
 * PENDING --START--> RUNNING --PLAN_DONE--> GENERATING --RESULT_FOUND-->
 * RESULT_FOUND --QA_PASS--> QA --QA_PASS--> APPROVED --APPROVED--> APPROVED
 * --DOWNLOAD_DONE--> DOWNLOADED. QA --QA_FAIL--> QA (retry loop).
 * WAITING --RESULT_FOUND--> RESULT_FOUND, WAITING --WAIT--> WAITING.
 *
 * Any unlisted (status, event) pair returns the status unchanged.
 *
 * Pure Kotlin — safe to run in JVM unit tests (no Android imports).
 */
object TaskTransitions {
    fun next(status: TaskStatus, event: TaskEvent): TaskStatus {
        when (event) {
            TaskEvent.FAIL ->
                return if (status == TaskStatus.DOWNLOADED) status else TaskStatus.FAILED
            TaskEvent.PAUSE ->
                return if (
                    status == TaskStatus.DOWNLOADED ||
                    status == TaskStatus.FAILED ||
                    status == TaskStatus.SKIPPED
                ) status else TaskStatus.PAUSED
            TaskEvent.SKIP ->
                return if (status == TaskStatus.DOWNLOADED) status else TaskStatus.SKIPPED
            TaskEvent.RESUME ->
                return if (status == TaskStatus.PAUSED) TaskStatus.PENDING else status
            TaskEvent.RETRY ->
                return if (status == TaskStatus.FAILED) TaskStatus.PENDING else status
            else -> { /* fall through to the pipeline table */ }
        }
        return when (status to event) {
            TaskStatus.PENDING to TaskEvent.START -> TaskStatus.RUNNING
            TaskStatus.RUNNING to TaskEvent.START -> TaskStatus.RUNNING
            TaskStatus.RUNNING to TaskEvent.PLAN_DONE -> TaskStatus.GENERATING
            TaskStatus.GENERATING to TaskEvent.RESULT_FOUND -> TaskStatus.RESULT_FOUND
            TaskStatus.WAITING to TaskEvent.RESULT_FOUND -> TaskStatus.RESULT_FOUND
            TaskStatus.WAITING to TaskEvent.WAIT -> TaskStatus.WAITING
            TaskStatus.RESULT_FOUND to TaskEvent.QA_PASS -> TaskStatus.QA
            TaskStatus.QA to TaskEvent.QA_PASS -> TaskStatus.APPROVED
            TaskStatus.QA to TaskEvent.QA_FAIL -> TaskStatus.QA
            TaskStatus.QA to TaskEvent.APPROVED -> TaskStatus.APPROVED
            TaskStatus.APPROVED to TaskEvent.DOWNLOAD_DONE -> TaskStatus.DOWNLOADED
            else -> status
        }
    }
}
