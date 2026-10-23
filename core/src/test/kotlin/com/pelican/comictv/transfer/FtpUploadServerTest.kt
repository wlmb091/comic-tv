package com.pelican.comictv.transfer

import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.io.File
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FtpUploadServerTest {
    private class Client(port: Int) : Closeable {
        val socket = Socket("127.0.0.1", port).apply { soTimeout = 3000 }
        private val reader = socket.getInputStream().bufferedReader()

        init { assertEquals(220, reply()) }

        fun reply(): Int {
            val first = reader.readLine() ?: throw java.io.EOFException("FTP closed")
            val code = first.take(3).toInt()
            if (first.getOrNull(3) == '-') {
                while (!reader.readLine().startsWith("$code ")) { /* Multiline feature reply. */ }
            }
            return code
        }

        fun command(value: String): Int {
            socket.getOutputStream().write("$value\r\n".toByteArray())
            return reply()
        }

        fun login(server: FtpUploadServer) {
            assertEquals(331, command("USER ${server.username}"))
            assertEquals(230, command("PASS ${server.password}"))
        }

        fun pasvData(): Socket {
            socket.getOutputStream().write("PASV\r\n".toByteArray())
            val line = reader.readLine()
            assertTrue(line.startsWith("227"))
            val numbers = line.substringAfter('(').substringBefore(')').split(',')
            val port = numbers[4].toInt() * 256 + numbers[5].toInt()
            return Socket("127.0.0.1", port)
        }
        fun data(): Socket {
            socket.getOutputStream().write("EPSV\r\n".toByteArray())
            val line = reader.readLine()
            assertTrue(line.startsWith("229"))
            val port = line.substringAfter("|||").substringBefore('|').toInt()
            return Socket("127.0.0.1", port).apply { soTimeout = 3000 }
        }

        fun start(name: String) {
            socket.getOutputStream().write("STOR $name\r\n".toByteArray())
            assertEquals(150, reply())
        }

        fun upload(name: String, bytes: ByteArray): Int {
            data().use {
                start(name)
                it.getOutputStream().write(bytes)
            }
            return reply()
        }

        override fun close() = socket.close()
    }

    private fun fixture(
        maxBytes: Long = 1024,
        receive: (File, String) -> Unit = { _, _ -> },
        block: (FtpUploadServer, File) -> Unit,
    ) {
        val dir = Files.createTempDirectory("ftp-test").toFile()
        val server = FtpUploadServer("127.0.0.1", dir, maxBytes = maxBytes, onReceived = receive)
        try {
            server.open()
            block(server, dir)
        } finally {
            server.close()
            dir.deleteRecursively()
        }
    }

    @Test fun `authenticated passive upload streams exact bytes and cleans temporary file`() {
        val bytes = ByteArray(777) { it.toByte() }
        var actual: ByteArray? = null
        var name = ""
        fixture(receive = { file, filename ->
            actual = file.readBytes()
            name = filename
        }) { server, dir ->
            assertTrue(server.url.contains("${server.username}:${server.password}@"))
            Client(server.port).use { client ->
                client.login(server)
                assertEquals(226, client.upload("Minha HQ.CBZ", bytes))
            }
            assertArrayEquals(bytes, actual)
            assertEquals("Minha HQ.CBZ", name)
            assertEquals(1, server.status.completed)
            assertTrue(dir.listFiles()!!.isEmpty())
        }
    }

    @Test fun `requires credentials and refuses filesystem and active commands`() = fixture { server, _ ->
        Client(server.port).use { client ->
            assertEquals(530, client.command("EPSV"))
            assertEquals(331, client.command("USER comictv"))
            assertEquals(530, client.command("PASS wrong"))
            client.login(server)
            val forbidden = listOf(
                "RETR book.cbz", "DELE book.cbz", "PORT 127,0,0,1,1,1", "APPE a.cbz",
                "REST 1", "CWD ..", "STOR ../bad.cbz", "STOR bad.exe",
            )
            for (command in forbidden) assertTrue(command, client.command(command) >= 500)
            assertEquals(257, client.command("PWD"))
            assertEquals(200, client.command("TYPE I"))
        }
    }

    @Test fun `listing is empty and file size limit prevents import`() {
        var imports = 0
        fixture(maxBytes = 8, receive = { _, _ -> imports++ }) { server, dir ->
            Client(server.port).use { client ->
                client.login(server)
                client.data().use { data ->
                    assertEquals(150, client.command("LIST"))
                    assertEquals(-1, data.getInputStream().read())
                }
                assertEquals(226, client.reply())
                assertTrue(client.upload("big.zip", ByteArray(9)) >= 400)
                assertEquals(0, imports)
                assertTrue(dir.listFiles()!!.isEmpty())
            }
        }
    }

    @Test fun `close interrupts pending upload without importing`() {
        val started = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val dir = Files.createTempDirectory("ftp-stop").toFile()
        val server = FtpUploadServer(
            "127.0.0.1", dir,
            onReceived = { _, _ -> fail("Stopped upload imported") },
            onStatus = {
                if (it.activeUploads == 1) started.countDown()
                if (it.activeUploads == 0 && !it.running) stopped.countDown()
            },
        )
        try {
            server.open()
            Client(server.port).use { client ->
                client.login(server)
                client.data().use { data ->
                    client.start("book.cbz")
                    data.getOutputStream().write(1)
                    assertTrue(started.await(2, TimeUnit.SECONDS))
                    server.close()
                    assertTrue(stopped.await(2, TimeUnit.SECONDS))
                }
            }
            assertTrue(dir.listFiles()!!.isEmpty())
        } finally {
            server.close()
            dir.deleteRecursively()
        }
    }

    @Test fun `expiry rejects commands and closed server cannot reopen`() {
        val dir = Files.createTempDirectory("ftp-expiry").toFile()
        val now = java.util.concurrent.atomic.AtomicLong(0)
        val server = FtpUploadServer("127.0.0.1", dir, clock = { now.get() }, lifetimeMillis = 10,
            onReceived = { _, _ -> fail() })
        try {
            server.open()
            Client(server.port).use { client ->
                client.login(server)
                now.set(11)
                assertTrue(server.expired())
                try {
                    assertEquals(421, client.command("NOOP"))
                } catch (_: java.io.IOException) {
                    // The watchdog may close the socket before the next command arrives.
                }
            }
            server.close()
            assertThrows(IllegalStateException::class.java) { server.open() }
        } finally {
            server.close()
            dir.deleteRecursively()
        }
    }

    @Test fun `unexpected import errors do not disclose private data`() = fixture(
        receive = { _, _ -> error("private-secret-path") },
    ) { server, _ ->
        Client(server.port).use { client ->
            client.login(server)
            assertTrue(client.upload("book.epub", byteArrayOf(1)) >= 400)
        }
        assertFalse(server.status.message.contains("private-secret"))
    }
    @Test fun `only one upload can run at once`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            fixture(receive = { _, _ ->
                entered.countDown()
                release.await(3, TimeUnit.SECONDS)
            }) { server, _ ->
                val first = executor.submit<Int> {
                    Client(server.port).use { client ->
                        client.login(server)
                        client.upload("first.cbz", byteArrayOf(1))
                    }
                }
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                Client(server.port).use { client ->
                    client.login(server)
                    client.data().use { assertEquals(450, client.command("STOR second.cbz")) }
                }
                release.countDown()
                assertEquals(226, first.get(3, TimeUnit.SECONDS).toInt())
            }
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun `fifth control client and oversized command are disconnected`() = fixture { server, _ ->
        val clients = (1..4).map { Client(server.port) }
        try {
            Socket("127.0.0.1", server.port).use { fifth ->
                fifth.soTimeout = 3000
                assertTrue(fifth.getInputStream().bufferedReader().readLine().startsWith("421"))
            }
            clients.first().socket.apply {
                getOutputStream().write(("A".repeat(4097) + "\r\n").toByteArray())
                try {
                    assertEquals(-1, getInputStream().read())
                } catch (_: java.net.SocketException) {
                    // Closing with unread excess bytes may produce TCP reset instead of EOF.
                }
            }
        } finally { clients.forEach { it.close() } }
    }

    @Test fun `data socket must have the same peer as control`() = fixture { server, _ ->
        Client(server.port).use { client ->
            client.login(server)
            client.socket.getOutputStream().write("EPSV\r\n".toByteArray())
            val input = client.socket.getInputStream()
            val reply = StringBuilder()
            while (true) {
                val value = input.read()
                if (value == 10) break
                reply.append(value.toChar())
            }
            val port = reply.toString().substringAfter("|||").substringBefore('|').toInt()
            val foreign = try {
                Socket().apply {
                    bind(java.net.InetSocketAddress("127.0.0.2", 0))
                    connect(java.net.InetSocketAddress("127.0.0.1", port), 2000)
                }
            } catch (_: java.io.IOException) {
                org.junit.Assume.assumeTrue("Platform lacks secondary loopback address", false)
                return@fixture
            }
            foreign.use {
                assertEquals(150, client.command("STOR foreign.cbz"))
                assertEquals(426, client.reply())
            }
            assertEquals(0, server.status.completed)
        }
    }
    @Test fun `PASV uploads work and three failed passwords close the connection`() = fixture { server, _ ->
        Client(server.port).use { client ->
            client.login(server)
            client.pasvData().use { data ->
                client.start("pasv.rar")
                data.getOutputStream().write(byteArrayOf(1, 2, 3))
            }
            assertEquals(226, client.reply())
        }
        Client(server.port).use { client ->
            assertEquals(331, client.command("USER comictv"))
            repeat(3) { assertEquals(530, client.command("PASS invalid")) }
            assertEquals(-1, client.socket.getInputStream().read())
        }
    }

    @Test fun `startup removes only abandoned UUID FTP uploads`() {
        val dir = Files.createTempDirectory("ftp-orphan").toFile()
        val orphan = File(dir, "${java.util.UUID.randomUUID()}.ftp-upload").apply { writeText("old") }
        val keep = File(dir, "keep.txt").apply { writeText("keep") }
        val server = FtpUploadServer("127.0.0.1", dir, onReceived = { _, _ -> })
        try {
            server.open()
            assertFalse(orphan.exists())
            assertTrue(keep.exists())
        } finally {
            server.close()
            dir.deleteRecursively()
        }
    }

    @Test fun `expiration interrupts active upload and prevents import`() {
        val dir = Files.createTempDirectory("ftp-active-expiry").toFile()
        val ended = CountDownLatch(1)
        val now = java.util.concurrent.atomic.AtomicLong(0)
        val server = FtpUploadServer(
            "127.0.0.1", dir, clock = { now.get() }, lifetimeMillis = 10,
            onReceived = { _, _ -> fail("Expired upload imported") },
            onStatus = { if (!it.running && it.activeUploads == 0) ended.countDown() },
        )
        try {
            server.open()
            Client(server.port).use { client ->
                client.login(server)
                client.data().use { data ->
                    client.start("book.cbz")
                    data.getOutputStream().write(1)
                    now.set(11)
                    assertTrue(ended.await(3, TimeUnit.SECONDS))
                }
            }
            assertTrue(dir.listFiles()!!.isEmpty())
            assertEquals(0, server.status.completed)
        } finally {
            server.close()
            dir.deleteRecursively()
        }
    }
}
