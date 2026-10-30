package com.pelican.comictv.archive

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Test

class ArchiveToolsTest {
    @Test fun conversionRejectsBadCrcInsteadOfHidingCorruption() {
        val root=Files.createTempDirectory("crc-tools").toFile()
        try {
            val input=File(root,"crc.cbz"); val payload="unique-page-content".toByteArray()
            ZipOutputStream(input.outputStream()).use { z->
                val entry=ZipEntry("1.jpg").apply { method=ZipEntry.STORED;size=payload.size.toLong();compressedSize=size;crc=java.util.zip.CRC32().apply {update(payload)}.value }
                z.putNextEntry(entry);z.write(payload);z.closeEntry()
            }
            val bytes=input.readBytes();val at=bytes.indices.first {i->i+payload.size<=bytes.size && payload.indices.all {bytes[i+it]==payload[it]} };bytes[at]=0;input.writeBytes(bytes)
            assertThrows(Exception::class.java) {ArchiveTools().convert(input,File(root,"out.cbz"))}
            assertFalse(File(root,"out.cbz").exists())
        } finally {root.deleteRecursively()}
    }
    @Test fun stagingStopsBeforeConsumingDiskReserve() {
        val root=Files.createTempDirectory("space-tools").toFile()
        try {
            val target=File(root,"input")
            assertThrows(Exception::class.java) {copyArchiveToolInput(ByteArray(100).inputStream(),target,1000,available={64L*1024*1024+50})}
            assertFalse(target.exists())
        } finally {root.deleteRecursively()}
    }
    private fun zip(root: File, vararg names: String) = File(root,"input.cbr").also { file ->
        ZipOutputStream(file.outputStream()).use { zip -> names.forEach { name ->
            zip.putNextEntry(ZipEntry(name)); zip.write(name.toByteArray()); zip.closeEntry()
        } }
    }
    @Test fun conversionPreservesPageBytesAndNaturalOrderAndOriginal() {
        val root=Files.createTempDirectory("tools").toFile()
        try {
            val input=zip(root,"page10.jpg","page2.png");val original=input.readBytes()
            val output=ArchiveTools().convert(input,File(root,"out.cbz"))
            ZipFile(output).use { z ->
                val entries=z.entries().toList(); assertEquals(listOf("00001.png","00002.jpg"),entries.map { it.name })
                assertArrayEquals("page2.png".toByteArray(),z.getInputStream(entries[0]).readBytes())
                assertArrayEquals("page10.jpg".toByteArray(),z.getInputStream(entries[1]).readBytes())
            }
            assertArrayEquals(original,input.readBytes())
        } finally {root.deleteRecursively()}
    }
    @Test fun extractionKeepsFoldersButRejectsTraversalAndOverwrite() {
        val root=Files.createTempDirectory("tools").toFile()
        try {
            val a=ArchiveTools();val input=zip(root,"pages/one.jpg","notes.txt")
            val output=a.extract(input,File(root,"result"));assertEquals("notes.txt",File(output,"notes.txt").readText())
            assertTrue(File(output,"pages/one.jpg").isFile)
            assertThrows(Exception::class.java) {a.extract(input,output)}
            zip(root,"../outside.jpg")
            assertThrows(Exception::class.java) {a.extract(input,File(root,"escape"))}
            assertFalse(File(root,"outside.jpg").exists())
        } finally {root.deleteRecursively()}
    }
    @Test fun cancellationLeavesNoResultAndApkIsNotAnArchiveToolInput() {
        val root=Files.createTempDirectory("tools").toFile()
        try {
            val input=zip(root,"1.jpg")
            assertThrows(Exception::class.java) { ArchiveTools(check = {throw java.io.InterruptedIOException()}).convert(input,File(root,"out.cbz")) }
            assertFalse(File(root,"out.cbz").exists())
            zip(root,"AndroidManifest.xml","classes.dex")
            assertThrows(Exception::class.java) {ArchiveTools().list(input)}
        } finally {root.deleteRecursively()}
    }
}
