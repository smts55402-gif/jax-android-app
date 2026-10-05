package com.jax.automation.tasks

import com.jax.automation.models.QAResult
import com.jax.automation.models.QaStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryEngineTest {

    private val engine = RetryEngine()

    private fun failResult(vararg reasons: String): QAResult =
        QAResult(taskId = "task-1", status = QaStatus.FAIL, reasons = reasons.toList())

    @Test
    fun retryWhileAttemptsRemainIncludesCorrection() {
        val outcome = engine.decide(failResult("hair drift"), 1, 3)
        assertEquals(RetryDecision.RETRY, outcome.decision)
        assertNotNull(outcome.correctionInstruction)
        assertTrue(outcome.correctionInstruction!!.contains("hair drift"))
    }

    @Test
    fun pauseTaskWhenAttemptBudgetExhausted() {
        val outcome = engine.decide(failResult("hair drift"), 3, 3)
        assertEquals(RetryDecision.PAUSE_TASK, outcome.decision)
    }

    @Test
    fun pauseTaskWhenAttemptBudgetExceeded() {
        val outcome = engine.decide(failResult("hair drift"), 4, 3)
        assertEquals(RetryDecision.PAUSE_TASK, outcome.decision)
    }

    @Test
    fun correctionInstructionMentionsReasonsAndLockedAttributes() {
        val instruction = engine.buildCorrectionInstruction(listOf("hair drift", "missing spear"))
        assertTrue(instruction.contains("hair drift"))
        assertTrue(instruction.contains("missing spear"))
        assertTrue(instruction.contains("locked"))
    }
}
