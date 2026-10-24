package com.pelican.comictv.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files

class BackupTransferServerTest {
    private fun request(url: String, body: ByteArray? = null): Int {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 2000; connection.readTimeout = 5000
        return try {
            if (body != null) { connection.requestMethod = "POST"; connection.doOutput = true; connection.setFixedLengthStreamingMode(body.size); connection.outputStream.use { it.write(body) } }
            connection.responseCode
        } finally { connection.disconnect() }
    }
    @Test fun uploadOnlyPreviewsAndRejectsInvalidBackupTokenAndExpiration() {
        val root = Files.createTempDirectory("backup-http").toFile()
        val bytes = BackupCodec.encode(BackupDocument(createdAt = 0, comics = emptyList()))
        val export = File(root, "prepared").apply { writeBytes(bytes) }
        var now = 0L; var previews = 0
        val server = BackupTransferServer("127.0.0.1", File(root,"uploads"), { export }, { previews++ }, clock = { now })
        try {
            server.open()
            assertEquals(200, request(server.url)); assertEquals(200, request(server.url + "/export"))
            assertEquals(404, request("http://127.0.0.1:${server.port}/wrong/export"))
            assertEquals(200, request(server.url + "/receive", bytes)); assertEquals(1, previews)
            assertEquals(422, request(server.url + "/receive", "{}".toByteArray())); assertEquals(1, previews)
            now = 15 * 60 * 1000L
            assertEquals(410, request(server.url))
            assertTrue(File(root,"uploads").listFiles().orEmpty().isEmpty())
        } finally { server.close(); root.deleteRecursively() }
    }
}
