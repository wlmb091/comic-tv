package com.pelican.comictv.tv

import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class TvLocalStreamServerTest {
    private val data = ByteArray(1000) { (it % 251).toByte() }
    private val source = object : TvByteSource {
        override val size = data.size.toLong()
        override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= size) return -1
            val n = minOf(length.toLong(), size - position).toInt()
            System.arraycopy(data, position.toInt(), buffer, offset, n); return n
        }
    }

    @Test fun rangesFollowHttpRules() {
        assertEquals(0L..99L, TvLocalStreamServer.parseRange("bytes=0-99", 1000))
        assertEquals(900L..999L, TvLocalStreamServer.parseRange("bytes=900-", 1000))
        assertEquals(950L..999L, TvLocalStreamServer.parseRange("bytes=-50", 1000))
        assertEquals(990L..999L, TvLocalStreamServer.parseRange("bytes=990-5000", 1000))
        assertNull(TvLocalStreamServer.parseRange("bytes=1000-", 1000))
        assertNull(TvLocalStreamServer.parseRange("bytes=5-1", 1000))
        assertNull(TvLocalStreamServer.parseRange("items=0-1", 1000))
    }

    @Test fun servesPartialContentOnlyUnderItsToken() {
        TvLocalStreamServer(source, "video/x-matroska").start().use { server ->
            val c = URL(server.videoUrl).openConnection() as HttpURLConnection
            c.setRequestProperty("Range", "bytes=100-199")
            assertEquals(206, c.responseCode)
            assertEquals("bytes 100-199/1000", c.getHeaderField("Content-Range"))
            assertArrayEquals(data.copyOfRange(100, 200), c.inputStream.readBytes())
            val full = URL(server.videoUrl).openConnection() as HttpURLConnection
            assertEquals(200, full.responseCode); assertEquals(1000, full.inputStream.readBytes().size)
            val guess = URL(server.videoUrl.replaceAfter("127.0.0.1:", server.videoUrl.substringAfter("127.0.0.1:").substringBefore('/') + "/0000/video")).openConnection() as HttpURLConnection
            assertEquals(404, guess.responseCode)
        }
    }
}
