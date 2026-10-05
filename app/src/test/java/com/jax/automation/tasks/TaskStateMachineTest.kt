package com.jax.automation.tasks

import com.jax.automation.models.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskStateMachineTest {

    @Test
    fun pendingStartBecomesRunning() {
        assertEquals(
            TaskStatus.RUNNING,
            TaskTransitions.next(TaskStatus.PENDING, TaskEvent.START)
        )
    }

    @Test
    fun runningPlanDoneBecomesGenerating() {
        assertEquals(
            TaskStatus.GENERATING,
            TaskTransitions.next(TaskStatus.RUNNING, TaskEvent.PLAN_DONE)
        )
    }

    @Test
    fun generatingResultFoundStaysResultFound() {
        assertEquals(
            TaskStatus.RESULT_FOUND,
            TaskTransitions.next(TaskStatus.GENERATING, TaskEvent.RESULT_FOUND)
        )
    }

    @Test
    fun resultFoundQaPassBecomesQa() {
        assertEquals(
            TaskStatus.QA,
            TaskTransitions.next(TaskStatus.RESULT_FOUND, TaskEvent.QA_PASS)
        )
    }

    @Test
    fun qaApprovedBecomesApproved() {
        assertEquals(
            TaskStatus.APPROVED,
            TaskTransitions.next(TaskStatus.QA, TaskEvent.APPROVED)
        )
    }

    @Test
    fun approvedDownloadDoneBecomesDownloaded() {
        assertEquals(
            TaskStatus.DOWNLOADED,
            TaskTransitions.next(TaskStatus.APPROVED, TaskEvent.DOWNLOAD_DONE)
        )
    }

    @Test
    fun runningFailBecomesFailed() {
        assertEquals(
            TaskStatus.FAILED,
            TaskTransitions.next(TaskStatus.RUNNING, TaskEvent.FAIL)
        )
    }

    @Test
    fun qaQaFailStaysInQa() {
        assertEquals(
            TaskStatus.QA,
            TaskTransitions.next(TaskStatus.QA, TaskEvent.QA_FAIL)
        )
    }

    @Test
    fun failedRetryBecomesPending() {
        assertEquals(
            TaskStatus.PENDING,
            TaskTransitions.next(TaskStatus.FAILED, TaskEvent.RETRY)
        )
    }

    @Test
    fun runningPauseBecomesPaused() {
        assertEquals(
            TaskStatus.PAUSED,
            TaskTransitions.next(TaskStatus.RUNNING, TaskEvent.PAUSE)
        )
    }

    @Test
    fun pausedResumeBecomesPending() {
        assertEquals(
            TaskStatus.PENDING,
            TaskTransitions.next(TaskStatus.PAUSED, TaskEvent.RESUME)
        )
    }

    @Test
    fun downloadedFailStaysDownloadedTerminalWins() {
        assertEquals(
            TaskStatus.DOWNLOADED,
            TaskTransitions.next(TaskStatus.DOWNLOADED, TaskEvent.FAIL)
        )
    }

    @Test
    fun runningSkipBecomesSkipped() {
        assertEquals(
            TaskStatus.SKIPPED,
            TaskTransitions.next(TaskStatus.RUNNING, TaskEvent.SKIP)
        )
    }
}
