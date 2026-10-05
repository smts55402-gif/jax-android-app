package com.jax.automation.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandValidatorTest {

    private fun assertValid(json: String): com.jax.automation.models.StructuredTask {
        val result = CommandValidator.validate(json)
        assertTrue(
            "expected Valid but got: $result",
            result is CommandValidator.ValidationResult.Valid
        )
        return (result as CommandValidator.ValidationResult.Valid).task
    }

    private fun assertInvalid(json: String): String {
        val result = CommandValidator.validate(json)
        assertTrue(
            "expected Invalid but got: $result",
            result is CommandValidator.ValidationResult.Invalid
        )
        return (result as CommandValidator.ValidationResult.Invalid).reason
    }

    @Test
    fun specExampleParsesToValidWithAllFields() {
        val json = "{" +
            "\"action\":\"GENERATE_IMAGE\"," +
            "\"target\":\"GOOGLE_FLOW\"," +
            "\"project\":\"Episode_01\"," +
            "\"scene_id\":\"017\"," +
            "\"characters\":[\"AncientYou\"]," +
            "\"locations\":[\"Pond_01\"]," +
            "\"objects\":[\"Spear_01\"]," +
            "\"style\":\"PREMIUM_STICKMAN_V2\"," +
            "\"aspect_ratio\":\"16:9\"," +
            "\"variations\":2," +
            "\"qa_required\":true," +
            "\"download\":true" +
            "}"
        val task = assertValid(json)
        assertEquals("GENERATE_IMAGE", task.action)
        assertEquals("GOOGLE_FLOW", task.target)
        assertEquals("Episode_01", task.project)
        assertEquals("017", task.sceneId)
        assertEquals(listOf("AncientYou"), task.characters)
        assertEquals(listOf("Pond_01"), task.locations)
        assertEquals(listOf("Spear_01"), task.objects)
        assertEquals("PREMIUM_STICKMAN_V2", task.style)
        assertEquals("16:9", task.aspectRatio)
        assertEquals(2, task.variations)
        assertTrue(task.qaRequired)
        assertTrue(task.download)
    }

    @Test
    fun unknownActionIsInvalid() {
        assertInvalid("{\"action\":\"FLY_TO_MOON\",\"target\":\"SYSTEM\"}")
    }

    @Test
    fun missingActionIsInvalid() {
        assertInvalid("{\"target\":\"SYSTEM\"}")
    }

    @Test
    fun missingTargetIsInvalidAndNeverDefaulted() {
        assertInvalid("{\"action\":\"SHOW_FAILED\"}")
    }

    @Test
    fun generateImageMissingProjectIsInvalid() {
        assertInvalid(
            "{\"action\":\"GENERATE_IMAGE\",\"target\":\"GOOGLE_FLOW\",\"scene_id\":\"017\"}"
        )
    }

    @Test
    fun generateImageMissingSceneIdIsInvalid() {
        assertInvalid(
            "{\"action\":\"GENERATE_IMAGE\",\"target\":\"GOOGLE_FLOW\",\"project\":\"Episode_01\"}"
        )
    }

    @Test
    fun variationsOutOfRangeIsInvalid() {
        assertInvalid(
            "{\"action\":\"GENERATE_IMAGE\",\"target\":\"GOOGLE_FLOW\"," +
                "\"project\":\"Episode_01\",\"scene_id\":\"017\",\"variations\":99}"
        )
    }

    @Test
    fun badAspectRatioIsInvalid() {
        assertInvalid("{\"action\":\"SHOW_FAILED\",\"target\":\"SYSTEM\",\"aspect_ratio\":\"wide\"}")
    }

    @Test
    fun malformedJsonIsInvalid() {
        assertInvalid("{this is not json")
    }

    @Test
    fun stringQaRequiredIsInvalid() {
        assertInvalid("{\"action\":\"SHOW_FAILED\",\"target\":\"SYSTEM\",\"qa_required\":\"yes\"}")
    }

    @Test
    fun minimalCommandGetsDefaults() {
        val task = assertValid("{\"action\":\"SHOW_FAILED\",\"target\":\"SYSTEM\"}")
        assertEquals("SHOW_FAILED", task.action)
        assertEquals("SYSTEM", task.target)
        assertEquals(1, task.variations)
        assertEquals("16:9", task.aspectRatio)
        assertTrue(task.qaRequired)
        assertTrue(task.download)
        assertTrue(task.characters.isEmpty())
        assertTrue(task.locations.isEmpty())
        assertTrue(task.objects.isEmpty())
    }
}
