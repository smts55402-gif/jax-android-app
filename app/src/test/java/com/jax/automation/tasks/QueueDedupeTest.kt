package com.jax.automation.tasks

import com.jax.automation.models.Task
import com.jax.automation.models.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QueueDedupeTest {

    private fun task(
        status: TaskStatus,
        sceneId: String = "scene-1",
        type: String = "GENERATE_IMAGE",
        taskId: String = "task-${status.name}-$sceneId"
    ): Task = Task(
        taskId = taskId,
        projectId = "proj-1",
        sceneId = sceneId,
        type = type,
        prompt = "prompt",
        status = status
    )

    @Test
    fun pendingDuplicateIsReturned() {
        val existing = task(TaskStatus.PENDING, taskId = "existing-1")
        val candidate = task(TaskStatus.PENDING, taskId = "candidate-1")
        val found = QueueRules.findDuplicate(listOf(existing), candidate)
        assertNotNull(found)
        assertEquals("existing-1", found!!.taskId)
    }

    @Test
    fun failedTaskAllowsRetry() {
        val existing = task(TaskStatus.FAILED, taskId = "existing-1")
        val candidate = task(TaskStatus.PENDING, taskId = "candidate-1")
        assertNull(QueueRules.findDuplicate(listOf(existing), candidate))
    }

    @Test
    fun downloadedTaskIsNeverReenqueued() {
        val existing = task(TaskStatus.DOWNLOADED, taskId = "done-1")
        val candidate = task(TaskStatus.PENDING, taskId = "candidate-1")
        val found = QueueRules.findDuplicate(listOf(existing), candidate)
        assertNotNull(found)
        assertEquals("done-1", found!!.taskId)
    }

    @Test
    fun differentSceneIdIsNotDuplicate() {
        val existing = task(TaskStatus.PENDING, sceneId = "scene-1", taskId = "existing-1")
        val candidate = task(TaskStatus.PENDING, sceneId = "scene-2", taskId = "candidate-1")
        assertNull(QueueRules.findDuplicate(listOf(existing), candidate))
    }

    @Test
    fun differentTypeIsNotDuplicate() {
        val existing = task(TaskStatus.PENDING, type = "GENERATE_IMAGE", taskId = "existing-1")
        val candidate = task(TaskStatus.PENDING, type = "BATCH_GENERATE", taskId = "candidate-1")
        assertNull(QueueRules.findDuplicate(listOf(existing), candidate))
    }
}
