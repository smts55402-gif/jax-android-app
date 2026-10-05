package com.jax.automation.qa

import com.jax.automation.ai.MiniJson
import com.jax.automation.ai.ProviderRegistry
import com.jax.automation.ai.ProviderRole
import com.jax.automation.database.QADao
import com.jax.automation.logging.JaxLogger
import com.jax.automation.models.*
import com.jax.automation.settings.JaxSettings
import com.jax.automation.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Vision QA inspector for AI-generated cartoon scenes.
 *
 * Sends the generated image plus the task requirements to the configured QA
 * vision provider and parses its JSON verdict into a [QAResult], persisted
 * via [QADao]. Parsing is fully defensive: any malformed provider output
 * becomes a FAIL with a descriptive reason.
 */
class VisionQA(
    private val registry: ProviderRegistry,
    private val settings: SettingsRepository,
    private val qaDao: QADao,
    private val logger: JaxLogger
) {

    suspend fun evaluate(task: Task, imageFile: File, requirements: String): QAResult {
        val cfg: JaxSettings = settings.settings.first()

        if (!cfg.qaEnabled) {
            val passResult = QAResult(
                taskId = task.taskId,
                status = QaStatus.PASS,
                score = 1f,
                reasons = listOf("QA disabled in settings — auto-pass")
            )
            qaDao.insert(passResult)
            return passResult
        }

        val resolved = registry.resolve(ProviderRole.QA) ?: run {
            val r = QAResult(
                taskId = task.taskId,
                status = QaStatus.FAIL,
                score = 0f,
                reasons = listOf("No QA vision provider configured — enable a provider and save an API key")
            )
            qaDao.insert(r)
            return r
        }

        if (!imageFile.exists()) {
            val r = QAResult(
                taskId = task.taskId,
                status = QaStatus.FAIL,
                score = 0f,
                reasons = listOf("Generated image file not found: ${imageFile.absolutePath}")
            )
            qaDao.insert(r)
            return r
        }

        val bytes = imageFile.readBytes()
        val mime = when (imageFile.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }

        val system = """You are a strict visual QA inspector for AI-generated cartoon scenes.
Check every frame against the requirements on four axes:
- CHARACTER: identity, head, eyes, mouth, hair, clothing, footwear, body proportions. Every character must match the locked reference design exactly.
- STYLE: style match, bold clean line style, flat colors. No unwanted realism, no gradients, no photorealistic elements.
- SCENE: location, action, props, composition, camera. The scene must depict exactly what the requirements describe.
- QUALITY: extra or missing limbs, distorted anatomy, malformed objects, duplicate characters, watermarks, any visible text or lettering.
CRITICAL: Never claim certainty you do not have. If you cannot determine something from the image, say 'uncertain: <what>' and treat it as a failure reason, not a pass.
Respond with ONLY a JSON object: {"status": "PASS" or "FAIL", "score": 0.0-1.0, "reasons": ["...", ...]}. PASS only if no material defects."""

        val user = "Task requirements:\n$requirements\n\nTask references: ${task.references.joinToString()}\n\nInspect the attached generated image against the requirements and return the QA JSON."

        val raw = try {
            resolved.provider.chatVision(system, user, bytes, mime, resolved.apiKey, resolved.model)
        } catch (e: Exception) {
            val r = QAResult(
                taskId = task.taskId,
                status = QaStatus.FAIL,
                score = 0f,
                reasons = listOf("QA provider error: ${e.message}")
            )
            qaDao.insert(r)
            return r
        }

        val jsonText = try {
            raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1)
        } catch (e: Exception) {
            val r = QAResult(
                taskId = task.taskId,
                status = QaStatus.FAIL,
                score = 0f,
                reasons = listOf("QA returned unparseable output")
            )
            qaDao.insert(r)
            return r
        }

        val parsed = try {
            MiniJson.parse(jsonText) as? Map<*, *>
        } catch (e: Exception) {
            null
        }
        if (parsed == null) {
            val r = QAResult(
                taskId = task.taskId,
                status = QaStatus.FAIL,
                score = 0f,
                reasons = listOf("QA returned unparseable output")
            )
            qaDao.insert(r)
            return r
        }

        val statusStr = (parsed["status"] as? String)?.uppercase() ?: ""
        val status = if (statusStr.contains("FAIL")) {
            QaStatus.FAIL
        } else if (statusStr.contains("PASS")) {
            QaStatus.PASS
        } else {
            QaStatus.FAIL
        }

        val score = ((parsed["score"] as? Double)
            ?: (parsed["score"] as? Number)?.toDouble())?.toFloat()?.coerceIn(0f, 1f)
            ?: if (status == QaStatus.PASS) 1f else 0f

        val defaultReason =
            if (status == QaStatus.PASS) "No issues reported" else "No reasons provided"
        val reasons = (parsed["reasons"] as? List<*>)?.mapNotNull { it as? String }
            ?.ifEmpty { listOf(defaultReason) }
            ?: listOf(defaultReason)

        val uncertain = raw.contains("uncertain", ignoreCase = true) && status == QaStatus.PASS
        val finalStatus = if (uncertain) QaStatus.FAIL else status
        val finalReasons = if (uncertain) reasons + "Model expressed uncertainty" else reasons

        val result = QAResult(
            taskId = task.taskId,
            status = finalStatus,
            score = score,
            reasons = finalReasons
        )
        qaDao.insert(result)
        logger.i(
            "VisionQA",
            "QA $finalStatus score=$score for scene ${task.sceneId}: ${finalReasons.joinToString()}",
            task.taskId
        )
        return result
    }
}
