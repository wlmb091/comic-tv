package com.pelican.comictv.archive

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BookFormatsTest {
    private fun zip(root: File, entries: Map<String, String>, binary: Map<String, ByteArray> = emptyMap()): File = File(root, "book.bin").also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            (entries.mapValues { it.value.toByteArray() } + binary).forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
        }
    }
    private fun docx(root: File, body: String, rels: String = "", extras: Map<String, ByteArray> = emptyMap()): File = zip(root, mapOf(
        "[Content_Types].xml" to "<Types/>",
        "word/document.xml" to "<w:document xmlns:w='urn:w' xmlns:a='urn:a' xmlns:r='urn:r'><w:body>$body</w:body></w:document>",
        "word/_rels/document.xml.rels" to "<Relationships>$rels</Relationships>"
    ), extras)
    private fun mobi(root: File, payload: ByteArray = "<p>Olá livro!</p>".toByteArray(), compression: Int = 1, encryption: Int = 0, version: Int = 6, declaredSize: Int = payload.size, extraFlags: Int = 0, image: ByteArray? = null): File {
        val header = ByteBuffer.allocate(248)
        header.putShort(0, compression.toShort()); header.putInt(4, declaredSize); header.putShort(8, 1); header.putShort(10, 4096); header.putShort(12, encryption.toShort())
        header.position(16); header.put("MOBI".toByteArray()); header.putInt(20, 232); header.putInt(28, 65001); header.putInt(36, 6); header.putInt(104, version); header.putInt(108, if (image != null) 2 else -1); header.putShort(242, extraFlags.toShort())
        val records = listOf(header.array(), payload) + listOfNotNull(image)
        val directory = ByteBuffer.allocate(78 + records.size * 8 + 2)
        directory.position(60); directory.put("BOOKMOBI".toByteArray()); directory.putShort(76, records.size.toShort())
        var offset = directory.capacity()
        records.forEachIndexed { index, bytes -> directory.putInt(78 + index * 8, offset); offset += bytes.size }
        return File(root, "disguised.dat").also { file -> file.outputStream().use { out -> out.write(directory.array()); records.forEach(out::write) } }
    }
    private inline fun temp(block: (File) -> Unit) { val root = Files.createTempDirectory("book-formats").toFile(); try { block(root) } finally { root.deleteRecursively() } }
    @Test fun `DOCX text tables and inline pictures follow content order without code`() = temp { root ->
        val file = docx(root, "<w:p><w:r><w:t>Primeiro</w:t><w:instrText>secret field code</w:instrText></w:r></w:p><w:tbl><w:tr><w:tc><w:p><w:r><w:t>Célula A</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Célula B</w:t></w:r></w:p></w:tc></w:tr></w:tbl><w:p><w:r><a:blip r:embed='r1'/><w:t>Depois</w:t></w:r></w:p>", "<Relationship Id='r1' Target='media/p.png' Type='image'/>", mapOf("word/media/p.png" to byteArrayOf(1, 2, 3)))
        val archive = ComicArchive(); val pages = archive.index(file)
        assertEquals(3, pages.size)
        val text = archive.extract(file, pages[0], File(root, "text")).readText()
        assertTrue(text, text.contains("Primeiro")); assertTrue(text, text.contains("Célula A")); assertTrue(text, text.contains("Célula B")); assertTrue(text, text.contains("|")); assertFalse(text.contains("secret"))
        assertArrayEquals(byteArrayOf(1, 2, 3), archive.extract(file, pages[1], File(root, "image")).readBytes())
        assertTrue(archive.extract(file, pages[2], File(root, "last")).readText().contains("Depois"))
    }
    @Test fun `DOCX refuses external and escaping image relationships`() = temp { root ->
        for (rel in listOf("Target='https://example.com/a.png' TargetMode='External'", "Target='../../a.png'")) {
            val file = docx(root, "<a:blip r:embed='r1'/>", "<Relationship Id='r1' $rel/>")
            assertThrows(ComicArchiveException::class.java) { ComicArchive().index(file) }
        }
    }
    @Test fun `DOCX long text uses bounded native sections and rejects forged page references`() = temp { root ->
        val text = "A".repeat(11999) + "📚Fim"
        val file = docx(root, "<w:p><w:r><w:t>$text</w:t></w:r></w:p>")
        val reader = ComicArchive(); val pages = reader.index(file)
        assertEquals(2, pages.size)
        assertEquals(text, pages.mapIndexed { i, page -> reader.extract(file, page, File(root, "part$i")).readText() }.joinToString(""))
        assertThrows(ComicArchiveException::class.java) { reader.extract(file, pages[0].copy(key = "word/document.xml"), File(root, "bad")) }
    }
    @Test fun `MOBI signature selects text reader independent of filename`() = temp { root ->
        val file = mobi(root); val archive = ComicArchive()
        assertEquals(ComicContainer.Mobi, ComicContainers.detect(file))
        val pages = archive.index(file); assertEquals(1, pages.size)
        assertEquals("Olá livro!", archive.extract(file, pages.single(), File(root, "text")).readText())
    }
    @Test fun `MOBI PalmDOC supports overlapping references and high byte spaces`() = temp { root ->
        val file = mobi(root, byteArrayOf(65, 66, 67, 0x80.toByte(), 0x18, 0xc4.toByte()), compression = 2, declaredSize = 8)
        val reader = ComicArchive(); assertEquals("ABCABC D", reader.extract(file, reader.index(file).single(), File(root, "text")).readText())
    }
    @Test fun `MOBI strips trailing entries before decoding and preserves embedded image order`() = temp { root ->
        val markup = "<p>Antes</p><img recindex='1'/><p>Depois</p>".toByteArray()
        val file = mobi(root, markup + byteArrayOf(7, 0x82.toByte()), declaredSize = markup.size, extraFlags = 2, image = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0))
        val reader = ComicArchive(); val pages = reader.index(file); assertEquals(3, pages.size)
        assertEquals("Antes", reader.extract(file, pages[0], File(root, "before")).readText())
        assertArrayEquals(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0), reader.extract(file, pages[1], File(root, "image")).readBytes())
        assertEquals("Depois", reader.extract(file, pages[2], File(root, "after")).readText())
    }
    @Test fun `MOBI refuses DRM KF8 unsupported compression and broken references explicitly`() = temp { root ->
        for (file in listOf(
            mobi(root, encryption = 2).copyTo(File(root, "drm")),
            mobi(root, version = 8).copyTo(File(root, "kf8")),
            mobi(root, compression = 17480).copyTo(File(root, "huff")),
            mobi(root, byteArrayOf(0x80.toByte(), 8), compression = 2).copyTo(File(root, "invalid"))
        )) assertThrows(ComicArchiveException::class.java) { ComicArchive().index(file) }
    }
    @Test fun `MOBI refuses excessive declared text and invalid record offsets`() = temp { root ->
        val huge = mobi(root, declaredSize = BookContent.MAX_TEXT + 1)
        assertThrows(ComicArchiveException::class.java) { ComicArchive().index(huge) }
        val bad = mobi(root); val bytes = bad.readBytes(); ByteBuffer.wrap(bytes).putInt(78, 0); bad.writeBytes(bytes)
        assertThrows(ComicArchiveException::class.java) { ComicArchive().index(bad) }
    }
}
