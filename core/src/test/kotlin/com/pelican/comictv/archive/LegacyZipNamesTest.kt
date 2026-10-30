package com.pelican.comictv.archive

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LegacyZipNamesTest {
    private fun fixture(root: File, charset: Charset, name: String): File = File(root, "comic.cbz").also { file ->
        ZipOutputStream(file.outputStream(), charset).use { out ->
            out.putNextEntry(ZipEntry(name)); out.write(byteArrayOf(1, 2, 3)); out.closeEntry()
        }
    }
    @Test fun legacyAccentedNamesPassCollectionInspectionDispatchAndExtraction() {
        val root = Files.createTempDirectory("legacy-zip").toFile()
        try {
            val file = fixture(root, Charset.forName("IBM437"), "O Máskara_00.jpg")
            assertNull(CollectionArchive().inspect(file))
            val archive = ComicArchive()
            val page = archive.index(file).single()
            assertEquals("O Máskara_00.jpg", page.key)
            assertArrayEquals(byteArrayOf(1,2,3), archive.extract(file, page, File(root,"page")).readBytes())
        } finally { root.deleteRecursively() }
    }
    @Test fun utf8NamesAndLegacyCollectionsPreserveExactKeys() {
        val root = Files.createTempDirectory("legacy-zip").toFile()
        try {
            val file = fixture(root, Charsets.UTF_8, "日本語 Máskara.jpg")
            assertEquals("日本語 Máskara.jpg", ComicArchive().index(file).single().key)
            fixture(root, Charset.forName("IBM437"), "Máskara 01.cbz")
            val entry = CollectionArchive().inspect(file)!!.single()
            assertEquals("Máskara 01.cbz", entry.name)
            assertArrayEquals(byteArrayOf(1,2,3),CollectionArchive().extract(file,entry,File(root,"edition")).readBytes())
        } finally { root.deleteRecursively() }
    }
    @Test fun legacyTraversalIsStillRejected() {
        val root = Files.createTempDirectory("legacy-zip").toFile()
        try {
            val file = fixture(root, Charset.forName("IBM437"), "../Máskara.jpg")
            assertThrows(ComicArchiveException::class.java) { ComicArchive().index(file) }
            assertThrows(ComicArchiveException::class.java) { CollectionArchive().inspect(file) }
        } finally { root.deleteRecursively() }
    }
    @Test fun incorrectlyFlaggedUtf8NamesExplainTheProblemInsteadOfMalformed() {
        val root=Files.createTempDirectory("invalid-name").toFile()
        try {
            val f=fixture(root,Charset.forName("IBM437"),"Máskara.jpg")
            val bytes=f.readBytes()
            bytes[7]=(bytes[7].toInt() or 8).toByte()
            val central=bytes.indices.first { it+3<bytes.size && bytes[it]==80.toByte() && bytes[it+1]==75.toByte() && bytes[it+2]==1.toByte() && bytes[it+3]==2.toByte() }
            bytes[central+9]=(bytes[central+9].toInt() or 8).toByte();f.writeBytes(bytes)
            val e=assertThrows(ComicArchiveException::class.java) { CollectionArchive().inspect(f) }
            assertTrue(e.message.orEmpty(),e.message.orEmpty().contains("nomes"))
        } finally { root.deleteRecursively() }
    }

}
