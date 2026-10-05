package com.jax.automation.automation

/**
 * Strict whitelist of automation actions. The AI planner may ONLY produce
 * these actions — never arbitrary code, never anything outside this enum.
 */
enum class AutomationAction {
    OPEN_APP,
    OPEN_URL,
    CLICK,
    TYPE,
    CLEAR,
    SCROLL,
    SWIPE,
    LONG_PRESS,
    BACK,
    WAIT,
    WAIT_FOR_ELEMENT,
    WAIT_FOR_TEXT,
    SCREENSHOT,
    UPLOAD_FILE,
    DOWNLOAD_FILE,
    READ_SCREEN,
    PAUSE,
    ASK_USER
}

/**
 * One validated step of an automation plan.
 *
 * Field usage by action:
 * - OPEN_APP: packageName
 * - OPEN_URL: url
 * - CLICK / CLEAR / LONG_PRESS / SCROLL / UPLOAD_FILE: selector (+ direction for SCROLL)
 * - TYPE: selector + text
 * - SWIPE: x1/y1/x2/y2/durationMs
 * - WAIT: timeoutMs (fixed delay; prefer WAIT_FOR_* with conditions)
 * - WAIT_FOR_ELEMENT: selector + timeoutMs
 * - WAIT_FOR_TEXT: text + timeoutMs
 * - SCREENSHOT / READ_SCREEN / BACK: no fields
 * - DOWNLOAD_FILE: url + text (destination file name hint)
 * - PAUSE / ASK_USER: message
 */
data class PlannedAction(
    val action: AutomationAction,
    val selector: Selector? = null,
    val text: String? = null,
    val url: String? = null,
    val packageName: String? = null,
    val direction: ScrollDirection? = null,
    val x1: Int = 0,
    val y1: Int = 0,
    val x2: Int = 0,
    val y2: Int = 0,
    val durationMs: Int = 500,
    val timeoutMs: Long = 10_000L,
    val retries: Int = 2,
    val message: String? = null
)
