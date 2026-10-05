package com.jax.automation.automation

/** Canonical error codes for every automation failure (spec error model). */
object ErrorCodes {
    const val CHROME_NOT_FOUND = "CHROME_NOT_FOUND"
    const val FLOW_NOT_OPEN = "FLOW_NOT_OPEN"
    const val ELEMENT_NOT_FOUND = "ELEMENT_NOT_FOUND"
    const val GENERATION_TIMEOUT = "GENERATION_TIMEOUT"
    const val DOWNLOAD_FAILED = "DOWNLOAD_FAILED"
    const val NETWORK_ERROR = "NETWORK_ERROR"
    const val AI_ERROR = "AI_ERROR"
    const val QA_FAILED = "QA_FAILED"
    const val PERMISSION_MISSING = "PERMISSION_MISSING"
    const val UNKNOWN_SCREEN = "UNKNOWN_SCREEN"
    const val USER_ACTION_REQUIRED = "USER_ACTION_REQUIRED"
    const val PAUSED = "PAUSED"
}
