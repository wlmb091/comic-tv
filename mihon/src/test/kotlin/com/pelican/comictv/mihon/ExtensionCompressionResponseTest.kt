package com.pelican.comictv.mihon

import java.net.ServerSocket
import java.net.InetAddress
import java.util.Base64
import okhttp3.CompressionInterceptor
import okhttp3.Gzip
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.brotli.Brotli
import okhttp3.zstd.Zstd
import org.junit.Assert.*
import org.junit.Test

class ExtensionCompressionResponseTest {
    @Test fun readsGzipResponse() = readCompressed("gzip", "H4sIAAAAAAAACnPOz81MVggJs1IoSi0uyC8uSVRISVUoLE1MKcrMy8gvBgC0lwdGIAAAAA==")
    @Test fun readsBrotliResponse() = readCompressed("br", "iw+AQ29taWMgVFY6IHJlc3Bvc3RhIGRlIHF1YWRyaW5ob3MD")


    private fun readCompressed(encoding: String, fixture: String) {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        server.soTimeout = 5000
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val advertised = java.util.concurrent.atomic.AtomicReference<String>("")
        val bytes = Base64.getDecoder().decode(fixture)
        val serving = executor.submit {
            server.accept().use { socket ->
                socket.soTimeout = 5000
                val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Accept-Encoding:", ignoreCase = true)) advertised.set(line.substringAfter(':'))
                }
                socket.getOutputStream().use { output ->
                    output.write("HTTP/1.1 200 OK\r\nContent-Encoding: $encoding\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    output.write(bytes)
                }
            }
        }
        val client = OkHttpClient.Builder().callTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(CompressionInterceptor(Brotli, Gzip, Zstd)).build()
        try {
            client.newCall(Request.Builder().url("http://127.0.0.1:${server.localPort}/").build()).execute().use { response ->
                assertEquals("Comic TV: resposta de quadrinhos", response.body.string())
                assertNull(response.header("Content-Encoding"))
            }
            serving.get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue(advertised.get().contains(encoding))
        } finally {
            server.close()
            executor.shutdownNow()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
        }
    }
}
