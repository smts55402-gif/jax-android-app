package com.jax.automation.ai

import com.jax.automation.models.StructuredTask

// ---------------------------------------------------------------------------
// Turns a natural-language command into executable StructuredTasks.
// Local fast paths (queue controls, "scene N"/"scene A to B") avoid the LLM;
// everything else goes through the CommandInterpreter.
// Pure Kotlin — no android.*, no org.json — safe for JVM unit tests.
// ---------------------------------------------------------------------------

data class CommandPreview(
    val command: String,
    val target: String,
    val project: String,
    val scenes: String,
    val references: List<String>,
    val outputCount: Int,
    val qa: Boolean,
    val download: Boolean
)

data class PlanResult(
    val tasks: List<StructuredTask>,
    val needsUserInput: Boolean,
    val question: String?,
    val preview: CommandPreview?
)

class TaskPlanner(private val interpreter: CommandInterpreter) {

    private val rangeRegex =
        Regex("scene\\s+(\\d{1,4})\\s+to\\s+(\\d{1,4})", RegexOption.IGNORE_CASE)
    private val singleSceneRegex =
        Regex("scene\\s+(\\d{1,4})", RegexOption.IGNORE_CASE)

    suspend fun plan(command: String): PlanResult {
        val trimmed = command.trim()
        val lower = trimmed.lowercase()

        // --- Queue-control fast paths (no LLM) ---
        when (lower) {
            "pause" -> return queuePlan(trimmed, "PAUSE_QUEUE")
            "resume" -> return queuePlan(trimmed, "RESUME_QUEUE")
            "stop" -> return queuePlan(trimmed, "STOP_QUEUE")
        }
        if ("retry failed" in lower) return queuePlan(trimmed, "RETRY_FAILED")
        if ("show failed" in lower) return queuePlan(trimmed, "SHOW_FAILED")

        // --- Scene range fast path: "scene 5 to 10" ---
        rangeRegex.find(trimmed)?.let { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            if (a > b || b - a > 500) {
                return PlanResult(
                    emptyList(), true,
                    "That range is too large (max 500 scenes per command).", null
                )
            }
            val tasks = (a..b).map { n ->
                StructuredTask(
                    action = "GENERATE_IMAGE", target = "GOOGLE_FLOW",
                    project = "", sceneId = "%03d".format(n)
                )
            }
            return PlanResult(
                tasks, true,
                "Which project should scenes %03d–%03d be generated into? (e.g. Episode_01)".format(a, b),
                null
            )
        }

        // --- Single scene fast path: "scene 17" ---
        singleSceneRegex.find(trimmed)?.let { m ->
            val id = "%03d".format(m.groupValues[1].toInt())
            val tasks = listOf(
                StructuredTask(
                    action = "GENERATE_IMAGE", target = "GOOGLE_FLOW",
                    project = "", sceneId = id
                )
            )
            return PlanResult(
                tasks, true,
                "Which project should scene $id be generated into? (e.g. Episode_01)",
                null
            )
        }

        // --- LLM path ---
        return when (val result = interpreter.interpret(trimmed)) {
            is CommandValidator.ValidationResult.Valid -> {
                val task = result.task
                PlanResult(listOf(task), false, null, previewOf(trimmed, listOf(task)))
            }
            is CommandValidator.ValidationResult.Invalid ->
                PlanResult(emptyList(), true, result.reason, null)
        }
    }

    private fun queueTask(action: String): StructuredTask = StructuredTask(
        action = action,
        target = "SYSTEM",
        project = "",
        sceneId = "",
        qaRequired = false,
        download = false
    )

    private fun queuePlan(command: String, action: String): PlanResult {
        val task = queueTask(action)
        return PlanResult(listOf(task), false, null, previewOf(command, listOf(task)))
    }

    private fun previewOf(command: String, tasks: List<StructuredTask>): CommandPreview {
        val first = tasks.first()
        val sceneIds = tasks.map { it.sceneId }.filter { it.isNotBlank() }.distinct()
        val scenes = if (sceneIds.isNotEmpty()) sceneIds.joinToString(", ") else first.action
        val references = first.characters + first.locations + first.objects +
            (if (first.style.isNotBlank()) listOf(first.style) else emptyList())
        return CommandPreview(
            command = command,
            target = first.target,
            project = first.project,
            scenes = scenes,
            references = references,
            outputCount = first.variations,
            qa = first.qaRequired,
            download = first.download
        )
    }
}
