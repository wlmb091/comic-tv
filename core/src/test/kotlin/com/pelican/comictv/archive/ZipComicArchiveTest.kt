package com.pelican.comictv.archive

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipComicArchiveTest {
    private fun zip(vararg entries: Pair<String, ByteArray>): File {
        val file = Files.createTempFile("comic", ".cbz").toFile().apply { deleteOnExit() }
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry()
            }
        }
        return file
    }

    @Test fun `sorts pages naturally and ignores metadata`() {
        val file = zip("10.jpg" to byteArrayOf(10), "2.png" to byteArrayOf(2),
            "1.webp" to byteArrayOf(1), "__MACOSX/3.jpg" to byteArrayOf(0), "readme.txt" to byteArrayOf(0))
        assertEquals(listOf("1.webp", "2.png", "10.jpg"), ZipComicArchive().index(file).map { it.key })
    }

    @Test fun `handles long numeric names without overflow`() {
        assertTrue(NaturalOrder.compare("9.jpg", "99999999999999999999.jpg") < 0)
    }

    @Test fun `rejects traversal and absolute names`() {
        listOf("../page.jpg", "/page.jpg", "C:/page.jpg", "a\\..\\page.jpg").forEach { name ->
            assertThrows(ComicArchiveException::class.java) { ZipComicArchive().index(zip(name to byteArrayOf(1))) }
        }
    }

    @Test fun `rejects invalid empty and oversized archives`() {
        val bad = Files.createTempFile("invalid", ".zip").toFile().apply { writeText("not a zip"); deleteOnExit() }
        assertThrows(ComicArchiveException::class.java) { ZipComicArchive().index(bad) }
        assertThrows(ComicArchiveException::class.java) { ZipComicArchive().index(zip("text.txt" to byteArrayOf(1))) }
        val archive = ZipComicArchive(ArchiveLimits(maxEntries = 1))
        assertThrows(ComicArchiveException::class.java) { archive.index(zip("1.jpg" to byteArrayOf(1), "2.jpg" to byteArrayOf(2))) }
    }

    @Test fun `extracts a page within limit and rejects excess`() {
        val root = Files.createTempDirectory("pages").toFile()
        try {
            val archive = ZipComicArchive(ArchiveLimits(maxPageBytes = 8))
            val file = zip("1.png" to ByteArray(8) { 7 })
            val page = archive.index(file).single()
            assertArrayEquals(ByteArray(8) { 7 }, archive.extract(file, page, File(root, "page")).readBytes())
            assertThrows(ComicArchiveException::class.java) { archive.index(zip("1.jpg" to ByteArray(9))) }
        } finally { root.deleteRecursively() }
    }

    @Test fun `stream enforces limit without known size and removes partial output`() {
        val root = Files.createTempDirectory("partial").toFile()
        try {
            val target = File(root, "page")
            assertThrows(ComicArchiveException::class.java) {
                copyLimited(ByteArray(9).inputStream(), target, 8)
            }
            assertFalse(target.exists())
            assertFalse(File(root, "page.part").exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun `cache evicts oldest unprotected file`() {
        val root = Files.createTempDirectory("lru").toFile()
        try {
            val old = File(root, "old").apply { writeBytes(ByteArray(4)); setLastModified(10) }
            val current = File(root, "current").apply { writeBytes(ByteArray(4)); setLastModified(20) }
            PageCache(root, 5).trim(setOf(current.name))
            assertFalse(old.exists()); assertTrue(current.exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun `rejected image is removed and cache is trimmed on failure`() {
        val root = Files.createTempDirectory("rejected").toFile()
        try {
            File(root, "old").writeBytes(ByteArray(8))
            val cache = PageCache(root, 5)
            assertThrows(java.io.IOException::class.java) {
                cache.materialize("bad", emptySet(), write = { it.writeBytes(ByteArray(8)) },
                    validate = { throw java.io.IOException("Invalid image") })
            }
            assertFalse(File(root, "bad").exists())
            assertTrue(root.listFiles().orEmpty().sumOf { it.length() } <= 5)
        } finally { root.deleteRecursively() }
    }
}
