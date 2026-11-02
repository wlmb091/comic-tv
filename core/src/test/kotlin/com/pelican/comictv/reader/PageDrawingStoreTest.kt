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
    @Test fun layersFollowThePage() {
        val root = Files.createTempDirectory("drawings").toFile()
        try {
            val store = PageDrawingStore(root)
            assertNull(store.layers("a", 1))
            store.saveLayers("a", 1, byteArrayOf(7, 8).inputStream())
            store.saveLayers("a", 1, byteArrayOf(9).inputStream())
            assertArrayEquals(byteArrayOf(9), store.layers("a", 1)!!.readBytes())
            store.delete("a", 1)
            assertNull(store.layers("a", 1))
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
    @Test fun textStretchesHaveTheirOwnDrawingPages() {
        val root = Files.createTempDirectory("drawings").toFile()
        try {
            val store = PageDrawingStore(root)
            val first = store.addExcerpt("a-1", 2, 10, 50, byteArrayOf(1))
            val second = store.addExcerpt("a-1", 2, 60, 90, byteArrayOf(2))
            store.addExcerpt("a-1", 3, 10, 50, byteArrayOf(3))
            assertEquals(TextExcerpt.FIRST_PAGE, first.page)
            assertEquals(listOf(first, second), store.excerpts("a-1", 2))
            assertTrue(runCatching { store.addExcerpt("a-1", 2, 40, 70, byteArrayOf(4)) }.isFailure)
            assertTrue(store.excerptBase("a-1", first.page)!!.isFile)
            store.delete("a-1", first.page)
            assertNull(store.excerptBase("a-1", first.page))
            assertEquals(listOf(second), store.excerpts("a-1", 2))
            assertEquals(second.page + 2, store.addExcerpt("a-1", 9, 0, 1, byteArrayOf(5)).page)
        } finally { root.deleteRecursively() }
    }
    @Test fun aRestoreNeverReplacesWhatIsHere() {
        val root = Files.createTempDirectory("drawings").toFile()
        try {
            val store = PageDrawingStore(root)
            store.save("a-1", 3, 10, 20, byteArrayOf(9))
            fun put(name: String, value: Int) = store.restoreFile(name, byteArrayOf(value.toByte()).inputStream(), 10)
            put("a-1/3.size", 1); put("a-1/3.png", 1); put("a-1/4.png", 2); put("a-1/4.size", 2); put("b-2/5.png", 3)
            store.finishRestore()
            assertEquals(9, store.file("a-1", 3)!!.readBytes()[0].toInt())
            assertEquals(2, store.file("a-1", 4)!!.readBytes()[0].toInt())
            assertEquals(3, store.file("b-2", 5)!!.readBytes()[0].toInt())
            for (bad in listOf("a-1/../x.png", "a-1/3.exe", "x/y/3.png", "a-1/abc.png")) assertTrue(bad, runCatching { put(bad, 1) }.isFailure)
            assertTrue(runCatching { store.restoreFile("c-3/1.png", ByteArray(20).inputStream(), 10) }.isFailure)
            assertNull(store.file("c-3", 1))
            assertEquals(listOf("a-1/3.png", "a-1/3.size", "a-1/4.png", "a-1/4.size", "b-2/5.png"), store.backupFiles().map { it.first })
        } finally { root.deleteRecursively() }
    }
}
