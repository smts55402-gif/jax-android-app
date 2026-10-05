package com.jax.automation.settings

import com.jax.automation.models.AutomationMode

/**
 * All user-configurable JAX settings. Persisted via [SettingsRepository]
 * (DataStore Preferences); [DEFAULTS] is the single source of default values.
 */
data class JaxSettings(
    val plannerProviderId: String = "gemini",
    val promptProviderId: String = "gemini",
    val qaProviderId: String = "gemini",
    val plannerModel: String = "gemini-2.0-flash",
    val promptModel: String = "gemini-2.0-flash",
    val qaModel: String = "gemini-2.0-flash",
    val maxRetryAttempts: Int = 3,
    val defaultOutputCount: Int = 2,
    val defaultAspectRatio: String = "16:9",
    val automationMode: AutomationMode = AutomationMode.SEMI_AUTO,
    val downloadFolder: String = "",
    val screenshotLogging: Boolean = true,
    val qaEnabled: Boolean = true,
    val autoResume: Boolean = true,
    val defaultTimeoutMs: Long = 30_000L,
    val currentProjectId: String = ""
)
