package com.pelican.comictv.browser

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class BrowserBlobFileTest {
    @Test fun capturedNameOverridesTemporaryUuidAndKeepsUnicode() {
        assertEquals("Godzilla — edição 01.cbr", BrowserBlobFile.name("Godzilla — edição 01.cbr", "uuid.bin", "application/octet-stream"))
        assertEquals("uuid.bin", BrowserBlobFile.name(null, "uuid.bin", "application/octet-stream"))
        rejected { BrowserBlobFile.name("instalador.apk", "uuid.bin", "application/octet-stream") }
    }
    @Test fun rarSignatureRecoversGenericNameAndPreservesCorrectCbr() = temporary { file ->
        file.writeBytes(byteArrayOf(82, 97, 114, 33, 26, 7, 0, 0))
        assertEquals("uuid.cbr", BrowserBlobFile.confirm(file, "uuid.bin"))
        assertEquals("Minha HQ.cbr", BrowserBlobFile.confirm(file, "Minha HQ.cbr"))
    }
    @Test fun zipIsNotBlindlyRenamedToCbrAndApkIsRejectedEvenWithComicName() = temporary { file ->
        ZipOutputStream(file.outputStream()).use { zip -> zip.putNextEntry(ZipEntry("001.jpg")); zip.write(byteArrayOf(1)); zip.closeEntry() }
        assertEquals("uuid.zip", BrowserBlobFile.confirm(file, "uuid.bin"))
        assertEquals("HQ.cbz", BrowserBlobFile.confirm(file, "HQ.cbz"))
        ZipOutputStream(file.outputStream()).use { zip -> zip.putNextEntry(ZipEntry("AndroidManifest.xml")); zip.write(byteArrayOf(1)); zip.closeEntry() }
        rejected { BrowserBlobFile.confirm(file, "HQ.cbr") }
    }
    @Test fun htmlOrUnknownPayloadCannotBecomeAnImportableComic() = temporary { file ->
        file.writeText("<!doctype html><title>Entrar</title>")
        rejected { BrowserBlobFile.confirm(file, "HQ.cbr") }
        file.writeText("unrecognized bytes")
        rejected { BrowserBlobFile.confirm(file, "HQ.cbr") }
        assertEquals("uuid.bin", BrowserBlobFile.confirm(file, "uuid.bin"))
    }
    @Test fun sevenZipIsRecognizedByBytesAndKeepsApkBlocking() = temporary { file ->
        javaClass.getResourceAsStream("/browser/pages.7z")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        assertEquals("HQ.7z",BrowserBlobFile.confirm(file,"HQ.cbr"))
        assertEquals("HQ.cb7",BrowserBlobFile.confirm(file,"HQ.cb7"))
        assertTrue(BrowserDownloadNames.importable("HQ.7z"))
        assertTrue(BrowserDownloadNames.importable("HQ.cb7"))
        javaClass.getResourceAsStream("/browser/apk.7z")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        rejected { BrowserBlobFile.confirm(file,"HQ.7z") }
    }
    private fun temporary(block: (File) -> Unit) {
        val path = Files.createTempFile("blob-format", ".data").toFile()
        try { block(path) } finally { path.delete() }
    }
    private fun rejected(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IOException) {} }
}
