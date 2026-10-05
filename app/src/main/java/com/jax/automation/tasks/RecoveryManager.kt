package com.jax.automation.tasks

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/**
 * Point-in-time snapshot of executor progress, persisted so an app restart
 * (or process death) can resume honestly instead of assuming success.
 */
data class RecoverySnapshot(
    val projectId: String?,
    val lastCompletedTaskId: String?,
    val currentTaskId: String?,
    val currentStep: String?,
    val attemptCount: Int,
    val lastKnownScreen: String?
)

private val Context.recoveryDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "jax_recovery")

/**
 * Persists [RecoverySnapshot] to a private DataStore file ("jax_recovery").
 * Never assumes a task succeeded: on restore, interrupted tasks are marked
 * PAUSED for the user to review.
 */
class RecoveryManager(private val dataStore: DataStore<Preferences>) {

    suspend fun save(snapshot: RecoverySnapshot) {
        dataStore.edit { prefs ->
            prefs[KEY_PROJECT] = snapshot.projectId ?: ""
            prefs[KEY_LAST_COMPLETED] = snapshot.lastCompletedTaskId ?: ""
            prefs[KEY_CURRENT_TASK] = snapshot.currentTaskId ?: ""
            prefs[KEY_STEP] = snapshot.currentStep ?: ""
            prefs[KEY_ATTEMPTS] = snapshot.attemptCount
            prefs[KEY_SCREEN] = snapshot.lastKnownScreen ?: ""
        }
    }

    /** Returns null when no snapshot was ever saved. */
    suspend fun load(): RecoverySnapshot? {
        val prefs = dataStore.data.first()
        val projectId = prefs[KEY_PROJECT].orEmpty()
        val currentTaskId = prefs[KEY_CURRENT_TASK].orEmpty()
        if (projectId.isEmpty() && currentTaskId.isEmpty()) return null
        return RecoverySnapshot(
            projectId = projectId.ifEmpty { null },
            lastCompletedTaskId = prefs[KEY_LAST_COMPLETED].orEmpty().ifEmpty { null },
            currentTaskId = currentTaskId.ifEmpty { null },
            currentStep = prefs[KEY_STEP].orEmpty().ifEmpty { null },
            attemptCount = prefs[KEY_ATTEMPTS] ?: 0,
            lastKnownScreen = prefs[KEY_SCREEN].orEmpty().ifEmpty { null }
        )
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    companion object {
        private val KEY_PROJECT = stringPreferencesKey("recovery_project")
        private val KEY_LAST_COMPLETED = stringPreferencesKey("recovery_last_completed")
        private val KEY_CURRENT_TASK = stringPreferencesKey("recovery_current_task")
        private val KEY_STEP = stringPreferencesKey("recovery_step")
        private val KEY_ATTEMPTS = intPreferencesKey("recovery_attempts")
        private val KEY_SCREEN = stringPreferencesKey("recovery_screen")

        fun create(context: Context): RecoveryManager =
            RecoveryManager(context.recoveryDataStore)
    }
}
