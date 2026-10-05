package com.jax.automation.logging

import com.jax.automation.models.AutomationLogEntry
import kotlinx.coroutines.flow.StateFlow

/**
 * Logging contract. The implementation writes to Room (AutomationLogEntry)
 * and logcat, and exposes a live feed for the on-screen automation console.
 * Implementations must NEVER log API keys or other secrets.
 */
interface JaxLogger {
    fun d(tag: String, message: String, taskId: String? = null)
    fun i(tag: String, message: String, taskId: String? = null)
    fun w(tag: String, message: String, taskId: String? = null)
    fun e(tag: String, message: String, taskId: String? = null, throwable: Throwable? = null)

    /** Most-recent-first ring buffer (capped) for the live log console. */
    val liveLogs: StateFlow<List<AutomationLogEntry>>
}
