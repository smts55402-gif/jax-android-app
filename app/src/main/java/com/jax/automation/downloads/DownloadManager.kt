package com.jax.automation.downloads

import android.content.Context
import com.jax.automation.database.DownloadDao
import com.jax.automation.logging.JaxLogger
import com.jax.automation.models.DownloadRecord
import com.jax.automation.models.newId
import com.jax.automation.models.now
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns the on-disk layout for downloaded scene assets and records every
 * approved download in Room.
 *
 * Storage layout (all mkdirs'd on access):
 *   JAX/Projects/<project>/Scenes/Scene_<sceneId>/   approved scene files
 *   JAX/Projects/<project>/References/               reference images
 *   JAX/Projects/<project>/Approved/                  approved exports
 *   JAX/Projects/<project>/Failed/                    failed captures
 *   JAX/Projects/<project>/Logs/Scene_<sceneId>/      debug screenshots
 *
 * App-specific storage (getExternalFilesDir) is used on purpose: no
 * READ/WRITE_EXTERNAL_STORAGE permission is needed and the files are removed
 * automatically when the app is uninstalled. Falls back to the internal
 * files dir when external storage is unavailable.
 */
class DownloadManager(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val logger: JaxLogger
) {
    companion object {
        private const val TAG = "DownloadManager"
    }

    /** Root of all JAX download storage. */
    fun baseDir(): File =
        (context.getExternalFilesDir(null)?.resolve("JAX") ?: File(context.filesDir, "JAX"))
            .apply { mkdirs() }

    fun projectDir(projectName: String): File =
        baseDir().resolve("Projects").resolve(sanitize(projectName)).apply { mkdirs() }

    fun sceneDir(projectName: String, sceneId: String): File =
        projectDir(projectName).resolve("Scenes").resolve("Scene_$sceneId").apply { mkdirs() }

    fun referencesDir(projectName: String): File =
        projectDir(projectName).resolve("References").apply { mkdirs() }

    fun approvedDir(projectName: String): File =
        projectDir(projectName).resolve("Approved").apply { mkdirs() }

    fun failedDir(projectName: String): File =
        projectDir(projectName).resolve("Failed").apply { mkdirs() }

    fun logsDir(projectName: String): File =
        projectDir(projectName).resolve("Logs").apply { mkdirs() }

    /**
     * Copies [source] into the scene directory under [fileName] (deduped with
     * _v2/_v3 suffixes on collision), verifies the copy, and records it in Room.
     *
     * @throws IllegalArgumentException if [source] does not exist.
     * @throws IllegalStateException if the copy fails verification.
     */
    suspend fun saveApproved(
        source: File,
        projectName: String,
        sceneId: String,
        fileName: String,
        taskId: String
    ): File = withContext(Dispatchers.IO) {
        require(source.exists()) { "Source file does not exist: ${source.absolutePath}" }
        val dest = dedupe(sceneDir(projectName, sceneId).resolve(fileName))
        source.copyTo(dest, overwrite = false)
        check(verifyExists(dest.absolutePath)) { "Copy verification failed" }
        downloadDao.insert(
            DownloadRecord(
                id = newId(),
                taskId = taskId,
                projectId = projectName,
                sceneId = sceneId,
                filePath = dest.absolutePath,
                fileName = dest.name,
                bytes = dest.length(),
                downloadedAt = now(),
                verified = true
            )
        )
        logger.i(TAG, "Saved approved file ${dest.name} (${dest.length()} bytes)", taskId)
        dest
    }

    /**
     * Writes raw PNG [screenshotBytes] to
     * Logs/Scene_<sceneId>/<name>.png. Returns null on any failure.
     */
    suspend fun saveDebug(
        screenshotBytes: ByteArray,
        projectName: String,
        sceneId: String,
        name: String
    ): File? = withContext(Dispatchers.IO) {
        try {
            val dir = logsDir(projectName).resolve("Scene_$sceneId").apply { mkdirs() }
            val dest = dir.resolve("$name.png")
            dest.writeBytes(screenshotBytes)
            dest
        } catch (t: Throwable) {
            logger.e(TAG, "Failed to save debug screenshot", null, t)
            null
        }
    }

    /** True only when the file exists and is non-empty. */
    fun verifyExists(path: String): Boolean =
        File(path).let { it.exists() && it.length() > 0 }

    /** Project names must be filesystem-safe: anything outside [A-Za-z0-9 _-] becomes "_". */
    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9 _-]"), "_").trim().take(64).ifBlank { "Project" }

    /** Returns a non-colliding sibling: name_v2.ext, name_v3.ext, ... */
    private fun dedupe(file: File): File {
        if (!file.exists()) return file
        val parent = file.parentFile ?: return file
        val stem = file.nameWithoutExtension
        val ext = file.extension.let { if (it.isEmpty()) "" else ".$it" }
        var version = 2
        var candidate = File(parent, "${stem}_v$version$ext")
        while (candidate.exists()) {
            version++
            candidate = File(parent, "${stem}_v$version$ext")
        }
        return candidate
    }
}
