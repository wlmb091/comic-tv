package com.pelican.comictv.archive

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CollectionArchiveTest {
    private fun inDirectory(block: (File) -> Unit) {
        val root = Files.createTempDirectory("collection-test").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
    private fun zip(root: File, vararg names: String): File = File(root, "package.zip").also { file ->
        ZipOutputStream(file.outputStream()).use { out -> names.forEach {
            out.putNextEntry(ZipEntry(it)); out.write("payload:$it".toByteArray()); out.closeEntry()
        } }
    }
    @Test fun `finds mixed edition files in natural order and extracts to generated path`() = inDirectory { root ->
        val file = zip(root, "set/10.cbr", "set/2.cbz", "3.pdf", "4.epub", "notes.txt")
        val reader = CollectionArchive()
        val entries = reader.inspect(file)!!
        assertEquals(listOf("3.pdf", "4.epub", "set/2.cbz", "set/10.cbr"), entries.map { it.name })
        val target = File(root, "generated")
        reader.extract(file, entries.last(), target)
        assertEquals("payload:set/10.cbr", target.readText())
    }
    @Test fun `keeps ordinary image comics and EPUB out of collection path`() = inDirectory { root ->
        val reader = CollectionArchive()
        assertNull(reader.inspect(zip(root, "1.png", "nested.cbz")))
        assertNull(reader.inspect(zip(root, "META-INF/container.xml", "nested.cbz")))
        val pdf = File(root, "book.pdf").apply { writeText("%PDF-1.4") }
        assertNull(reader.inspect(pdf))
    }
    @Test fun `rejects traversal and bounds including ignored bytes`() = inDirectory { root ->
        assertThrows(ComicArchiveException::class.java) { CollectionArchive().inspect(zip(root, "../escape.cbz")) }
        assertThrows(ComicArchiveException::class.java) { CollectionArchive(maxComics = 1).inspect(zip(root, "1.cbz", "2.cbz")) }
        assertThrows(ComicArchiveException::class.java) { CollectionArchive(maxTotalBytes = 12).inspect(zip(root, "1.cbz", "large.txt")) }
        assertThrows(ComicArchiveException::class.java) { CollectionArchive(maxEntries = 1).inspect(zip(root, "1.cbz", "notes.txt")) }
    }
    @Test fun `reuses actual RAR validation and cancels extraction without publishing`() = inDirectory { root ->
        for (name in listOf("pages-rar4.rar", "pages-rar5.rar")) {
            val file = File(root, name)
            javaClass.getResourceAsStream("/archives/$name")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            assertNull(CollectionArchive().inspect(file))
        }
        val file = zip(root, "1.cbz")
        val reader = CollectionArchive()
        val entry = reader.inspect(file)!!.first()
        Thread.currentThread().interrupt()
        try { assertThrows(Exception::class.java) { reader.extract(file, entry, File(root, "cancelled")) } }
        finally { Thread.interrupted() }
        assertFalse(File(root, "cancelled").exists())
        assertFalse(File(root, "cancelled.part").exists())
    }
    @Test fun `extracts a solid RAR collection in natural order with readable inner editions`() = inDirectory { root ->
        val file = File(root, "collection.rar")
        javaClass.getResourceAsStream("/archives/collection-solid-rar5.rar")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        val reader = CollectionArchive()
        val entries = reader.inspect(file)!!
        assertEquals(listOf("2.cbr", "10.cbr"), entries.map { it.name })
        entries.forEachIndexed { index, entry ->
            val extracted = reader.extract(file, entry, File(root, "inner-$index"))
            assertEquals(listOf("2.png", "10.png"), ComicArchive().index(extracted).map { it.key })
        }
    }
    @Test fun `rejects ZIP links and oversized metadata before opening directory`() = inDirectory { root ->
        val file = zip(root, "1.cbz")
        val original = file.readBytes()
        val central = original.indices.first { i -> i + 3 < original.size && original[i] == 0x50.toByte() &&
            original[i + 1] == 0x4b.toByte() && original[i + 2] == 1.toByte() && original[i + 3] == 2.toByte() }
        val link = original.copyOf()
        link[central + 5] = 3 // Unix host
        link[central + 41] = 0xa0.toByte() // Symlink mode in high 16 bits of external attributes
        file.writeBytes(link)
        assertThrows(ComicArchiveException::class.java) { CollectionArchive().inspect(file) }
        val large = original.copyOf()
        large[large.size - 22 + 10] = 0xff.toByte(); large[large.size - 22 + 11] = 0x7f
        file.writeBytes(large)
        assertThrows(ComicArchiveException::class.java) { CollectionArchive().inspect(file) }
    }
}
