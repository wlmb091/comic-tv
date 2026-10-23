package com.pelican.comictv.archive

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubImportTest {
    private fun book(root: File, body: String = "<p>Olá leitor! Texto com acentos.</p><img src=\"page.png\"/>", extra: Map<String, ByteArray> = emptyMap()): File {
        val entries = linkedMapOf(
            "mimetype" to "application/epub+zip".toByteArray(),
            "META-INF/container.xml" to "<container><rootfiles><rootfile full-path=\"OPS/book.opf\"/></rootfiles></container>".toByteArray(),
            "OPS/book.opf" to "<package><manifest><item id=\"a\" href=\"a.xhtml\" media-type=\"application/xhtml+xml\"/><item id=\"z\" href=\"z.xhtml\" media-type=\"application/xhtml+xml\"/></manifest><spine><itemref idref=\"z\"/><itemref idref=\"a\"/></spine></package>".toByteArray(),
            "OPS/z.xhtml" to "<html><body>$body</body></html>".toByteArray(),
            "OPS/a.xhtml" to "<html><body><p>Última seção.</p></body></html>".toByteArray(),
            "OPS/page.png" to byteArrayOf(1, 2, 3)
        )
        entries.putAll(extra)
        return File(root, "book.zip").also { file -> ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
        } }
    }
    @Test fun `EPUB follows spine and keeps text and images in content order`() {
        val root = Files.createTempDirectory("epub").toFile()
        try {
            val file = book(root)
            val archive = ComicArchive()
            val pages = archive.index(file)
            assertEquals(3, pages.size)
            val first = archive.extract(file, pages[0], File(root, "first"))
            assertTrue(first.readText().contains("Olá leitor!"))
            assertArrayEquals(byteArrayOf(1, 2, 3), archive.extract(file, pages[1], File(root, "image")).readBytes())
            assertTrue(archive.extract(file, pages[2], File(root, "last")).readText().contains("Última seção"))
        } finally { root.deleteRecursively() }
    }
    @Test fun `EPUB refuses remote and escaping images`() {
        val root = Files.createTempDirectory("epub-invalid").toFile()
        try {
            for (url in listOf("https://example.com/page.png", "../../outside.png", "file:///private.png")) {
                val file = book(root, "<img src=\"$url\"/>")
                assertThrows(ComicArchiveException::class.java) { ComicArchive().index(file) }
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun `EPUB rejects encryption and bounded metadata`() {
        val root = Files.createTempDirectory("epub-limits").toFile()
        try {
            val encrypted = book(root, extra = mapOf("META-INF/encryption.xml" to "<encryption/>".toByteArray()))
            assertThrows(ComicArchiveException::class.java) { ComicArchive().index(encrypted) }
            val huge = book(root, body = "x".repeat(2 * 1024 * 1024 + 1))
            assertThrows(ComicArchiveException::class.java) { ComicArchive().index(huge) }
        } finally { root.deleteRecursively() }
    }

    @Test fun `EPUB keeps long text and ignores scripts without loading external entities`() {
        val root = Files.createTempDirectory("epub-text").toFile()
        try {
            val text = "A".repeat(12_000) + "📚 Fim do capítulo"
            val file = book(root, body = "<script>private script</script><p>$text</p>")
            val archive = ComicArchive()
            val pages = archive.index(file)
            assertEquals(3, pages.size)
            val first = archive.extract(file, pages[0], File(root, "one")).readText()
            val second = archive.extract(file, pages[1], File(root, "two")).readText()
            assertEquals(text, first + second)
            assertFalse(first.contains("private script"))
        } finally { root.deleteRecursively() }
    }
}
