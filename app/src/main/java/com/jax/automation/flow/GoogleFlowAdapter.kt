package com.jax.automation.flow

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.jax.automation.automation.ActionResult
import com.jax.automation.automation.AutomationAdapter
import com.jax.automation.automation.ErrorCodes
import com.jax.automation.automation.Selector
import com.jax.automation.logging.JaxLogger
import com.jax.automation.models.JaxError
import com.jax.automation.models.Task
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File

/**
 * Google Flow site adapter. Treats Flow (https://labs.google/fx) as a
 * UI-driven web app: every interaction goes through [AutomationAdapter]
 * selectors, trying a prioritized list of Selectors
 * (viewId -> contentDescription -> text -> textContains).
 *
 * Rules honored here:
 * - NEVER click random coordinates; only selector-based actions.
 * - Required steps that cannot find their element: capture a screenshot,
 *   log a warning naming the attempted selectors, and fail with
 *   ELEMENT_NOT_FOUND (the Flow UI may have changed since this was written).
 * - Optional steps (project, character, aspect ratio, output count):
 *   warn and CONTINUE on a miss — the setting may already be correct.
 * - CAPTCHA / login / rate-limit challenges are NEVER bypassed. The
 *   [checkForSecurityChallenge] gate runs after [openFlow] and again before
 *   [clickGenerate]; on a hit we pause and hand control to the user.
 */
data class FlowGenResult(
    val ok: Boolean,
    val imageFile: File?,
    val error: JaxError?
)

