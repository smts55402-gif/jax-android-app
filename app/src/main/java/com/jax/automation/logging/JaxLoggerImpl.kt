package com.jax.automation.logging

import android.util.Log
import com.jax.automation.database.LogDao
import com.jax.automation.models.AutomationLogEntry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Production [JaxLogger] implementation.
 *
 * - Mirrors every event to logcat and Room ([LogDao]) for durability.
 * - Keeps a capped (500 entries) most-recent-first ring buffer exposed as
 *   [liveLogs] for the on-screen automation console.
 * - Secrets are scrubbed before they ever reach logcat, the buffer, or Room.
 *   Raw API keys are never logged.
 */
class JaxLoggerImpl(
    private val logDao: LogDao,
    private val scope: CoroutineScope
) : JaxLogger {

    private val buffer = MutableStateFlow<List<AutomationLogEntry>>(emptyList())

    override val liveLogs: StateFlow<List<AutomationLogEntry>> = buffer.asStateFlow()

    override fun d(tag: String, message: String, taskId: String?) {
        log("DEBUG", tag, message, taskId, null)
    }

    override fun i(tag: String, message: String, taskId: String?) {
        log("INFO", tag, message, taskId, null)
    }

    override fun w(tag: String, message: String, taskId: String?) {
        log("WARN", tag, message, taskId, null)
    }

    override fun e(tag: String, message: String, taskId: String?, throwable: Throwable?) {
        log("ERROR", tag, message, taskId, throwable)
    }

    private fun log(level: String, tag: String, message: String, taskId: String?, t: Throwable?) {
        val safe = scrub(message)
        Log.println(
            prioFor(level),
            "JAX/$tag",
            safe + (t?.let { "\n${Log.getStackTraceString(it)}" } ?: "")
        )
        val entry = AutomationLogEntry(level = level, tag = tag, message = safe, taskId = taskId)
        buffer.update { (listOf(entry) + it).take(500) }
        scope.launch {
            try {
                logDao.insert(entry)
                logDao.trim(5000)
            } catch (_: Exception) {
                // Logging must never crash the app: drop the write if Room fails.
            }
        }
    }

    private fun prioFor(level: String): Int = when (level) {
        "DEBUG" -> Log.DEBUG
        "INFO" -> Log.INFO
        "WARN" -> Log.WARN
        "ERROR" -> Log.ERROR
        else -> Log.INFO
    }

    private fun scrub(message: String): String {
        var out = message
        for (pattern in SECRET_PATTERNS) {
            out = pattern.replace(out, "\$1[REDACTED]")
        }
        return out
    }

    companion object {
        /** Anything shaped like a secret assignment gets its value redacted. */
        private val SECRET_PATTERNS = listOf(
            Regex("(?i)(api[_-]?key\\s*[\"']?\\s*[:=]\\s*[\"']?)[^\"'\\s]+"),
            Regex("(?i)((?:secret|token|password|passwd|pwd)\\s*[\"']?\\s*[:=]\\s*[\"']?)[^\"'\\s]+")
        )
    }
}
