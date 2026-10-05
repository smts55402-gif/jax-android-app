package com.jax.automation.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ---------------------------------------------------------------------------
// Shared domain models for JAX. Several of these double as Room entities so
// there is exactly one source of truth for the persistence schema.
// ---------------------------------------------------------------------------

enum class TaskStatus {
    PENDING, PLANNING, RUNNING, WAITING, GENERATING, RESULT_FOUND,
    QA, APPROVED, DOWNLOADING, DOWNLOADED, FAILED, PAUSED, SKIPPED
}

enum class TaskType {
    GENERATE_IMAGE, BATCH_GENERATE, OPEN_URL, CUSTOM
}

enum class ReferenceCategory {
    CHARACTERS, LOCATIONS, OBJECTS, STYLE, WARDROBE, PROPS, CAMERA, COLOR_PALETTE
}

enum class QaStatus { PASS, FAIL }

enum class AutomationMode { MANUAL, SEMI_AUTO, FULL_AUTO }

enum class RecoveryState {
    IDLE, RUNNING, PAUSED, WAITING, FAILED, OPENING_CHROME, OPENING_FLOW,
    SELECTING_PROJECT, SETTING_REFERENCES, ENTERING_PROMPT, GENERATING,
    WAITING_RESULT, QA, DOWNLOADING, COMPLETE, ERROR, WAITING_USER
}

fun newId(): String = java.util.UUID.randomUUID().toString()
fun now(): Long = System.currentTimeMillis()

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey val projectId: String = newId(),
    val name: String,
    val description: String = "",
    val createdAt: Long = now(),
    val updatedAt: Long = now()
)

@Entity(tableName = "scenes", primaryKeys = ["projectId", "sceneId"])
data class Scene(
    val projectId: String,
    val sceneId: String,
    /** 0-based scene number used for S001-style file naming. */
    val sceneIndex: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    /** Verbatim script text — never rewritten by importers. */
    val exactScript: String,
    val visualDescription: String = "",
    val characters: List<String> = emptyList(),
    val locations: List<String> = emptyList(),
    val objects: List<String> = emptyList(),
    val prompt: String = "",
    val references: List<String> = emptyList()
)

@Entity(tableName = "tasks", indices = [Index("projectId"), Index("status")])
data class Task(
    @PrimaryKey val taskId: String = newId(),
    val projectId: String,
    val sceneId: String,
    val type: String = TaskType.GENERATE_IMAGE.name,
    val prompt: String,
    val references: List<String> = emptyList(),
    val status: TaskStatus = TaskStatus.PENDING,
    val attemptCount: Int = 0,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
    val resultPath: String? = null,
    val errorMessage: String? = null,
    val lastKnownScreen: String? = null,
    /** Small JSON blob for adapter-specific extras. */
    val metadata: String = "{}"
)

@Entity(tableName = "reference_items", indices = [Index("category")])
data class ReferenceItem(
    @PrimaryKey val id: String = newId(),
    val category: ReferenceCategory,
    val name: String,
    val description: String = "",
    val imagePaths: List<String> = emptyList(),
    /** JSON object of locked attribute name -> value. */
    val lockedAttributesJson: String = "{}",
    val negativeRules: List<String> = emptyList(),
    val promptBlock: String = "",
    val version: Int = 1,
    val notes: String = "",
    val createdAt: Long = now(),
    val updatedAt: Long = now()
)

@Entity(tableName = "character_locks")
data class CharacterLock(
    @PrimaryKey val characterId: String,
    val headShape: String = "",
    val face: String = "",
    val eyes: String = "",
    val mouth: String = "",
    val hair: String = "",
    val bodyProportions: String = "",
    val arms: String = "",
    val legs: String = "",
    val clothing: String = "",
    val footwear: String = "",
    val accessories: String = "",
    val colorPalette: String = "",
    val lineStyle: String = "",
    val specialIdentifiers: String = ""
)

@Entity(tableName = "style_locks")
data class StyleLock(
    @PrimaryKey val id: String = "global",
    val illustrationStyle: String = "",
    val lineWeight: String = "",
    val colorPalette: String = "",
    val backgroundStyle: String = "",
    val lighting: String = "",
    val rendering: String = "",
    val cameraLanguage: String = "",
    val compositionRules: String = "",
    val aspectRatio: String = "16:9",
    val negativeRules: List<String> = emptyList()
)

@Entity(tableName = "location_locks")
data class LocationLock(
    @PrimaryKey val locationId: String,
    val appearance: String = "",
    val environment: String = "",
    val geography: String = "",
    val lighting: String = "",
    val palette: String = "",
    val keyElements: String = ""
)

@Entity(tableName = "object_locks")
data class ObjectLock(
    @PrimaryKey val objectId: String,
    val shape: String = "",
    val material: String = "",
    val color: String = "",
    val size: String = "",
    val design: String = "",
    val details: String = ""
)

@Entity(tableName = "ai_providers")
data class AIProviderConfig(
    @PrimaryKey val id: String,
    val displayName: String,
    val enabled: Boolean = true,
    val isDefaultPlanner: Boolean = false,
    val isDefaultPrompt: Boolean = false,
    val isDefaultQa: Boolean = false,
    val defaultModel: String = "",
    /** Only used by the "Other" OpenAI-compatible provider. */
    val customBaseUrl: String? = null,
    val updatedAt: Long = now()
)

@Entity(tableName = "automation_logs", indices = [Index("timestamp")])
data class AutomationLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = now(),
    val level: String = "INFO",
    val tag: String,
    val message: String,
    val taskId: String? = null
)

@Entity(tableName = "download_records", indices = [Index("taskId")])
data class DownloadRecord(
    @PrimaryKey val id: String = newId(),
    val taskId: String,
    val projectId: String,
    val sceneId: String,
    val filePath: String,
    val fileName: String,
    val bytes: Long = 0,
    val downloadedAt: Long = now(),
    val verified: Boolean = false
)

@Entity(tableName = "qa_results", indices = [Index("taskId")])
data class QAResult(
    @PrimaryKey val id: String = newId(),
    val taskId: String,
    val status: QaStatus,
    val score: Float = 0f,
    val reasons: List<String> = emptyList(),
    val checkedAt: Long = now()
)

/** Structured error for every automation failure (spec error model). */
data class JaxError(
    val errorCode: String,
    val message: String,
    val taskId: String? = null,
    val timestamp: Long = now(),
    val recoverable: Boolean = true,
    val screenshotPath: String? = null
)

/**
 * Validated structured task produced by the AI command interpreter.
 * Mirrors the spec's JSON example:
 * {"action":"GENERATE_IMAGE","target":"GOOGLE_FLOW","project":"Episode_01",
 *  "scene_id":"017","characters":["AncientYou"],"locations":["Pond_01"],
 *  "objects":["Spear_01"],"style":"PREMIUM_STICKMAN_V2","aspect_ratio":"16:9",
 *  "variations":2,"qa_required":true,"download":true}
 */
data class StructuredTask(
    val action: String,
    val target: String,
    val project: String,
    val sceneId: String,
    val characters: List<String> = emptyList(),
    val locations: List<String> = emptyList(),
    val objects: List<String> = emptyList(),
    val style: String = "",
    val aspectRatio: String = "16:9",
    val variations: Int = 1,
    val qaRequired: Boolean = true,
    val download: Boolean = true
)
