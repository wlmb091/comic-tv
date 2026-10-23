package com.pelican.comictv.archive

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LocalComicStoreTest {
    private fun comic(): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { it.putNextEntry(ZipEntry("1.jpg")); it.write(byteArrayOf(1, 2, 3)); it.closeEntry() }
    }.toByteArray()

    @Test fun `valid import has stable original and page count`() {
        val root = Files.createTempDirectory("library").toFile()
        try {
            val result = LocalComicStore(root).import("stable-id", comic().inputStream())
            assertEquals(1, result.size)
            assertTrue(LocalComicStore(root).original("stable-id").isFile)
        } finally { root.deleteRecursively() }
    }
    @Test fun `broken input is not promoted and leaves no partial file`() {
        val root = Files.createTempDirectory("library").toFile()
        try {
            val broken = object : InputStream() { override fun read(): Int = throw IOException("disk or transfer failure") }
            assertThrows(IOException::class.java) { LocalComicStore(root).import("bad", broken) }
            assertTrue(root.listFiles().orEmpty().isEmpty())
            assertThrows(ComicArchiveException::class.java) { LocalComicStore(root).import("bad", byteArrayOf(1).inputStream()) }
            assertTrue(root.listFiles().orEmpty().isEmpty())
        } finally { root.deleteRecursively() }
    }
    @Test fun `reconcile removes only orphan imports`() {
        val root = Files.createTempDirectory("library").toFile()
        try {
            val store = LocalComicStore(root)
            store.import("keep", comic().inputStream()); store.import("orphan", comic().inputStream())
            store.reconcile(setOf("keep"))
            assertTrue(store.original("keep").exists()); assertFalse(store.original("orphan").exists())
        } finally { root.deleteRecursively() }
    }
}
