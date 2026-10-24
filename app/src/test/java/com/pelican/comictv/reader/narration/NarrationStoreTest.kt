package com.pelican.comictv.reader.narration

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class NarrationStoreTest {
    @Test fun bookmarksAndSettingsRoundTripWithoutUsingBookIdAsAPath() = runBlocking {
        val root = Files.createTempDirectory("narration-store").toFile()
        try {
            val store = FileNarrationStore(root)
            val position = NarrationBookmark(3, "a".repeat(64), 120)
            store.save("../../book", position)
            store.saveSettings(NarrationPreferences("pt-BR-local", 2))
            val restored = FileNarrationStore(root)
            assertEquals(position, restored.load("../../book"))
            assertEquals(2, restored.settings().rateIndex)
            assertEquals("pt-BR-local", restored.settings().voiceId)
            assertTrue(root.listFiles()!!.all { it.parentFile?.canonicalFile == root.canonicalFile })
            root.listFiles()!!.forEach { it.writeText("invalid") }
            assertNull(restored.load("../../book"))
            assertEquals(NarrationPreferences(), restored.settings())
        } finally { root.deleteRecursively() }
    }
}
