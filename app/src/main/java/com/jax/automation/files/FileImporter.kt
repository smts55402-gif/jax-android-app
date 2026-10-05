package com.jax.automation.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.jax.automation.logging.JaxLogger
import com.jax.automation.models.now
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of importing a user-picked document into app storage. */
data class ImportedFile(val file: File, val kind: String)

/**
 * Copies a user-picked document (Storage Access Framework [Uri]) into an
 * app-owned destination directory. Only plain-text and common image formats
 * are accepted.
 */
class FileImporter(private val context: Context, private val logger: JaxLogger) {

    companion object {
        private const val TAG = "FileImporter"
        private val SUPPORTED = setOf("txt", "json", "csv", "png", "jpg", "jpeg", "webp")
        private val MIME_TO_EXT = mapOf(
            "text/plain" to "txt",
            "application/json" to "json",
            "text/csv" to "csv",
            "image/png" to "png",
            "image/jpeg" to "jpg",
            "image/jpg" to "jpg",
            "image/webp" to "webp"
        )
    }

    /**
     * Copies [uri] into [destDir] (created if missing) with a deduped name.
     * Returns null — with a warning log — for unsupported types, and null —
     * with an error log — on any failure.
     */
    suspend fun import(uri: Uri, destDir: File): ImportedFile? = withContext(Dispatchers.IO) {
        try {
            val displayName = queryDisplayName(uri)
            val ext = extensionFromName(displayName) ?: extensionFromMime(uri)
            if (ext == null || ext !in SUPPORTED) {
                logger.w(TAG, "Unsupported file type for import: $displayName")
                return@withContext null
            }
            destDir.mkdirs()
            val base = displayName?.takeIf { it.isNotBlank() } ?: "import_${now()}"
            val dest = dedupeName(destDir, base, ext)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: run {
                logger.w(TAG, "Could not open input stream for $uri")
                return@withContext null
            }
            logger.i(TAG, "Imported $displayName -> ${dest.absolutePath}")
            ImportedFile(dest, ext)
        } catch (t: Throwable) {
            logger.e(TAG, "Import failed for $uri", null, t)
            null
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    /** Extension from the display name, or null when absent/unusable. */
    private fun extensionFromName(displayName: String?): String? {
        if (displayName.isNullOrBlank() || !displayName.contains('.')) return null
        return displayName.substringAfterLast('.').lowercase().takeIf { it.isNotEmpty() }
    }

    /** Extension from the content-resolver MIME type, or null when unknown. */
    private fun extensionFromMime(uri: Uri): String? =
        context.contentResolver.getType(uri)?.let { MIME_TO_EXT[it] }

    /** Dedupes the destination name: base.ext, base_v2.ext, base_v3.ext, ... */
    private fun dedupeName(dir: File, base: String, ext: String): File {
        val stem = base.substringBeforeLast('.').ifBlank { "import_${now()}" }
        var candidate = File(dir, "$stem.$ext")
        var version = 2
        while (candidate.exists()) {
            candidate = File(dir, "${stem}_v$version.$ext")
            version++
        }
        return candidate
    }
}
