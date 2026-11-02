package com.pelican.comictv.reader

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class PageDrawingStoreTest {
    @Test fun savesReplacesAndRemovesOnePage() {
        val root = Files.createTempDirectory("drawings").toFile()
        try {
            val store = PageDrawingStore(root)
            assertNull(store.file("a-1", 3))
            store.save("a-1", 3, 800, 1200, byteArrayOf(1, 2))
            store.save("a-1", 3, 900, 1300, byteArrayOf(3))
            assertArrayEquals(byteArrayOf(3), store.file("a-1", 3)!!.readBytes())
            assertEquals(900 to 1300, store.pageSize("a-1", 3))
            store.save("a-1", 4, 800, 1200, byteArrayOf(4))
            store.delete("a-1", 3)
            assertNull(store.file("a-1", 3)); assertNull(store.pageSize("a-1", 3))
            assertNotNull(store.file("a-1", 4))
            store.deleteEdition("a-1")
            assertNull(store.file("a-1", 4))
        } finally { root.deleteRecursively() }
    }
    @Test fun refusesAPathOutsideTheFolder() {
        val root = Files.createTempDirectory("drawings").toFile()
        try {
            val store = PageDrawingStore(root)
            for (id in listOf("../x", "a/b", "", "a.b")) assertThrows(IllegalArgumentException::class.java) { store.file(id, 0) }
            assertThrows(IllegalArgumentException::class.java) { store.file("a", -1) }
        } finally { root.deleteRecursively() }
    }
    @Test fun rememberesThatDrawingsAreHidden() {
        val root = Files.createTempDirectory("drawings").toFile()
        try {
            val store = PageDrawingStore(root)
            assertFalse(store.hidden); store.hidden = true
            assertTrue(PageDrawingStore(root).hidden); store.hidden = false
            assertFalse(store.hidden)
        } finally { root.deleteRecursively() }
    }
}
