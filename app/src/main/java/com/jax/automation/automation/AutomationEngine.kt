package com.jax.automation.automation

import com.jax.automation.logging.JaxLogger
import com.jax.automation.models.JaxError
import kotlinx.coroutines.*
import java.io.File

/**
 * Executes a validated plan of [PlannedAction] steps against an
 * [AutomationAdapter], with per-action timeouts, retries and structured
 * error reporting. Real work happens in the adapter; the engine only
 * orchestrates.
 */
data class EngineResult(val ok: Boolean, val error: JaxError?)

class AutomationEngine(
    private val adapter: AutomationAdapter,
    private val logger: JaxLogger,
    private val onAskUser: suspend (message: String) -> Unit = {}
) {

    companion object {
        private const val TAG = "Engine"
        private const val RETRY_DELAY_MS = 500L
    }

    suspend fun execute(taskId: String, plan: List<PlannedAction>): EngineResult {
        logger.i(TAG, "Starting plan with ${plan.size} steps", taskId)
        plan.forEachIndexed { index, action ->
            logger.i(TAG, "Step ${index + 1}/${plan.size}: ${action.action}", taskId)
            val outcome = runAction(taskId, action)
            if (!outcome.ok) return outcome
        }
        logger.i(TAG, "Plan complete: ${plan.size} steps succeeded", taskId)
        return EngineResult(true, null)
    }

    // ------------------------------------------------------------------
    // Single step: validation, terminal actions, then the attempt loop.
    // ------------------------------------------------------------------

    private suspend fun runAction(taskId: String, action: PlannedAction): EngineResult {
        // Terminal actions that never go through the adapter attempt loop.
        when (action.action) {
            AutomationAction.DOWNLOAD_FILE -> return EngineResult(
                false,
                JaxError(
                    ErrorCodes.DOWNLOAD_FAILED,
                    "DOWNLOAD_FILE must be executed by the site adapter (e.g. GoogleFlowAdapter), not the generic engine",
                    taskId,
                    recoverable = true
                )
            )
            AutomationAction.PAUSE -> return EngineResult(
                false,
                JaxError(
                    ErrorCodes.PAUSED,
                    action.message ?: "Paused by plan",
                    taskId,
                    recoverable = true
                )
            )
            AutomationAction.ASK_USER -> {
                val msg = action.message ?: "Input required"
                onAskUser(msg)
                return EngineResult(
                    false,
                    JaxError(ErrorCodes.USER_ACTION_REQUIRED, msg, taskId, recoverable = true)
                )
            }
            else -> { /* fall through */ }
        }

        val validationError = validateRequiredFields(taskId, action)
        if (validationError != null) return EngineResult(false, validationError)

        // UPLOAD_FILE: a missing file is a plan error, not an attempt failure.
        if (action.action == AutomationAction.UPLOAD_FILE) {
            val path = action.text!!
            if (!File(path).exists()) {
                return EngineResult(
                    false,
                    JaxError(
                        ErrorCodes.UNKNOWN_SCREEN,
                        "Upload file not found: $path",
                        taskId,
                        recoverable = false
                    )
                )
            }
        }

        val attempts = 1 + action.retries
        for (attempt in 1..attempts) {
            var code: String? = null
            var failureMessage: String? = null
            try {
                val result = dispatch(taskId, action)
                if (result.ok) return EngineResult(true, null)
                code = result.errorCode
                failureMessage = result.message.ifBlank { "action failed" }
            } catch (t: TimeoutCancellationException) {
                failureMessage = "timed out after ${action.timeoutMs}ms"
            } catch (e: CancellationException) {
                // Cooperative cancellation must propagate; everything else is
                // an attempt failure.
                throw e
            } catch (e: Exception) {
                failureMessage = e.message ?: "unexpected error: ${e::class.java.simpleName}"
            }

            val display = failureMessage ?: "action failed"
            if (attempt < attempts) {
                logger.w(TAG, "Attempt $attempt/$attempts failed: $display — retrying", taskId)
                delay(RETRY_DELAY_MS)
            } else {
                logger.w(TAG, "Attempt $attempt/$attempts failed: $display — no attempts left", taskId)
                return EngineResult(
                    false,
                    JaxError(code ?: ErrorCodes.UNKNOWN_SCREEN, display, taskId, recoverable = true)
                )
            }
        }
        return EngineResult(
            false,
            JaxError(ErrorCodes.UNKNOWN_SCREEN, "action failed", taskId, recoverable = true)
        )
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    private fun validateRequiredFields(taskId: String, action: PlannedAction): JaxError? {
        fun missing(field: String): JaxError = JaxError(
            ErrorCodes.UNKNOWN_SCREEN,
            "PlannedAction ${action.action} missing required field '$field'",
            taskId,
            recoverable = false
        )
        return when (action.action) {
            AutomationAction.OPEN_APP ->
                if (action.packageName.isNullOrBlank()) missing("packageName") else null
            AutomationAction.OPEN_URL ->
                if (action.url.isNullOrBlank()) missing("url") else null
            AutomationAction.TYPE -> when {
                action.selector == null -> missing("selector")
                action.text == null -> missing("text")
                else -> null
            }
            AutomationAction.WAIT_FOR_TEXT ->
                if (action.text == null) missing("text") else null
            AutomationAction.CLICK,
            AutomationAction.CLEAR,
            AutomationAction.LONG_PRESS,
            AutomationAction.SCROLL,
            AutomationAction.WAIT_FOR_ELEMENT,
            AutomationAction.UPLOAD_FILE ->
                if (action.selector == null) missing("selector") else null
            else -> null
        }
    }

    // ------------------------------------------------------------------
    // Adapter dispatch. Non-wait actions run inside withTimeout; WAIT,
    // WAIT_FOR_ELEMENT and WAIT_FOR_TEXT rely on the adapter's own
    // timeout/delay (WAIT uses a plain delay here).
    // ------------------------------------------------------------------

    private suspend fun dispatch(taskId: String, action: PlannedAction): ActionResult {
        return when (action.action) {
            AutomationAction.OPEN_APP ->
                withTimeout(action.timeoutMs) { adapter.openApp(action.packageName!!) }
            AutomationAction.OPEN_URL ->
                withTimeout(action.timeoutMs) { adapter.openUrl(action.url!!) }
            AutomationAction.CLICK ->
                withTimeout(action.timeoutMs) { adapter.click(action.selector!!) }
            AutomationAction.TYPE ->
                withTimeout(action.timeoutMs) { adapter.typeText(action.selector!!, action.text!!) }
            AutomationAction.CLEAR ->
                withTimeout(action.timeoutMs) { adapter.clearText(action.selector!!) }
            AutomationAction.SCROLL ->
                withTimeout(action.timeoutMs) {
                    adapter.scroll(action.selector!!, action.direction ?: ScrollDirection.DOWN)
                }
            AutomationAction.SWIPE ->
                withTimeout(action.timeoutMs) {
                    adapter.swipe(action.x1, action.y1, action.x2, action.y2, action.durationMs)
                }
            AutomationAction.LONG_PRESS ->
                withTimeout(action.timeoutMs) { adapter.longPress(action.selector!!) }
            AutomationAction.BACK ->
                withTimeout(action.timeoutMs) { adapter.back() }
            AutomationAction.WAIT -> {
                logger.i(
                    TAG,
                    "fixed wait ${action.timeoutMs / 1000}s — prefer WAIT_FOR_* conditions",
                    taskId
                )
                delay(action.timeoutMs)
                ActionResult(ok = true)
            }
            AutomationAction.WAIT_FOR_ELEMENT ->
                adapter.waitForElement(action.selector!!, action.timeoutMs)
            AutomationAction.WAIT_FOR_TEXT ->
                adapter.waitForText(action.text!!, action.timeoutMs)
            AutomationAction.SCREENSHOT ->
                withTimeout(action.timeoutMs) {
                    val bytes = adapter.screenshot()
                    if (bytes == null) {
                        logger.i(TAG, "screenshot unavailable", taskId)
                    } else {
                        logger.i(TAG, "screenshot: ${bytes.size} bytes", taskId)
                    }
                    ActionResult(ok = true)
                }
            AutomationAction.UPLOAD_FILE ->
                withTimeout(action.timeoutMs) {
                    adapter.uploadFile(action.selector!!, File(action.text!!))
                }
            AutomationAction.READ_SCREEN ->
                withTimeout(action.timeoutMs) {
                    val snapshot = adapter.readScreen()
                    logger.i(TAG, "screen: ${snapshot.texts.size} texts", taskId)
                    ActionResult(ok = true)
                }
            // Terminal actions are handled in runAction and never reach here.
            AutomationAction.DOWNLOAD_FILE,
            AutomationAction.PAUSE,
            AutomationAction.ASK_USER ->
                ActionResult(ok = false, message = "unreachable: handled by runAction")
        }
    }
}
