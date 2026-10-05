package com.jax.automation.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptImporterTest {

    private val sample = """
        Some intro narration before any timestamp is ignored.

        [00:00]
        AncientYou wakes up. He looks around, blinking.

        [00:04]
        He walks to the pond.
        The water is still.

        [01:02:03]
        Night falls; stars appear!
    """.trimIndent()

    @Test
    fun parsesThreeScenes() {
        val scenes = ScriptImporter.parse("proj-1", sample)
        assertEquals(3, scenes.size)
    }

    @Test
    fun sceneIdsArePaddedSequence() {
        val scenes = ScriptImporter.parse("proj-1", sample)
        assertEquals("001", scenes[0].sceneId)
        assertEquals("002", scenes[1].sceneId)
        assertEquals("003", scenes[2].sceneId)
    }

    @Test
    fun startTimesParsedToMs() {
        val scenes = ScriptImporter.parse("proj-1", sample)
        assertEquals(0L, scenes[0].startTimeMs)
        assertEquals(4000L, scenes[1].startTimeMs)
        assertEquals(3723000L, scenes[2].startTimeMs)
    }

    @Test
    fun endTimesChainedToNextStart() {
        val scenes = ScriptImporter.parse("proj-1", sample)
        assertEquals(4000L, scenes[0].endTimeMs)
        assertEquals(3723000L, scenes[1].endTimeMs)
        assertEquals(3723000L + 30000L, scenes[2].endTimeMs)
    }

    @Test
    fun exactScriptPreservedVerbatim() {
        val scenes = ScriptImporter.parse("proj-1", sample)
        assertEquals("proj-1", scenes[0].projectId)
        assertTrue(
            scenes[0].exactScript.contains("AncientYou wakes up. He looks around, blinking.")
        )
        assertTrue(
            scenes[1].exactScript.contains("He walks to the pond.\nThe water is still.")
        )
        assertTrue(scenes[2].exactScript.contains("Night falls; stars appear!"))
    }

    @Test
    fun textBeforeFirstTimestampIsIgnored() {
        val scenes = ScriptImporter.parse("proj-1", sample)
        assertFalse(scenes[0].exactScript.contains("ignored"))
    }

    @Test
    fun noTimestampsGivesEmptyList() {
        assertTrue(
            ScriptImporter.parse("proj-1", "just narration, no timestamps here").isEmpty()
        )
    }
}
