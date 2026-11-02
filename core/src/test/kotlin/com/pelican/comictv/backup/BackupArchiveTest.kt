package com.pelican.comictv.backup

import com.pelican.comictv.reader.PageDrawingStore
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
    private val adjust = "1:" + List(34) { if (it == 3) 25 else 0 }.joinToString(",")
    private val document = BackupDocument(createdAt = 5, comics = listOf(BackupComic("a-1", "Título", 3, sha256 = "a".repeat(64), imageAdjustments = adjust)))
    @Test fun carriesDrawingsAndTheDocument() {
        val root = Files.createTempDirectory("archive").toFile()
        try {
            val source = PageDrawingStore(java.io.File(root, "from"))
            source.save("a-1", 1, 10, 20, byteArrayOf(7, 8))
            source.saveLayers("a-1", 1, byteArrayOf(1, 2, 3).inputStream())
            val excerpt = source.addExcerpt("a-1", 2, 0, 5, byteArrayOf(4))
            source.save("a-1", excerpt.page, 10, 20, byteArrayOf(6))
            val file = java.io.File(root, "backup")
            file.outputStream().use { BackupArchive.write(it, BackupCodec.encode(document), source.backupFiles()) }
            assertTrue(BackupArchive.isArchive(file))
            assertEquals(adjust, BackupArchive.document(file).comics.single().imageAdjustments)
            assertEquals(2, BackupArchive.drawingCount(file))
            val target = PageDrawingStore(java.io.File(root, "to"))
            BackupArchive.restoreDrawings(file, target)
            assertArrayEquals(byteArrayOf(7, 8), target.file("a-1", 1)!!.readBytes())
            assertEquals(10 to 20, target.pageSize("a-1", 1))
            assertArrayEquals(byteArrayOf(1, 2, 3), target.layers("a-1", 1)!!.readBytes())
            assertEquals(listOf(excerpt), target.excerpts("a-1", 2))
            assertArrayEquals(byteArrayOf(4), target.excerptBase("a-1", excerpt.page)!!.readBytes())
        } finally { root.deleteRecursively() }
    }
    @Test fun plainJsonStillOpens() {
        val root = Files.createTempDirectory("archive").toFile()
        try {
            val file = java.io.File(root, "backup").apply { writeBytes(BackupCodec.encode(document)) }
            assertFalse(BackupArchive.isArchive(file))
            assertEquals("a-1", BackupArchive.document(file).comics.single().id)
            assertEquals(0, BackupArchive.drawingCount(file))
            BackupArchive.restoreDrawings(file, PageDrawingStore(java.io.File(root, "to")))
            assertNull(BackupCodec.decode(BackupCodec.encode(document.copy(comics = listOf(document.comics.single().copy(imageAdjustments = null)))).inputStream()).comics.single().imageAdjustments)
        } finally { root.deleteRecursively() }
    }
    @Test fun refusesWhatIsNotADrawing() {
        val root = Files.createTempDirectory("archive").toFile()
        try {
            for (name in listOf("drawings/../../x.png", "other.txt", "drawings/a-1/1.exe")) {
                val file = java.io.File(root, "evil")
                ZipOutputStream(file.outputStream()).use { zip ->
                    zip.putNextEntry(ZipEntry("backup.json")); zip.write(BackupCodec.encode(document)); zip.closeEntry()
                    zip.putNextEntry(ZipEntry(name)); zip.write(byteArrayOf(1)); zip.closeEntry()
                }
                val target = java.io.File(root, "to")
                assertTrue(name, runCatching { BackupArchive.restoreDrawings(file, PageDrawingStore(target)) }.isFailure)
                assertFalse(java.io.File(root, "x.png").exists())
            }
            assertTrue(runCatching { BackupCodec.encode(document.copy(comics = listOf(document.comics.single().copy(imageAdjustments = "lixo")))) }.isFailure)
        } finally { root.deleteRecursively() }
    }
}
