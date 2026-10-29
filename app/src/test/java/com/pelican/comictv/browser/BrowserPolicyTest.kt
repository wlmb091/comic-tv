package com.pelican.comictv.browser

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.concurrent.thread

class BrowserPolicyTest {
    private fun rejected(block: () -> Unit) { try { block(); fail("Should reject") } catch (_: IOException) {} }
    @Test fun schemesCredentialsAndApksAreRejected() {
        listOf("file:///tmp/x", "data:text/plain,a", "blob:https://example.org/a", "intent://a", "https://user:pass@example.org/").forEach {
            rejected { BrowserPolicy.url(it) }
        }
        rejected { BrowserPolicy.filename("book.apk") }
        assertEquals("book.CBZ", BrowserPolicy.filename("../../book.CBZ"))
        assertEquals("book.pdf", BrowserPolicy.filename("C:\\folder\\book.pdf"))
    }
    @Test fun redirectsAndBoundsAreStrict() {
        val origin = BrowserPolicy.url("https://example.org/book")
        assertEquals("https://example.org/next", BrowserPolicy.redirect(origin, "/next", 4).toString())
        rejected { BrowserPolicy.redirect(origin, "http://example.org/next", 0) }
        rejected { BrowserPolicy.redirect(origin, "/next", 5) }
        rejected { BrowserPolicy.checkSize(BrowserPolicy.MAX_BYTES + 1, Long.MAX_VALUE) }
        rejected { BrowserPolicy.checkSize(1, BrowserPolicy.RESERVE - 1) }
        BrowserPolicy.checkSize(BrowserPolicy.MAX_BYTES, BrowserPolicy.RESERVE)
    }
    @Test fun downloaderUsesDestinationCookieAndDeletesFailedPartial() {
        val server = ServerSocket(0)
        val worker = thread {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                while (!reader.readLine().isNullOrEmpty()) {}
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 10\r\nConnection: close\r\n\r\nabc".toByteArray())
            }
        }
        val directory = Files.createTempDirectory("browser-test").toFile()
        val target = directory.resolve("partial")
        val requested = mutableListOf<String>()
        try {
            rejected { BrowserDownloader().download("http://127.0.0.1:${server.localPort}/book.cbz", target,
                { requested += it; null }, "Test", {}, { _, _ -> }) }
            assertEquals(1, requested.size)
            assertFalse(target.exists())
        } finally { server.close(); worker.join(2000); target.delete(); directory.delete() }
    }
}
