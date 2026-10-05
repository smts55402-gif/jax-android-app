package com.jax.automation.tasks

import com.jax.automation.models.QAResult

/**
 * What the executor should do after a QA failure.
 */
enum class RetryDecision { RETRY, PAUSE_TASK }

/**
 * @param decision what to do next.
 * @param correctionInstruction appended to the prompt when [decision] is
 * RETRY; null when the task is being parked for the user.
 */
data class RetryOutcome(
    val decision: RetryDecision,
    val correctionInstruction: String?
)

/**
 * Decides whether a QA-failed generation should be retried automatically
 * (with a corrective prompt amendment) or parked as PAUSED for the user.
 *
 * Pure Kotlin — safe to run in JVM unit tests (no Android imports).
 */
class RetryEngine {
    /**
     * Only called for QA FAIL results. Retries while [attemptCount] is below
     * [maxAttempts]; once the budget is exhausted the task is parked so the
     * user can fix the prompt or skip the scene.
     */
    fun decide(qaResult: QAResult, attemptCount: Int, maxAttempts: Int = 3): RetryOutcome {
        return if (attemptCount < maxAttempts) {
            RetryOutcome(RetryDecision.RETRY, buildCorrectionInstruction(qaResult.reasons))
        } else {
            RetryOutcome(RetryDecision.PAUSE_TASK, null)
        }
    }

    /**
     * Builds the prompt amendment that tells the generator exactly what to
     * fix while keeping every locked attribute unchanged.
     */
    fun buildCorrectionInstruction(reasons: List<String>): String {
        val bullets = reasons.joinToString(separator = "\n") { "- $it" }
        return "The previous generation failed QA for these reasons:\n" +
            bullets +
            "\nRegenerate the image correcting exactly these issues while keeping " +
            "every locked character, style, location and object attribute unchanged."
    }
}
