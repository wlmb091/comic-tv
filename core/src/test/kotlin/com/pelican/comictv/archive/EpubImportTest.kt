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

    @Test fun `EPUB permits only font obfuscation and uses native reader fonts`() {
        val root = Files.createTempDirectory("epub-fonts").toFile()
        try {
            val opf = "<package><manifest><item id='a' href='a.xhtml' media-type='application/xhtml+xml'/><item id='f' href='font.otf' media-type='font/otf'/></manifest><spine><itemref idref='a'/></spine></package>"
            for (algorithm in listOf("http://www.idpf.org/2008/embedding", "http://ns.adobe.com/pdf/enc#RC")) {
                val encryption = "<encryption><enc:EncryptedData xmlns:enc='urn:xmlenc'><enc:EncryptionMethod Algorithm='$algorithm'/><enc:CipherData><enc:CipherReference URI='OPS/font.otf'/></enc:CipherData></enc:EncryptedData></encryption>"
                val file = book(root, extra = mapOf("OPS/book.opf" to opf.toByteArray(), "OPS/font.otf" to byteArrayOf(1), "META-INF/encryption.xml" to encryption.toByteArray()))
                val archive = ComicArchive(); val pages = archive.index(file)
                assertEquals(1, pages.size)
                assertTrue(archive.extract(file, pages.single(), File(root, "text-${algorithm.length}")).readText().contains("Última seção"))
            }
            for ((algorithm, path) in listOf("http://www.w3.org/2001/04/xmlenc#aes256-cbc" to "OPS/font.otf", "http://www.idpf.org/2008/embedding" to "OPS/a.xhtml")) {
                val encryption = "<encryption><EncryptedData><EncryptionMethod Algorithm='$algorithm'/><CipherData><CipherReference URI='$path'/></CipherData></EncryptedData></encryption>"
                val file = book(root, extra = mapOf("OPS/book.opf" to opf.toByteArray(), "META-INF/encryption.xml" to encryption.toByteArray()))
                assertThrows(ComicArchiveException::class.java) { ComicArchive().index(file) }
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun `EPUB text tables preserve cell boundaries without interactive content`() {
        val root = Files.createTempDirectory("epub-table").toFile()
        try {
            val file = book(root, "<table><tr><th>Nome</th><th>Valor</th></tr><tr><td>Um</td><td>Dois</td></tr></table><form>Não executar</form>")
            val reader = ComicArchive(); val pages = reader.index(file)
            val text = reader.extract(file, pages.first(), File(root, "table")).readText()
            assertTrue(text.contains("Nome | Valor |")); assertTrue(text.contains("Um | Dois |")); assertFalse(text.contains("Não executar"))
        } finally { root.deleteRecursively() }
    }
}
