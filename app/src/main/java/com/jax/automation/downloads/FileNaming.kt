package com.jax.automation.downloads

/**
 * Deterministic, filesystem-safe names for downloaded scene files.
 *
 * Pure Kotlin — no Android imports — so it runs in JVM unit tests.
 */
object FileNaming {

    /**
     * Builds a scene file name like "S001_0000-0007_AncientYou_01.png".
     *
     * The [tag] is sanitized: every char outside [A-Za-z0-9_] becomes "_",
     * runs of "_" are collapsed, the result is trimmed to 32 chars, and a
     * blank result falls back to "Scene".
     */
    fun sceneFileName(sceneIndex: Int, startSec: Int, endSec: Int, tag: String, variant: Int): String {
        val clean = tag
            .replace(Regex("[^A-Za-z0-9_]"), "_")
            .replace(Regex("_+"), "_")
            .take(32)
            .ifBlank { "Scene" }
        return "S%03d_%04d-%04d_%s_%02d.png".format(sceneIndex, startSec, endSec, clean, variant)
    }
}
