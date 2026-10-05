package com.jax.automation.files

import com.jax.automation.models.Scene

/**
 * Parses timestamped narration scripts into [Scene] rows.
 *
 * Pure Kotlin — no Android imports — so it runs in JVM unit tests.
 *
 * Accepted format:
 *   [00:00]
 *   You're standing at the edge of a still pond.
 *
 *   [00:04]
 *   It's about 40,000 years ago.
 *
 * Rules:
 * - A scene's script text is every line after its timestamp until the next
 *   timestamp line (or EOF), trimmed but otherwise VERBATIM — never rewritten
 *   or reordered.
 * - Text before the first timestamp is ignored; scenes with no text are skipped.
 * - Malformed timestamp numbers (e.g. seconds of 99) skip that line, never crash.
 * - sceneIndex is 0-based; sceneId is "%03d" of (index + 1); endTimeMs is the
 *   next scene's start, or start + 30s for the last scene.
 */
object ScriptImporter {

    private val TIMESTAMP = Regex("""^\[(\d{1,2}):(\d{2})(?::(\d{2}))?\]\s*$""")
    private const val LAST_SCENE_MS = 30_000L

    fun parse(projectId: String, text: String): List<Scene> {
        data class RawScene(val startMs: Long, val script: String)

        val starts = mutableListOf<Long>()
        val buffers = mutableListOf<MutableList<String>>()

        for (line in text.lineSequence()) {
            val ms = parseTimestamp(line)
            if (ms != null) {
                // Valid timestamp: start a new scene.
                starts.add(ms)
                buffers.add(mutableListOf())
            } else if (TIMESTAMP.matches(line)) {
                // Looks like a timestamp but has malformed numbers (e.g. [00:99]): skip the line.
            } else if (buffers.isNotEmpty()) {
                // Ordinary script line belonging to the current scene.
                buffers.last().add(line)
            }
            // Lines before the first timestamp are ignored.
        }

        val kept = starts.zip(buffers) { startMs, lines ->
            RawScene(startMs, lines.joinToString("\n").trim())
        }.filter { it.script.isNotEmpty() }

        if (kept.isEmpty()) return emptyList()

        return kept.mapIndexed { index, raw ->
            Scene(
                projectId = projectId,
                sceneIndex = index,
                sceneId = "%03d".format(index + 1),
                startTimeMs = raw.startMs,
                endTimeMs = if (index < kept.lastIndex) kept[index + 1].startMs
                else raw.startMs + LAST_SCENE_MS,
                exactScript = raw.script
            )
        }
    }

    /**
     * Returns the timestamp in ms, or null when the line is not a valid timestamp.
     * Two-part timestamps are MM:SS, three-part are HH:MM:SS.
     */
    private fun parseTimestamp(line: String): Long? {
        val match = TIMESTAMP.matchEntire(line) ?: return null
        val hasHours = match.groupValues[3].isNotEmpty()
        val hours = if (hasHours) match.groupValues[1].toIntOrNull() ?: return null else 0
        val minutes = (if (hasHours) match.groupValues[2] else match.groupValues[1])
            .toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        val seconds = (if (hasHours) match.groupValues[3] else match.groupValues[2])
            .toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        return hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L
    }
}
