package com.pelican.comictv.archive

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.File

class LocalComicRemovalTest {
    @Test fun stagedRemovalRollsBackAndOnlyDeletesTheSelectedOwnedCopy() {
        val root = Files.createTempDirectory("comic-remove").toFile()
        try {
            val store = LocalComicStore(root)
            store.original("one").writeText("owned")
            store.original("other").writeText("other")
            val external = File(root, "external.pdf").apply { writeText("external") }
            store.stageRemoval("one")
            assertFalse(store.original("one").exists())
            store.reconcile(setOf("one", "other"))
            assertEquals("owned", store.original("one").readText())
            store.stageRemoval("one")
            store.reconcile(setOf("other"))
            assertFalse(store.original("one").exists())
            assertFalse(File(root,"one.cbz.deleting").exists())
            assertEquals("other", store.original("other").readText())
            assertEquals("external", external.readText())
            assertThrows(IllegalArgumentException::class.java) { store.stageRemoval("../outside") }
            assertThrows(IllegalArgumentException::class.java) { store.stageRemoval("online-abc") }
        } finally { root.deleteRecursively() }
    }
}
