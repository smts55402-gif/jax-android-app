package com.jax.automation.ai

import com.jax.automation.models.CharacterLock
import com.jax.automation.models.LocationLock
import com.jax.automation.models.ObjectLock
import com.jax.automation.models.Scene
import com.jax.automation.models.StructuredTask
import com.jax.automation.models.StyleLock
import com.jax.automation.references.LockManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeLockManager : LockManager {
    override suspend fun getCharacterLock(characterId: String): CharacterLock? =
        if (characterId == "AncientYou") {
            CharacterLock(
                characterId = "AncientYou",
                hair = "messy short black hair",
                clothing = "brown animal skin tunic"
            )
        } else {
            null
        }

    override suspend fun saveCharacterLock(lock: CharacterLock) = Unit

    override suspend fun getStyleLock(): StyleLock? =
        StyleLock(
            illustrationStyle = "flat 2D cartoon",
            negativeRules = listOf("no realism")
        )

    override suspend fun saveStyleLock(lock: StyleLock) = Unit

    override suspend fun getLocationLock(locationId: String): LocationLock? =
        if (locationId == "Pond_01") {
            LocationLock(locationId = "Pond_01", appearance = "still pond")
        } else {
            null
        }

    override suspend fun saveLocationLock(lock: LocationLock) = Unit

    override suspend fun getObjectLock(objectId: String): ObjectLock? =
        if (objectId == "Spear_01") {
            ObjectLock(objectId = "Spear_01", shape = "wooden spear")
        } else {
            null
        }

    override suspend fun saveObjectLock(lock: ObjectLock) = Unit
}

class PromptBuilderTest {

    @Test
    fun buildIncludesLockedAttributesWithSectionsInOrder() = runTest {
        val builder = PromptBuilder(FakeLockManager())
        val task = StructuredTask(
            action = "GENERATE_IMAGE",
            target = "GOOGLE_FLOW",
            project = "Episode_01",
            sceneId = "017",
            characters = listOf("AncientYou"),
            locations = listOf("Pond_01"),
            objects = listOf("Spear_01")
        )
        val scene = Scene(
            projectId = "proj-1",
            sceneIndex = 0,
            startTimeMs = 0,
            endTimeMs = 4000,
            exactScript = "AncientYou wakes at dawn.",
            visualDescription = "AncientYou stands beside the still pond at dawn."
        )

        val prompt = builder.build(task, scene)

        assertTrue(prompt.contains("messy short black hair"))
        assertTrue(prompt.contains("no realism"))
        assertTrue(prompt.contains("still pond"))
        assertTrue(prompt.contains("wooden spear"))

        val styleIdx = prompt.indexOf("STYLE")
        val characterIdx = prompt.indexOf("CHARACTER")
        val locationIdx = prompt.indexOf("LOCATION")
        val objectIdx = prompt.indexOf("OBJECT")
        val sceneIdx = prompt.indexOf("SCENE")
        assertTrue(styleIdx >= 0)
        assertTrue(characterIdx >= 0)
        assertTrue(locationIdx >= 0)
        assertTrue(objectIdx >= 0)
        assertTrue(sceneIdx >= 0)
        assertTrue(styleIdx < characterIdx)
        assertTrue(characterIdx < locationIdx)
        assertTrue(locationIdx < objectIdx)
        assertTrue(objectIdx < sceneIdx)
    }
}