class GoogleFlowAdapter(
    private val context: Context,
    private val adapter: AutomationAdapter,
    private val logger: JaxLogger,
    private val onNeedUser: suspend (message: String) -> Unit
) {

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Best-effort runner for OPTIONAL steps. Tries each selector in order;
     * clicks the first one that appears. On a total miss, warns and returns
     * ok (the setting may already be correct).
     */
    private suspend fun tryOptional(step: String, candidates: List<Selector>): ActionResult {
        for (sel in candidates) {
            try {
                if (!adapter.waitForElement(sel, 5000).ok) continue
                val r = adapter.click(sel)
                if (r.ok) {
                    logger.i(TAG, "Optional step '$step' applied via $sel")
                    return ActionResult(true, "$step applied")
                }
                logger.w(TAG, "Optional step '$step': element found but click failed (${r.message})")
            } catch (t: Throwable) {
                logger.w(TAG, "Optional step '$step' attempt failed: ${t.message}")
            }
        }
        logger.w(TAG, "Optional step '$step' not found in Flow UI — continuing (may already be correct)")
        return ActionResult(true, "$step skipped (not found)")
    }

    private suspend fun screenshotOrNull(): ByteArray? {
        return try {
            adapter.screenshot()
        } catch (t: Throwable) {
            logger.w(TAG, "Screenshot capture failed: ${t.message}")
            null
        }
    }

    private suspend fun saveDebugScreenshot(step: String, workDir: File): String? {
        return try {
            val bytes = screenshotOrNull() ?: return null
            workDir.mkdirs()
            val f = File(workDir, "debug_${step}_${System.currentTimeMillis()}.png")
            f.writeBytes(bytes)
            logger.w(TAG, "Debug screenshot for step '$step' saved to ${f.absolutePath}")
            f.absolutePath
        } catch (t: Throwable) {
            logger.w(TAG, "Could not save debug screenshot for step '$step': ${t.message}")
            null
        }
    }

    private suspend fun fail(step: String, result: ActionResult, taskId: String, workDir: File): FlowGenResult {
        val screenshotPath = saveDebugScreenshot(step, workDir)
        val code = result.errorCode ?: ErrorCodes.UNKNOWN_SCREEN
        val message = result.message.ifEmpty { "Step '$step' failed" }
        logger.e(TAG, "Step '$step' failed: $message (code=$code)", taskId)
        return FlowGenResult(false, null, JaxError(code, message, taskId, screenshotPath = screenshotPath))
    }

    /**
     * Security gate. Looks for sign-in / CAPTCHA / unusual-traffic wording on
     * screen. On a hit: logs, notifies the user via [onNeedUser], returns true.
     * Automation is NEVER attempted past a challenge — the user resolves it
     * manually and resumes.
     */
    private suspend fun checkForSecurityChallenge(): Boolean {
        val texts = try {
            adapter.readScreen().texts
        } catch (t: Throwable) {
            emptyList<String>()
        }
        val joined = texts.joinToString(" ").lowercase()
        val keywords = listOf(
            "captcha",
            "verify you are human",
            "verify you're human",
            "unusual traffic",
            "confirm you're not a robot",
            "sign in",
            "choose an account",
            "2-step verification",
            "enter your password"
        )
        val hit = keywords.any { joined.contains(it) }
        if (hit) {
            logger.e(TAG, "Security/auth challenge detected — pausing automation")
            onNeedUser(
                "Manual action required: Google Flow is showing a sign-in, verification or " +
                    "CAPTCHA challenge. Please complete it in Chrome, then press Resume in JAX."
            )
            return true
        }
        return false
    }

    // ------------------------------------------------------------------
    // Public Flow steps
    // ------------------------------------------------------------------

    suspend fun openFlow(): ActionResult {
        logger.i(TAG, "Opening Google Flow: $FLOW_URL")
        val r = adapter.openUrl(FLOW_URL)
        if (!r.ok) {
            logger.e(TAG, "Could not open Flow URL: ${r.message}")
            return r.copy(
                errorCode = ErrorCodes.FLOW_NOT_OPEN,
                message = "Google Flow could not be opened (${r.message}). Check Chrome and your connection."
            )
        }
        // Poll up to 20s for any screen content (page loaded).
        val deadline = System.currentTimeMillis() + 20_000
        var loaded = false
        while (System.currentTimeMillis() < deadline) {
            val texts = try {
                adapter.readScreen().texts
            } catch (t: Throwable) {
                emptyList<String>()
            }
            if (texts.isNotEmpty()) {
                loaded = true
                break
            }
            delay(1000)
        }
        if (!loaded) {
            logger.e(TAG, "Flow page produced no screen content within 20s")
            return ActionResult(
                false,
                "Google Flow did not load (no screen content after 20s).",
                ErrorCodes.FLOW_NOT_OPEN
            )
        }
        if (checkForSecurityChallenge()) {
            return ActionResult(false, "Manual action required.", ErrorCodes.USER_ACTION_REQUIRED)
        }
        logger.i(TAG, "Google Flow is open")
        return ActionResult(true, "Google Flow opened")
    }

    suspend fun selectProject(projectName: String): ActionResult {
        if (projectName.isBlank()) {
            logger.i(TAG, "No project requested — skipping project selection")
            return ActionResult(true, "no project requested")
        }
        // Optional: a miss means the project list UI changed or the project is
        // already open — continue with a warning.
        return tryOptional(
            "selectProject($projectName)",
            listOf(Selector(text = projectName), Selector(textContains = projectName))
        )
    }

    suspend fun openGenerationInterface(): ActionResult {
        // Optional: if we cannot find an explicit entry point, assume we are
        // already in the generation UI and continue with a warning.
        // Note: Selector has exact contentDescription only (no "contains"
        // variant), so we try exact plus textContains variants.
        return tryOptional(
            "openGenerationInterface",
            listOf(
                Selector(textContains = "Create"),
                Selector(textContains = "New"),
                Selector(contentDescription = "Create")
            )
        )
    }

    suspend fun setPrompt(prompt: String): ActionResult {
        val candidates = listOf(
            Selector(contentDescription = "Prompt"),
            Selector(contentDescription = "prompt"),
            Selector(contentDescription = "Describe"),
            Selector(textContains = "Describe your video"),
            Selector(textContains = "Type a prompt"),
            Selector(textContains = "Describe")
        )
        for (sel in candidates) {
            try {
                if (!adapter.waitForElement(sel, 5000).ok) continue
                val clickR = adapter.click(sel)
                if (!clickR.ok) {
                    logger.w(TAG, "Prompt field found ($sel) but click failed: ${clickR.message}")
                    continue
                }
                adapter.clearText(sel) // best effort; ignore result
                val typeR = adapter.typeText(sel, prompt)
                if (typeR.ok) {
                    logger.i(TAG, "Prompt entered into Flow prompt field")
                    return ActionResult(true, "Prompt entered")
                }
                logger.w(TAG, "typeText failed on $sel: ${typeR.message}")
            } catch (t: Throwable) {
                logger.w(TAG, "setPrompt attempt on $sel failed: ${t.message}")
            }
        }
        screenshotOrNull()
        logger.w(TAG, "Prompt field not found — attempted selectors: $candidates")
        return ActionResult(
            false,
            "Prompt input not found in the Flow UI — the Flow interface may have changed since this adapter was written.",
            ErrorCodes.ELEMENT_NOT_FOUND
        )
    }

    suspend fun attachReference(imagePath: String): ActionResult {
        val f = File(imagePath)
        if (!f.exists()) {
            logger.e(TAG, "Reference file not found: $imagePath")
            return ActionResult(false, "Reference file not found: $imagePath", ErrorCodes.UNKNOWN_SCREEN)
        }
        val candidates = listOf(
            Selector(textContains = "Add"),
            Selector(textContains = "Reference"),
            Selector(textContains = "Ingredient"),
            Selector(contentDescription = "Add")
        )
        for (sel in candidates) {
            try {
                if (!adapter.waitForElement(sel, 5000).ok) continue
                logger.i(TAG, "Attachment affordance found ($sel) — handing file to picker")
                // The adapter opens the system picker and returns
                // USER_ACTION_REQUIRED when it cannot pick the file itself;
                // propagate that result honestly.
                return adapter.uploadFile(sel, f)
            } catch (t: Throwable) {
                logger.w(TAG, "attachReference attempt on $sel failed: ${t.message}")
            }
        }
        screenshotOrNull()
        logger.w(TAG, "No reference-attachment control found — attempted selectors: $candidates")
        return ActionResult(
            false,
            "No reference-attachment control found in Flow UI",
            ErrorCodes.ELEMENT_NOT_FOUND
        )
    }

    suspend fun selectCharacter(name: String): ActionResult {
        // Optional / best effort.
        return tryOptional(
            "selectCharacter($name)",
            listOf(Selector(text = name), Selector(textContains = name))
        )
    }

    suspend fun setAspectRatio(ratio: String): ActionResult {
        // Optional: if the control is missing the ratio may already be correct.
        return tryOptional(
            "setAspectRatio($ratio)",
            listOf(Selector(text = ratio), Selector(textContains = ratio))
        )
    }

    suspend fun setOutputCount(n: Int): ActionResult {
        // Real stepper automation is UI-specific — do not guess coordinates.
        // If a variations/outputs control exists we leave it as-is and
        // continue; a miss only warns.
        val candidates = listOf(
            Selector(textContains = "Variations"),
            Selector(textContains = "Outputs")
        )
        for (sel in candidates) {
            try {
                if (adapter.waitForElement(sel, 8000).ok) {
                    logger.i(TAG, "Output-count control present; leaving value as-is (target n=$n, stepper is Flow-specific)")
                    return ActionResult(true, "output count control present")
                }
            } catch (t: Throwable) {
                logger.w(TAG, "setOutputCount attempt failed: ${t.message}")
            }
        }
        logger.w(TAG, "Output-count control not found — continuing (may already be correct)")
        return ActionResult(true, "output count skipped (not found)")
    }

    suspend fun clickGenerate(): ActionResult {
        val candidates = listOf(
            Selector(text = "Generate"),
            Selector(textContains = "Generate"),
            Selector(contentDescription = "Generate")
        )
        for (sel in candidates) {
            try {
                if (!adapter.waitForElement(sel, 10_000).ok) continue
                val r = adapter.click(sel)
                if (r.ok) {
                    logger.i(TAG, "Generate clicked — generation started")
                    return ActionResult(true, "Generation started")
                }
                logger.w(TAG, "Generate button found ($sel) but click failed: ${r.message}")
            } catch (t: Throwable) {
                logger.w(TAG, "clickGenerate attempt on $sel failed: ${t.message}")
            }
        }
        screenshotOrNull()
        logger.w(TAG, "Generate button not found — attempted selectors: $candidates")
        return ActionResult(
            false,
            "Generate button not found in the Flow UI — the Flow interface may have changed since this adapter was written.",
            ErrorCodes.ELEMENT_NOT_FOUND
        )
    }

    suspend fun waitForGeneration(timeoutMs: Long): ActionResult {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val snap = try {
                adapter.readScreen()
            } catch (t: Throwable) {
                logger.w(TAG, "readScreen failed during generation wait: ${t.message}")
                delay(5000)
                continue
            }
            val t = snap.texts.joinToString(" ")
            val working = t.contains("generat", ignoreCase = true) ||
                t.contains("creating", ignoreCase = true) ||
                t.contains("in progress", ignoreCase = true)
            val done = t.contains("download", ignoreCase = true)
            val elapsed = System.currentTimeMillis() - start
            if (done && !working) {
                logger.i(TAG, "Generation finished (Download affordance visible, no progress markers)")
                return ActionResult(true, "Generation finished")
            }
            if (!working && snap.texts.isNotEmpty() && elapsed > 30_000) {
                logger.i(TAG, "No progress markers after 30s — assuming generation is done")
                return ActionResult(true, "Generation finished (assumed)")
            }
            delay(5000)
        }
        logger.e(TAG, "Generation did not finish within ${timeoutMs}ms")
        return ActionResult(
            false,
            "Generation did not finish within ${timeoutMs}ms",
            ErrorCodes.GENERATION_TIMEOUT
        )
    }

    suspend fun detectGeneratedResult(): ActionResult {
        val texts = try {
            adapter.readScreen().texts
        } catch (t: Throwable) {
            emptyList<String>()
        }
        return if (texts.any { it.contains("Download", ignoreCase = true) }) {
            logger.i(TAG, "Generated result detected (Download affordance on screen)")
            ActionResult(true, "Generated result detected")
        } else {
            logger.w(TAG, "No generated result detected (no Download affordance on screen)")
            ActionResult(false, "No generated result detected", ErrorCodes.ELEMENT_NOT_FOUND)
        }
    }

    // ------------------------------------------------------------------
    // Result download (best-effort — documented honestly)
    // ------------------------------------------------------------------

    private fun isImageName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".png") || lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") || lower.endsWith(".webp")
    }

    private fun copyUriToTemp(uri: Uri): File? {
        return try {
            val tmp = File(context.cacheDir, "flow_recent_${System.currentTimeMillis()}.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            if (tmp.length() > 0) tmp else null
        } catch (t: Throwable) {
            logger.w(TAG, "Could not read MediaStore image: ${t.message}")
            null
        }
    }

    /**
     * Best-effort newest-image lookup.
     *
     * Chrome saves downloads to the public Downloads directory, which we
     * cannot list directly on API 29+ without broad storage permission. So
     * we check BOTH our app-specific downloads dir (listable) AND MediaStore
     * for images modified in the last ~3 minutes (requires READ_MEDIA_IMAGES,
     * declared in the manifest), and take the newest of what we find.
     * Returns null when nothing recent is found — callers treat that as a
     * failed download, not as proof one happened.
     */
    private fun findNewestRecentImage(): File? {
        return try {
            val appDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val appNewest = appDir
                ?.listFiles { f -> f.isFile && isImageName(f.name) }
                ?.maxByOrNull { it.lastModified() }

            val cutoffSec = System.currentTimeMillis() / 1000 - 180
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_MODIFIED
            )
            val mediaNewest: File? = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Images.Media.DATE_MODIFIED} > ?",
                arrayOf(cutoffSec.toString()),
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    copyUriToTemp(uri)
                } else null
            }

            listOfNotNull(appNewest, mediaNewest).maxByOrNull { it.lastModified() }
        } catch (t: Throwable) {
            logger.w(TAG, "MediaStore recent-image lookup failed: ${t.message}")
            null
        }
    }

    suspend fun downloadResult(workDir: File, baseName: String): File? {
        try {
            val candidates = listOf(
                Selector(textContains = "Download"),
                Selector(contentDescription = "Download")
            )
            var clicked = false
            for (sel in candidates) {
                try {
                    if (!adapter.waitForElement(sel, 10_000).ok) continue
                    if (adapter.click(sel).ok) {
                        clicked = true
                        logger.i(TAG, "Download clicked ($sel)")
                        break
                    }
                } catch (t: Throwable) {
                    logger.w(TAG, "downloadResult click attempt on $sel failed: ${t.message}")
                }
            }
            if (!clicked) {
                logger.w(TAG, "Download affordance not clicked — polling for a recent download anyway")
            }
            // Poll up to 60s for the file to land.
            val deadline = System.currentTimeMillis() + 60_000
            while (System.currentTimeMillis() < deadline) {
                val newest = findNewestRecentImage()
                if (newest != null) {
                    workDir.mkdirs()
                    val dest = File(workDir, "$baseName.png")
                    newest.inputStream().use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    logger.i(TAG, "Downloaded result -> ${dest.absolutePath} (${dest.length()} bytes)")
                    return dest
                }
                delay(5000)
            }
        } catch (t: Throwable) {
            logger.e(TAG, "downloadResult failed: ${t.message}", throwable = t)
            return null
        }
        logger.e(TAG, "No downloaded image found after clicking Download (60s poll)")
        return null
    }

    // ------------------------------------------------------------------
    // Full single-generation orchestration
    // ------------------------------------------------------------------

    suspend fun generateSingle(
        task: Task,
        prompt: String,
        workDir: File,
        aspectRatio: String,
        outputCount: Int
    ): FlowGenResult {
        val taskId = task.taskId

        val opened = openFlow()
        if (!opened.ok) return fail("openFlow", opened, taskId, workDir)
        if (checkForSecurityChallenge()) {
            return fail(
                "securityChallenge",
                ActionResult(false, "Manual action required.", ErrorCodes.USER_ACTION_REQUIRED),
                taskId, workDir
            )
        }

        val projectName = try {
            JSONObject(task.metadata).optString("projectName", "")
        } catch (t: Throwable) {
            ""
        }
        selectProject(projectName) // optional — always ok
        openGenerationInterface() // optional — always ok

        val promptR = setPrompt(prompt)
        if (!promptR.ok) return fail("setPrompt", promptR, taskId, workDir)

        // Reference attachments: file-backed refs are attached honestly;
        // everything else is treated as a character/name best-effort select.
        val imageExts = listOf(".png", ".jpg", ".jpeg", ".webp")
        val isImageRef: (String) -> Boolean = { ref ->
            imageExts.any { ref.endsWith(it, ignoreCase = true) } && File(ref).exists()
        }
        for (ref in task.references) {
            if (isImageRef(ref)) {
                val ar = attachReference(ref)
                if (!ar.ok) {
                    if (ar.errorCode == ErrorCodes.USER_ACTION_REQUIRED) {
                        onNeedUser(
                            "Manual action required: the system file picker is open in Chrome — " +
                                "please select the reference image, then press Resume in JAX."
                        )
                    }
                    return fail("attachReference", ar, taskId, workDir)
                }
            }
        }
        for (ref in task.references) {
            if (!isImageRef(ref)) selectCharacter(ref) // optional — always ok
        }

        setAspectRatio(aspectRatio) // optional — always ok
        setOutputCount(outputCount) // optional — always ok

        // Final security gate before committing to generation.
        if (checkForSecurityChallenge()) {
            return fail(
                "securityChallenge",
                ActionResult(false, "Manual action required.", ErrorCodes.USER_ACTION_REQUIRED),
                taskId, workDir
            )
        }

        val genR = clickGenerate()
        if (!genR.ok) return fail("clickGenerate", genR, taskId, workDir)

        val waitR = waitForGeneration(10 * 60 * 1000L)
        if (!waitR.ok) return fail("waitForGeneration", waitR, taskId, workDir)

        val detectR = detectGeneratedResult()
        if (!detectR.ok) return fail("detectGeneratedResult", detectR, taskId, workDir)

        val file = downloadResult(workDir, "scene_raw")
        if (file == null) {
            logger.e(TAG, "Generated result detected but the file could not be downloaded", taskId)
            return FlowGenResult(
                false, null,
                JaxError(
                    ErrorCodes.DOWNLOAD_FAILED,
                    "Generated result detected but the file could not be downloaded — see logs",
                    taskId
                )
            )
        }
        logger.i(TAG, "Generation complete: ${file.absolutePath}", taskId)
        return FlowGenResult(true, file, null)
    }

    companion object {
        const val FLOW_URL = "https://labs.google/fx"
        private const val TAG = "Flow"
    }
}
