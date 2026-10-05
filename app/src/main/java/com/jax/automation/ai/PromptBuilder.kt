package com.jax.automation.ai

import com.jax.automation.models.CharacterLock
import com.jax.automation.models.LocationLock
import com.jax.automation.models.ObjectLock
import com.jax.automation.models.Scene
import com.jax.automation.models.StructuredTask
import com.jax.automation.references.LockManager

// ---------------------------------------------------------------------------
// Deterministic prompt assembly: locked reference attributes can never be
// dropped or redesigned. Section order is fixed. A section is included only
// if it has non-blank content, but a non-empty locked attribute is never
// dropped. Pure Kotlin — no android.*, no org.json — safe for JVM unit tests.
// ---------------------------------------------------------------------------

class PromptBuilder(private val locks: LockManager) {

    suspend fun build(task: StructuredTask, scene: Scene?): String {
        val sections = mutableListOf<Pair<String, String>>()
        val style = locks.getStyleLock()

        // GLOBAL STYLE LOCK
        val styleLines = mutableListOf<String>()
        if (style != null) {
            addLine(styleLines, "illustration style", style.illustrationStyle)
            addLine(styleLines, "line weight", style.lineWeight)
            addLine(styleLines, "color palette", style.colorPalette)
            addLine(styleLines, "background style", style.backgroundStyle)
            addLine(styleLines, "rendering", style.rendering)
        } else if (task.style.isNotBlank()) {
            styleLines += "Style: ${task.style}"
        }
        addSection(sections, "GLOBAL STYLE LOCK", styleLines)

        // CHARACTER LOCK
        val characterLines = mutableListOf<String>()
        for (id in task.characters) {
            val lock = locks.getCharacterLock(id)
            if (lock == null) {
                characterLines += "Character reference: $id (no lock defined — keep generic)"
            } else {
                val fields = characterFields(lock)
                if (fields.isNotEmpty()) {
                    characterLines += "Character: $id"
                    characterLines.addAll(fields)
                }
            }
        }
        addSection(sections, "CHARACTER LOCK", characterLines)

        // LOCATION LOCK
        val locationLines = mutableListOf<String>()
        for (id in task.locations) {
            val lock = locks.getLocationLock(id)
            if (lock == null) {
                locationLines += "Location reference: $id (no lock defined — keep generic)"
            } else {
                val fields = locationFields(lock)
                if (fields.isNotEmpty()) {
                    locationLines += "Location: $id"
                    locationLines.addAll(fields)
                }
            }
        }
        addSection(sections, "LOCATION LOCK", locationLines)

        // OBJECT LOCK
        val objectLines = mutableListOf<String>()
        for (id in task.objects) {
            val lock = locks.getObjectLock(id)
            if (lock == null) {
                objectLines += "Object reference: $id (no lock defined — keep generic)"
            } else {
                val fields = objectFields(lock)
                if (fields.isNotEmpty()) {
                    objectLines += "Object: $id"
                    objectLines.addAll(fields)
                }
            }
        }
        addSection(sections, "OBJECT LOCK", objectLines)

        // SCENE ACTION
        val action = scene?.visualDescription?.ifBlank { null }
            ?: scene?.exactScript
            ?: "Scene ${task.sceneId}"
        addSection(
            sections, "SCENE ACTION",
            listOfNotNull(action.takeIf { it.isNotBlank() })
        )

        // CAMERA
        val cameraLines = mutableListOf<String>()
        addLine(cameraLines, "camera language", style?.cameraLanguage ?: "")
        cameraLines += "Aspect ratio ${task.aspectRatio}"
        addSection(sections, "CAMERA", cameraLines)

        // LIGHTING
        addSection(
            sections, "LIGHTING",
            listOfNotNull(style?.lighting?.takeIf { it.isNotBlank() })
        )

        // COMPOSITION
        addSection(
            sections, "COMPOSITION",
            listOfNotNull(style?.compositionRules?.takeIf { it.isNotBlank() })
        )

        // NEGATIVE RULES
        val negatives = style?.negativeRules.orEmpty() +
            listOf("no text", "no watermark", "no signature")
        addSection(sections, "NEGATIVE RULES", listOf(negatives.joinToString(", ")))

        return sections.joinToString("\n\n") { (header, body) -> "## $header\n$body" }
    }

    private fun addLine(lines: MutableList<String>, name: String, value: String) {
        if (value.isNotBlank()) lines += "$name: $value"
    }

    private fun addSection(
        sections: MutableList<Pair<String, String>>,
        header: String,
        lines: List<String>
    ) {
        val body = lines.filter { it.isNotBlank() }.joinToString("\n")
        if (body.isNotBlank()) sections += header to body
    }

    private fun characterFields(lock: CharacterLock): List<String> {
        val lines = mutableListOf<String>()
        addLine(lines, "head shape", lock.headShape)
        addLine(lines, "face", lock.face)
        addLine(lines, "eyes", lock.eyes)
        addLine(lines, "mouth", lock.mouth)
        addLine(lines, "hair", lock.hair)
        addLine(lines, "body proportions", lock.bodyProportions)
        addLine(lines, "arms", lock.arms)
        addLine(lines, "legs", lock.legs)
        addLine(lines, "clothing", lock.clothing)
        addLine(lines, "footwear", lock.footwear)
        addLine(lines, "accessories", lock.accessories)
        addLine(lines, "color palette", lock.colorPalette)
        addLine(lines, "line style", lock.lineStyle)
        addLine(lines, "special identifiers", lock.specialIdentifiers)
        return lines
    }

    private fun locationFields(lock: LocationLock): List<String> {
        val lines = mutableListOf<String>()
        addLine(lines, "appearance", lock.appearance)
        addLine(lines, "environment", lock.environment)
        addLine(lines, "geography", lock.geography)
        addLine(lines, "lighting", lock.lighting)
        addLine(lines, "palette", lock.palette)
        addLine(lines, "key elements", lock.keyElements)
        return lines
    }

    private fun objectFields(lock: ObjectLock): List<String> {
        val lines = mutableListOf<String>()
        addLine(lines, "shape", lock.shape)
        addLine(lines, "material", lock.material)
        addLine(lines, "color", lock.color)
        addLine(lines, "size", lock.size)
        addLine(lines, "design", lock.design)
        addLine(lines, "details", lock.details)
        return lines
    }
}
