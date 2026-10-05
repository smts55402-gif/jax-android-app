package com.jax.automation.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jax.automation.models.AutomationMode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "jax_settings")

private val PLANNER_PROVIDER_ID = stringPreferencesKey("planner_provider_id")
private val PROMPT_PROVIDER_ID = stringPreferencesKey("prompt_provider_id")
private val QA_PROVIDER_ID = stringPreferencesKey("qa_provider_id")
private val PLANNER_MODEL = stringPreferencesKey("planner_model")
private val PROMPT_MODEL = stringPreferencesKey("prompt_model")
private val QA_MODEL = stringPreferencesKey("qa_model")
private val MAX_RETRY_ATTEMPTS = intPreferencesKey("max_retry_attempts")
private val DEFAULT_OUTPUT_COUNT = intPreferencesKey("default_output_count")
private val DEFAULT_ASPECT_RATIO = stringPreferencesKey("default_aspect_ratio")
private val AUTOMATION_MODE = stringPreferencesKey("automation_mode")
private val DOWNLOAD_FOLDER = stringPreferencesKey("download_folder")
private val SCREENSHOT_LOGGING = booleanPreferencesKey("screenshot_logging")
private val QA_ENABLED = booleanPreferencesKey("qa_enabled")
private val AUTO_RESUME = booleanPreferencesKey("auto_resume")
private val DEFAULT_TIMEOUT_MS = longPreferencesKey("default_timeout_ms")
private val CURRENT_PROJECT_ID = stringPreferencesKey("current_project_id")

/** Single source of default values, used whenever a key is absent. */
private val DEFAULTS = JaxSettings()

/**
 * Persists [JaxSettings] in DataStore Preferences ("jax_settings").
 * API keys are NOT stored here — they live in [com.jax.automation.security.SecureStorage].
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    /** Live settings stream; falls back to defaults on read errors (IOException). */
    val settings: Flow<JaxSettings> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs -> toSettings(prefs) }

    /** Applies [transform] to the current settings and persists the result. */
    suspend fun update(transform: (JaxSettings) -> JaxSettings) {
        dataStore.edit { prefs ->
            val next = transform(toSettings(prefs))
            prefs[PLANNER_PROVIDER_ID] = next.plannerProviderId
            prefs[PROMPT_PROVIDER_ID] = next.promptProviderId
            prefs[QA_PROVIDER_ID] = next.qaProviderId
            prefs[PLANNER_MODEL] = next.plannerModel
            prefs[PROMPT_MODEL] = next.promptModel
            prefs[QA_MODEL] = next.qaModel
            prefs[MAX_RETRY_ATTEMPTS] = next.maxRetryAttempts
            prefs[DEFAULT_OUTPUT_COUNT] = next.defaultOutputCount
            prefs[DEFAULT_ASPECT_RATIO] = next.defaultAspectRatio
            prefs[AUTOMATION_MODE] = next.automationMode.name
            prefs[DOWNLOAD_FOLDER] = next.downloadFolder
            prefs[SCREENSHOT_LOGGING] = next.screenshotLogging
            prefs[QA_ENABLED] = next.qaEnabled
            prefs[AUTO_RESUME] = next.autoResume
            prefs[DEFAULT_TIMEOUT_MS] = next.defaultTimeoutMs
            prefs[CURRENT_PROJECT_ID] = next.currentProjectId
        }
    }

    companion object {
        fun create(context: Context): SettingsRepository =
            SettingsRepository(context.applicationContext.settingsDataStore)
    }
}

private fun toSettings(prefs: Preferences): JaxSettings = JaxSettings(
    plannerProviderId = prefs[PLANNER_PROVIDER_ID] ?: DEFAULTS.plannerProviderId,
    promptProviderId = prefs[PROMPT_PROVIDER_ID] ?: DEFAULTS.promptProviderId,
    qaProviderId = prefs[QA_PROVIDER_ID] ?: DEFAULTS.qaProviderId,
    plannerModel = prefs[PLANNER_MODEL] ?: DEFAULTS.plannerModel,
    promptModel = prefs[PROMPT_MODEL] ?: DEFAULTS.promptModel,
    qaModel = prefs[QA_MODEL] ?: DEFAULTS.qaModel,
    maxRetryAttempts = prefs[MAX_RETRY_ATTEMPTS] ?: DEFAULTS.maxRetryAttempts,
    defaultOutputCount = prefs[DEFAULT_OUTPUT_COUNT] ?: DEFAULTS.defaultOutputCount,
    defaultAspectRatio = prefs[DEFAULT_ASPECT_RATIO] ?: DEFAULTS.defaultAspectRatio,
    automationMode = prefs[AUTOMATION_MODE]
        ?.let { runCatching { AutomationMode.valueOf(it) }.getOrNull() }
        ?: DEFAULTS.automationMode,
    downloadFolder = prefs[DOWNLOAD_FOLDER] ?: DEFAULTS.downloadFolder,
    screenshotLogging = prefs[SCREENSHOT_LOGGING] ?: DEFAULTS.screenshotLogging,
    qaEnabled = prefs[QA_ENABLED] ?: DEFAULTS.qaEnabled,
    autoResume = prefs[AUTO_RESUME] ?: DEFAULTS.autoResume,
    defaultTimeoutMs = prefs[DEFAULT_TIMEOUT_MS] ?: DEFAULTS.defaultTimeoutMs,
    currentProjectId = prefs[CURRENT_PROJECT_ID] ?: DEFAULTS.currentProjectId
)
