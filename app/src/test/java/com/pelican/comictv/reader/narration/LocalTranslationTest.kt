package com.pelican.comictv.reader.narration

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class LocalTranslationTest {
    @Test fun refusesRedirectsAndOversizedResponsesAndCancelsStalledRequests() = runBlocking {
        for (mode in listOf("redirect", "oversized", "stall")) {
            val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
            val accepted = CompletableDeferred<Unit>()
            val worker = thread(isDaemon = true) {
                try { server.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    input.readLine()
                    while (!input.readLine().isNullOrEmpty()) { }
                    accepted.complete(Unit)
                    val response = when (mode) {
                        "redirect" -> "HTTP/1.1 302 Found\r\nLocation: http://example.com/translate\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        "oversized" -> "HTTP/1.1 200 OK\r\nContent-Length: 65537\r\nConnection: close\r\n\r\n" + "a".repeat(65537)
                        else -> null
                    }
                    if (response != null) { socket.getOutputStream().write(response.toByteArray()); socket.getOutputStream().flush() }
                    else { socket.soTimeout = 3000; while (socket.getInputStream().read() >= 0) { } }
                } } catch (_: java.io.IOException) { }
            }
            val client = LocalTranslationClient("http://127.0.0.1:${server.localPort}", "en")
            try {
                if (mode == "stall") {
                    val request = async { client.translate("Hello") }
                    withTimeout(3000) { accepted.await() }
                    request.cancelAndJoin(); assertTrue(request.isCancelled)
                } else try { client.translate("Hello"); fail("Unsafe response accepted") }
                    catch (e: java.io.IOException) { assertTrue(e.message.orEmpty().contains(if (mode == "redirect") "302" else "limite")) }
            } finally { client.close(); server.close(); worker.join(3500) }
        }
    }
    @Test fun onlyLocalAddressesAreAllowedAndRedirectsNeverForwardBookText() = runBlocking {
        for (url in listOf("https://example.com", "http://8.8.8.8", "http://192.168.1.1@evil.com", "file:///tmp", "http://192.168.1.1/?q=key", "http://192.168.a.1.2")) {
            assertThrows(IllegalArgumentException::class.java) { LocalTranslationClient(url, "en") }
        }
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val worker = thread(isDaemon = true) {
            server.accept().use { socket ->
                val input = socket.getInputStream().bufferedReader()
                val request = input.readLine()
                assertTrue(request.startsWith("POST /translate"))
                while (!input.readLine().isNullOrEmpty()) { }
                val body = "{\"translatedText\":\"Olá, mundo.\"}".toByteArray(Charsets.UTF_8)
                socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n".toByteArray()); write(body); flush() }
            }
        }
        val client = LocalTranslationClient("http://127.0.0.1:${server.localPort}", "en")
        try { assertEquals("Olá, mundo.", client.translate("Hello, world.")) }
        finally { client.close(); server.close(); worker.join(2000) }
    }
    @Test fun translatedSourceKeepsOriginalAndChangesIdentityWithoutAutodownload() = runBlocking {
        var calls = 0
        val original = object : NarrationSource {
            override val count = 1
            override val label = "Página"
            override suspend fun text(page: Int) = "Hello."
            override suspend fun close() = Unit
        }
        val source = TranslatedNarrationSource(original)
        source.configure(object : PageTranslation {
            override suspend fun translate(text: String): String { calls++; return "Olá." }
            override fun close() = Unit
        }, "server-en")
        assertEquals("Hello.", source.text(0)); assertEquals(0, calls)
        source.useTranslation(true)
        assertNotEquals("original", source.variant)
        assertEquals("Olá.", source.text(0)); assertEquals("Olá.", source.text(0)); assertEquals(1, calls)
        source.useTranslation(false)
        assertEquals("Hello.", source.text(0))
        source.close()
    }
}
