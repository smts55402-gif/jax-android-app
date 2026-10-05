package com.jax.automation.ai

import com.jax.automation.models.StructuredTask

// ---------------------------------------------------------------------------
// Strict validator for planner-produced command JSON. Pure Kotlin —
// no android.*, no org.json — safe for JVM unit tests.
// A missing "target" is a critical gap: it is never defaulted.
// ---------------------------------------------------------------------------

object CommandValidator {

    val ALLOWED_ACTIONS: Set<String> = setOf(
        "GENERATE_IMAGE", "BATCH_GENERATE", "OPEN_APP", "OPEN_URL",
        "RETRY_FAILED", "PAUSE_QUEUE", "RESUME_QUEUE", "STOP_QUEUE",
        "SHOW_FAILED", "CUSTOM"
    )
    val ALLOWED_TARGETS: Set<String> = setOf("GOOGLE_FLOW", "CHROME", "SYSTEM")

    sealed interface ValidationResult {
        data class Valid(val task: StructuredTask) : ValidationResult
        data class Invalid(val reason: String) : ValidationResult
    }

    private val ASPECT_RATIO_REGEX = Regex("\\d+:\\d+")

    fun validate(json: String): ValidationResult {
        val root: Any? = try {
            MiniJson.parse(json)
        } catch (e: IllegalArgumentException) {
            return ValidationResult.Invalid(e.message ?: "Malformed JSON")
        }
        if (root !is Map<*, *>) {
            return ValidationResult.Invalid("Top-level JSON must be an object")
        }
        @Suppress("UNCHECKED_CAST")
        val map = root as Map<String, Any?>

        val action = map["action"] as? String
            ?: return ValidationResult.Invalid("\"action\" is required and must be a string")
        if (action !in ALLOWED_ACTIONS) {
            return ValidationResult.Invalid(
                "\"action\" must be one of ${ALLOWED_ACTIONS.sorted().joinToString(", ")}"
            )
        }
        val target = map["target"] as? String
            ?: return ValidationResult.Invalid("\"target\" is required and must be a string")
        if (target !in ALLOWED_TARGETS) {
            return ValidationResult.Invalid(
                "\"target\" must be one of ${ALLOWED_TARGETS.sorted().joinToString(", ")}"
            )
        }

        if (action == "GENERATE_IMAGE") {
            if ((map["project"] as? String).isNullOrBlank()) {
                return ValidationResult.Invalid("GENERATE_IMAGE requires a non-blank \"project\"")
            }
            if ((map["scene_id"] as? String).isNullOrBlank()) {
                return ValidationResult.Invalid("GENERATE_IMAGE requires a non-blank \"scene_id\"")
            }
        }
        if (action == "BATCH_GENERATE") {
            if ((map["project"] as? String).isNullOrBlank()) {
                return ValidationResult.Invalid("BATCH_GENERATE requires a non-blank \"project\"")
            }
        }

        fun stringList(key: String): Pair<List<String>?, String?> {
            val v = map[key] ?: return emptyList<String>() to null
            if (v !is List<*>) return null to "\"$key\" must be a list of strings"
            if (!v.all { it is String }) return null to "\"$key\" must be a list of strings"
            @Suppress("UNCHECKED_CAST")
            return (v as List<String>) to null
        }
        val (characters, charactersErr) = stringList("characters")
        if (charactersErr != null) return ValidationResult.Invalid(charactersErr)
        val (locations, locationsErr) = stringList("locations")
        if (locationsErr != null) return ValidationResult.Invalid(locationsErr)
        val (objects, objectsErr) = stringList("objects")
        if (objectsErr != null) return ValidationResult.Invalid(objectsErr)

        val style = (map["style"] as? String) ?: ""

        val aspectRatio = when (val v = map["aspect_ratio"]) {
            null -> "16:9"
            is String -> if (ASPECT_RATIO_REGEX.matches(v)) v
                else return ValidationResult.Invalid("\"aspect_ratio\" must match W:H, e.g. \"16:9\"")
            else -> return ValidationResult.Invalid("\"aspect_ratio\" must be a string like \"16:9\"")
        }

        val variations = when (val v = map["variations"]) {
            null -> 1
            is Number -> {
                val i = v.toInt()
                if (i !in 1..8) {
                    return ValidationResult.Invalid("\"variations\" must be between 1 and 8")
                }
                i
            }
            else -> return ValidationResult.Invalid("\"variations\" must be a number between 1 and 8")
        }

        fun boolOrDefault(key: String, default: Boolean): Boolean? {
            val v = map[key] ?: return default
            return if (v is Boolean) v else null
        }
        val qaRequired = boolOrDefault("qa_required", true)
            ?: return ValidationResult.Invalid("\"qa_required\" must be a boolean")
        val download = boolOrDefault("download", true)
            ?: return ValidationResult.Invalid("\"download\" must be a boolean")

        return ValidationResult.Valid(
            StructuredTask(
                action = action,
                target = target,
                project = (map["project"] as? String) ?: "",
                sceneId = (map["scene_id"] as? String) ?: "",
                characters = characters!!,
                locations = locations!!,
                objects = objects!!,
                style = style,
                aspectRatio = aspectRatio,
                variations = variations,
                qaRequired = qaRequired,
                download = download
            )
        )
    }
}
