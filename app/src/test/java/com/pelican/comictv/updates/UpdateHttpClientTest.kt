package com.pelican.comictv.updates

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class UpdateHttpClientTest {
    private fun response(chain: Interceptor.Chain, code: Int, body: ResponseBody, location: String? = null): Response =
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Test").body(body)
            .apply { if (location != null) header("Location", location) }.build()
    @Test fun redirectsStayHttpsAndWithinGithubHostsAndBodiesAreBounded() = runBlocking {
        var calls = 0
        var location = "http://github.com/unsafe"
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            if (chain.request().url.host == "github.com") response(chain, 302, "".toResponseBody(), location)
            else response(chain, 200, "0123456789".toResponseBody())
        }.build()
        val transport = UpdateHttpClient(client)
        try { transport.text(UpdateManifest.CHANNEL_URL, 20); fail("Downgrade followed") } catch (_: Exception) { }
        assertEquals(1, calls)
        location = "https://evil.example/file"
        try { transport.text(UpdateManifest.CHANNEL_URL, 20); fail("External host followed") } catch (_: Exception) { }
        assertEquals(2, calls)
        location = "https://release-assets.githubusercontent.com/file?token=temporary"
        assertEquals("0123456789", transport.text(UpdateManifest.CHANNEL_URL, 20).toString(Charsets.UTF_8))
        try { transport.text(UpdateManifest.CHANNEL_URL, 5); fail("Oversize accepted") } catch (_: Exception) { }
    }
    @Test(timeout = 5000) fun cancellationClosesAResponseWhoseBodyReadIsBlocked() = runBlocking {
        val entered = CountDownLatch(1); val released = CountDownLatch(1)
        val source = object : Source {
            override fun timeout() = Timeout.NONE
            override fun close() { released.countDown() }
            override fun read(sink: Buffer, byteCount: Long): Long { entered.countDown(); released.await(3, TimeUnit.SECONDS); return -1 }
        }
        val body = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength() = -1L
            override fun source() = source.buffer()
        }
        val client = OkHttpClient.Builder().addInterceptor { response(it, 200, body) }.build()
        val file = File.createTempFile("update-body", ".part")
        val job = launch(Dispatchers.IO) { UpdateHttpClient(client).download(UpdateManifest.CHANNEL_URL, file, 100) { } }
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS)); job.cancel()
            assertTrue("Response was not closed on cancellation", released.await(500, TimeUnit.MILLISECONDS))
            job.join()
        } finally { released.countDown(); job.cancelAndJoin(); file.delete() }
    }
}
