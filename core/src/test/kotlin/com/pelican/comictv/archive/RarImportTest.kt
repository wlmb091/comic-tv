package com.pelican.comictv.archive

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class RarImportTest {
    @Test fun `store imports a RAR even though internal filename is cbz`() {
        val root = Files.createTempDirectory("rar-import").toFile()
        try {
            val store = LocalComicStore(root)
            val pages = javaClass.getResourceAsStream("/archives/pages-rar5.rar")!!.use { store.import("rar", it) }
            assertEquals(listOf("2.png", "10.png"), pages.map { it.key })
            assertTrue(store.original("rar").isFile)
        } finally { root.deleteRecursively() }
    }
}
