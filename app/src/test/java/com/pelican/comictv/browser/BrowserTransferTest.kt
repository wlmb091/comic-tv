package com.pelican.comictv.browser

import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class BrowserTransferTest {
    @Test fun responseFilenameSupportsUtf8AndGenericUrlsWithoutEnablingImportOfExecutables() {
        assertEquals("Minha HQ.cbz", BrowserDownloadNames.resolve("https://example.org/api/download", "attachment; filename*=UTF-8''Minha%20HQ.cbz", "application/octet-stream", "download.bin"))
        assertEquals("quadrinho.pdf", BrowserDownloadNames.resolve("https://example.org/api", null, "application/pdf", "quadrinho"))
        assertEquals("arquivo.apk", BrowserDownloadNames.safe("../../arquivo.apk"))
        assertFalse(BrowserDownloadNames.importable("arquivo.apk"))
        rejected { BrowserDownloadNames.validate("arquivo.apk", "application/octet-stream") }
        rejected { BrowserDownloadNames.validate("arquivo.bin", "application/vnd.android.package-archive") }
        assertTrue(BrowserDownloadNames.importable("HQ.CBZ"))
    }
    @Test fun refererIsFullOnlyOnSameOriginAndNeverLeaksQueryAcrossHosts() {
        assertEquals("https://example.org/read?id=7", browserDownloadReferer("https://example.org/read?id=7#x", "https://example.org/file"))
        assertEquals("https://example.org/", browserDownloadReferer("https://example.org/read?private=1", "https://cdn.example.org/file"))
        assertNull(browserDownloadReferer("https://example.org/read", "http://example.org/file"))
    }
    @Test fun genericDownloadUsesResponseNameAndPageContext() = serve { base, target, requests ->
        val result = BrowserDownloader().transfer(BrowserDownloadRequest("$base/api", "download.bin", "Agent", "$base/read?id=7"), target,
            { "session=local" }, {}, { _, _ -> }, {})
        assertEquals("HQ.cbz", result.filename); assertEquals("abcdef", target.readText())
        assertTrue(requests.single().contains("Referer: $base/read?id=7"))
        assertTrue(requests.single().contains("Cookie: session=local"))
    }
    @Test fun partialResumesOnlyWithValidatorAndExactContentRange() = serve(resume = true) { base, target, requests ->
        target.writeText("abc")
        val result = BrowserDownloader().transfer(BrowserDownloadRequest("$base/file", "HQ.cbz", "Agent", validator = "\"v1\""), target,
            { null }, {}, { _, _ -> }, {})
        assertEquals("abcdef", target.readText()); assertEquals(6L, result.totalBytes)
        assertTrue(requests.single().contains("Range: bytes=3-")); assertTrue(requests.single().contains("If-Range: \"v1\""))
    }
    @Test fun serverIgnoringRangeRestartsInsteadOfAppendingDuplicateBytes() = serve { base, target, _ ->
        target.writeText("old")
        BrowserDownloader().transfer(BrowserDownloadRequest("$base/file", "HQ.cbz", "Agent", validator = "\"old\""), target,
            { null }, {}, { _, _ -> }, {})
        assertEquals("abcdef", target.readText())
    }
    @Test fun htmlErrorPageIsNotReportedAsACompletedComicDownload() = serve(html = true) { base, target, _ ->
        try {
            BrowserDownloader().transfer(BrowserDownloadRequest("$base/file", "HQ.pdf", "Agent"), target,
                { null }, {}, { _, _ -> }, {})
            fail("HTML login must fail")
        } catch (_: IOException) { }
        assertFalse(target.exists())
    }
    private fun rejected(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IOException) {} }
    private fun serve(resume: Boolean = false, html: Boolean = false,
        block: (String, File, MutableList<String>) -> Unit) {
        val server = ServerSocket(0); val requests = mutableListOf<String>()
        val worker = thread {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader(); val lines = mutableListOf<String>()
                while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break; lines += line }
                requests += lines.joinToString("\n")
                val status = if (resume) "206 Partial Content" else "200 OK"
                val body = if (resume) "def" else if (html) "<html>login</html>" else "abcdef"
                val type = if (html) "text/html" else "application/octet-stream"
                val disposition = if (html) "" else "Content-Disposition: attachment; filename=HQ.cbz\r\n"
                val range = if (resume) "Content-Range: bytes 3-5/6\r\nETag: \"v1\"\r\n" else ""
                socket.getOutputStream().write(("HTTP/1.1 $status\r\nContent-Length: ${body.length}\r\nContent-Type: $type\r\n" + disposition + range + "Connection: close\r\n\r\n" + body).toByteArray())
            }
        }
        val dir = Files.createTempDirectory("transfer-test").toFile(); val target = File(dir, "data.part")
        try { block("http://127.0.0.1:${server.localPort}", target, requests) }
        finally { server.close(); worker.join(2000); target.delete(); dir.delete() }
    }
}
