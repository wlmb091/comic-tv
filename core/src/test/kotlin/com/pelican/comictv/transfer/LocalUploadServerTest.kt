package com.pelican.comictv.transfer

import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LocalUploadServerTest {
    @Test fun `accepts rar and cbr filenames for the shared importer`() {
        val root = Files.createTempDirectory("rar-upload").toFile()
        val received = mutableListOf<String>()
        val server = LocalUploadServer("127.0.0.1", root, onReceived = { _, name -> received.add(name) })
        try {
            server.open()
            for (name in listOf("Comic.rar", "Comic.CBR", "Book.PDF", "Book.epub")) {
                assertEquals(200, request(server.url + "/upload?name=$name", byteArrayOf(1)).first)
            }
            assertEquals(listOf("Comic.rar", "Comic.CBR", "Book.PDF", "Book.epub"), received)
        } finally { server.close(); root.deleteRecursively() }
    }
    private fun request(url: String, bytes: ByteArray? = null): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 2_000; connection.readTimeout = 5_000
        try {
            if (bytes != null) {
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.setRequestProperty("Content-Type", "application/octet-stream")
                connection.outputStream.use { it.write(bytes) }
            }
            val code = connection.responseCode
            return code to (if (code >= 400) connection.errorStream else connection.inputStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally { connection.disconnect() }
    }

    @Test fun `serves upload page only for session token`() {
        val root = Files.createTempDirectory("http").toFile()
        val server = LocalUploadServer("127.0.0.1", root, onReceived = { _, _ -> })
        try {
            server.open()
            assertEquals(200, request(server.url).first)
            assertTrue(request(server.url).second.contains("Enviar quadrinhos"))
            assertEquals(404, request("http://127.0.0.1:${server.port}/invalid").first)
        } finally { server.close(); root.deleteRecursively() }
    }
    @Test fun `streams exact file then removes temporary upload`() {
        val root = Files.createTempDirectory("upload").toFile()
        val expected = ByteArray(65_537) { (it % 251).toByte() }
        var received: ByteArray? = null
        var filename = ""
        val server = LocalUploadServer("127.0.0.1", root, onReceived = { file, name -> received = file.readBytes(); filename = name })
        try {
            server.open()
            assertEquals(200, request(server.url + "/upload?name=Minha%20HQ.cbz", expected).first)
            assertArrayEquals(expected, received)
            assertEquals("Minha HQ.cbz", filename)
            assertTrue(root.listFiles().orEmpty().isEmpty())
            assertEquals(0, server.status.activeUploads)
            assertEquals(1, server.status.completed)
        } finally { server.close(); root.deleteRecursively() }
    }

    @Test fun `archive validation reason reaches browser and TV`() {
        val root = Files.createTempDirectory("reason").toFile()
        val reason = "Nenhuma página JPEG, PNG ou WebP foi encontrada."
        val server = LocalUploadServer("127.0.0.1", root, onReceived = { _, _ ->
            throw com.pelican.comictv.archive.ComicArchiveException(reason)
        })
        try {
            server.open()
            val result = request(server.url + "/upload?name=Thanos.zip", byteArrayOf(1))
            assertEquals(422, result.first)
            assertTrue(result.second.contains(reason))
            assertTrue(server.status.message.contains(reason))
        } finally { server.close(); root.deleteRecursively() }
    }

    @Test fun `unexpected exceptions do not disclose internal paths to browser`() {
        val root = Files.createTempDirectory("private-error").toFile()
        val server = LocalUploadServer("127.0.0.1", root, onReceived = { _, _ ->
            throw IllegalStateException("/data/user/0/private/database-secret")
        })
        try {
            server.open()
            val result = request(server.url + "/upload?name=Thanos.cbz", byteArrayOf(1))
            assertEquals(422, result.first)
            assertFalse(result.second.contains("database-secret"))
            assertFalse(server.status.message.contains("database-secret"))
        } finally { server.close(); root.deleteRecursively() }
    }
    @Test fun `rejects excess and invalid content without orphan files`() {
        val root = Files.createTempDirectory("limits").toFile()
        val server = LocalUploadServer("127.0.0.1", root, maxBytes = 8, onReceived = { _, _ -> throw java.io.IOException("Quadrinho inválido") })
        try {
            server.open()
            assertEquals(413, request(server.url + "/upload?name=test.cbz", ByteArray(9)).first)
            assertEquals(422, request(server.url + "/upload?name=test.cbz", ByteArray(8)).first)
            assertTrue(root.listFiles().orEmpty().isEmpty())
        } finally { server.close(); root.deleteRecursively() }
    }
    @Test fun `interrupted upload is removed and a later upload works`() {
        val root = Files.createTempDirectory("interrupt").toFile()
        val failed = CountDownLatch(1)
        val server = LocalUploadServer("127.0.0.1", root, onReceived = { _, _ -> },
            onStatus = { if (it.message.contains("incompleto")) failed.countDown() })
        try {
            server.open()
            Socket("127.0.0.1", server.port).use { socket ->
                socket.getOutputStream().write(("POST ${URL(server.url).path}/upload?name=a.cbz HTTP/1.1\r\nHost: 127.0.0.1:${server.port}\r\nContent-Length: 100\r\n\r\na").toByteArray())
            }
            assertTrue(failed.await(5, TimeUnit.SECONDS))
            assertEquals(200, request(server.url + "/upload?name=a.cbz", byteArrayOf(1)).first)
            assertTrue(root.listFiles().orEmpty().isEmpty())
        } finally { server.close(); root.deleteRecursively() }
    }
    @Test fun `expiration rejects uploads and close shuts listener`() {
        val root = Files.createTempDirectory("expiry").toFile()
        var time = 0L
        val server = LocalUploadServer("127.0.0.1", root, clock = { time }, lifetimeMillis = 10, onReceived = { _, _ -> })
        try {
            server.open(); val url = server.url
            time = 11
            assertEquals(410, request(url).first)
            server.close()
            assertThrows(java.io.IOException::class.java) { request(url) }
        } finally { server.close(); root.deleteRecursively() }
    }

    @Test fun `closed session cannot be reopened`() {
        val root = Files.createTempDirectory("closed").toFile()
        val server = LocalUploadServer("127.0.0.1", root, onReceived = { _, _ -> })
        try {
            server.close()
            assertThrows(IllegalStateException::class.java) { server.open() }
        } finally { server.close(); root.deleteRecursively() }
    }

    @Test fun `new session removes abandoned uploads but preserves unrelated files`() {
        val root = Files.createTempDirectory("abandoned").toFile()
        val stale = java.io.File(root, "stale.upload").apply { writeBytes(ByteArray(128)) }
        val unrelated = java.io.File(root, "keep.txt").apply { writeText("keep") }
        val server = LocalUploadServer("127.0.0.1", root, onReceived = { _, _ -> })
        try {
            server.open()
            assertFalse(stale.exists()); assertTrue(unrelated.exists())
            assertEquals(200, request(server.url + "/upload?name=new.cbz", byteArrayOf(1)).first)
        } finally { server.close(); root.deleteRecursively() }
    }

    @Test fun `rejects cross origin host rebinding and unsafe filenames`() {
        val root = Files.createTempDirectory("origin").toFile()
        val server = LocalUploadServer("127.0.0.1", root, onReceived = { _, _ -> fail("Must reject") })
        try {
            server.open()
            Socket("127.0.0.1", server.port).use { socket ->
                socket.soTimeout = 2_000
                socket.getOutputStream().write(("GET ${URL(server.url).path} HTTP/1.1\r\nHost: 127.0.0.1:${server.port}\r\nOrigin: https://unrelated.example\r\n\r\n").toByteArray())
                assertTrue(socket.getInputStream().bufferedReader().readLine().contains("403"))
            }
            assertEquals(400, request(server.url + "/upload?name=..%2Fbad.cbz", byteArrayOf(1)).first)
            Socket("127.0.0.1", server.port).use { socket ->
                socket.soTimeout = 2_000
                socket.getOutputStream().write(("GET ${URL(server.url).path} HTTP/1.1\r\nHost: unrelated.example\r\n\r\n").toByteArray())
                assertTrue(socket.getInputStream().bufferedReader().readLine().contains("403"))
            }
        } finally { server.close(); root.deleteRecursively() }
    }

    @Test fun `concurrent upload is refused without affecting active upload`() {
        val root = Files.createTempDirectory("concurrent").toFile()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val server = LocalUploadServer("127.0.0.1", root, onReceived = { _, _ ->
            entered.countDown(); check(release.await(5, TimeUnit.SECONDS))
        })
        try {
            server.open()
            val first = executor.submit<Pair<Int, String>> { request(server.url + "/upload?name=first.cbz", byteArrayOf(1)) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(409, request(server.url + "/upload?name=second.cbz", byteArrayOf(2)).first)
            release.countDown()
            assertEquals(200, first.get(5, TimeUnit.SECONDS).first)
            assertEquals(1, server.status.completed)
        } finally { release.countDown(); server.close(); executor.shutdownNow(); root.deleteRecursively() }
    }

    @Test fun `stop cancels incomplete socket upload and cleans temporary file`() {
        val root = Files.createTempDirectory("stop").toFile()
        val started = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val server = LocalUploadServer("127.0.0.1", root, onReceived = { _, _ -> fail("Incomplete upload cannot commit") },
            onStatus = { if (it.activeUploads == 1) started.countDown(); if (it.message.contains("incompleto")) ended.countDown() })
        try {
            server.open()
            Socket("127.0.0.1", server.port).use { socket ->
                socket.getOutputStream().write(("POST ${URL(server.url).path}/upload?name=a.cbz HTTP/1.1\r\nHost: 127.0.0.1:${server.port}\r\nContent-Length: 100\r\n\r\na").toByteArray())
                assertTrue(started.await(5, TimeUnit.SECONDS))
                server.close()
                assertTrue(ended.await(5, TimeUnit.SECONDS))
            }
            // Wait for the request's finally to finish, not an arbitrary sleep.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (server.status.activeUploads != 0 && System.nanoTime() < deadline) Thread.yield()
            assertEquals(0, server.status.activeUploads)
            assertTrue(root.listFiles().orEmpty().isEmpty())
        } finally { server.close(); root.deleteRecursively() }
    }
}
