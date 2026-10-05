package com.jax.automation.automation

import java.io.File

/**
 * Semantic selector for UI elements. Priority order when resolving:
 * viewId > contentDescription > text > textContains. Coordinates are only
 * ever a last resort and are NOT part of this selector model.
 */
data class Selector(
    val viewId: String? = null,
    val contentDescription: String? = null,
    val text: String? = null,
    val textContains: String? = null
)

enum class ScrollDirection { UP, DOWN, LEFT, RIGHT }

data class ActionResult(
    val ok: Boolean,
    val message: String = "",
    val errorCode: String? = null
)

data class ScreenSnapshot(
    val texts: List<String>,
    val timestamp: Long
)

/**
 * The single automation contract every engine, adapter and test speaks.
 * Implementations: [com.jax.automation.accessibility.AccessibilityAutomationAdapter]
 * (real, on-device) and a JVM MockAutomationAdapter in the test source set.
 */
interface AutomationAdapter {
    suspend fun openApp(packageName: String): ActionResult
    suspend fun openUrl(url: String): ActionResult
    suspend fun click(sel: Selector): ActionResult
    suspend fun typeText(sel: Selector, text: String): ActionResult
    suspend fun clearText(sel: Selector): ActionResult
    suspend fun scroll(sel: Selector, direction: ScrollDirection): ActionResult
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): ActionResult
    suspend fun longPress(sel: Selector): ActionResult
    suspend fun back(): ActionResult
    suspend fun home(): ActionResult
    suspend fun waitForText(text: String, timeoutMs: Long): ActionResult
    suspend fun waitForElement(sel: Selector, timeoutMs: Long): ActionResult
    suspend fun waitForElementGone(sel: Selector, timeoutMs: Long): ActionResult
    /** PNG bytes, or null when screenshots are unavailable on this device. */
    suspend fun screenshot(): ByteArray?
    suspend fun readScreen(): ScreenSnapshot
    /**
     * Honest limitation: tapping a file field can open the system picker,
     * but selecting a file programmatically is not reliably automatable.
     * Implementations must return a non-ok result with USER_ACTION_REQUIRED
     * when they cannot complete the upload themselves.
     */
    suspend fun uploadFile(sel: Selector, file: File): ActionResult
}
