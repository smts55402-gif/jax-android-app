package com.jax.automation.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNamingTest {

    @Test
    fun exactSceneFileNameFormat() {
        assertEquals(
            "S001_0000-0007_AncientYou_01.png",
            FileNaming.sceneFileName(1, 0, 7, "AncientYou", 1)
        )
    }

    @Test
    fun tagIsSanitized() {
        val name = FileNaming.sceneFileName(12, 65, 130, "Pond 01!", 3)
        assertFalse("spaces must be removed", name.contains(" "))
        assertFalse("bang must be removed", name.contains("!"))
        assertTrue(name.startsWith("S012_0065-0130_"))
        assertTrue(name.endsWith("_03.png"))
    }

    @Test
    fun sceneIndexIsZeroPadded() {
        val name = FileNaming.sceneFileName(5, 0, 7, "X", 1)
        assertTrue(name.startsWith("S005_"))
    }
}
