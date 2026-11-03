package com.pelican.comictv.tv

import fi.iki.elonen.NanoHTTPD
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/** Bytes of one video that may still be downloading; [read] blocks until data exists and returns -1 at the end. */
interface TvByteSource {
    val size: Long
    fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int
}

/**
 * Loopback-only HTTP server that hands a torrent video and its subtitle to an external player.
 * Every path carries a random token, so other apps on the device cannot guess the address.
 */
class TvLocalStreamServer(private val source: TvByteSource, private val mime: String,
    private val clock: () -> Long = System::currentTimeMillis) : Closeable {
    private val token = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 255) }
    private val files = ConcurrentHashMap<String, Pair<String, File>>()
    @Volatile var lastUse = clock(); private set
    @Volatile private var closed = false
    private val server = object : NanoHTTPD("127.0.0.1", 0) { override fun serve(session: IHTTPSession): Response = handle(session) }
    fun start(): TvLocalStreamServer { server.start(30_000, true); return this }
    val videoUrl get() = "http://127.0.0.1:${server.listeningPort}/$token/video"
    /** Publishes a small private file (a subtitle) under [name]; returns its loopback address. */
    fun file(name: String, mime: String, file: File): String {
        require(name.matches(Regex("[A-Za-z0-9._-]{1,60}")))
        files[name] = mime to file
        return "http://127.0.0.1:${server.listeningPort}/$token/f/$name"
    }
    override fun close() { closed = true; server.stop() }

    private fun handle(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val path = session.uri
        if (closed || !path.startsWith("/$token/")) return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "")
        if (session.method != NanoHTTPD.Method.GET && session.method != NanoHTTPD.Method.HEAD)
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "text/plain", "")
        lastUse = clock()
        val rest = path.removePrefix("/$token/")
        if (rest.startsWith("f/")) {
            val (type, file) = files[rest.removePrefix("f/")] ?: return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "")
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, type, file.inputStream(), file.length())
        }
        if (rest != "video") return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "")
        val size = source.size
        val range = parseRange(session.headers["range"], size)
        if (range == null && session.headers["range"] != null) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "").apply {
                addHeader("Content-Range", "bytes */$size")
            }
        }
        val start = range?.first ?: 0L
        val end = range?.last ?: (size - 1)
        val length = end - start + 1
        val body = if (session.method == NanoHTTPD.Method.HEAD) ByteArray(0).inputStream() else stream(start, length)
        val status = if (range != null) NanoHTTPD.Response.Status.PARTIAL_CONTENT else NanoHTTPD.Response.Status.OK
        return NanoHTTPD.newFixedLengthResponse(status, mime, body, length).apply {
            addHeader("Accept-Ranges", "bytes")
            if (range != null) addHeader("Content-Range", "bytes $start-$end/$size")
        }
    }

    private fun stream(start: Long, length: Long) = object : InputStream() {
        var position = start
        var remaining = length
        override fun read(): Int { val one = ByteArray(1); return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 255 }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (closed) return -1
            if (remaining <= 0) return -1
            val n = source.read(position, b, off, minOf(len.toLong(), remaining).toInt())
            if (n < 0) return -1
            position += n; remaining -= n; lastUse = clock()
            return n
        }
    }

    companion object {
        /** A single satisfiable byte range, or null when absent or invalid. */
        fun parseRange(header: String?, size: Long): LongRange? {
            if (header == null || size <= 0) return null
            val m = Regex("bytes=(\\d*)-(\\d*)").matchEntire(header.trim()) ?: return null
            val (a, b) = m.destructured
            val range = when {
                a.isEmpty() && b.isEmpty() -> return null
                a.isEmpty() -> { val n = b.toLongOrNull() ?: return null; if (n <= 0) return null; maxOf(0, size - n)..(size - 1) }
                else -> { val s = a.toLongOrNull() ?: return null; val e = if (b.isEmpty()) size - 1 else (b.toLongOrNull() ?: return null).coerceAtMost(size - 1); s..e }
            }
            return if (range.first > range.last || range.first >= size) null else range
        }
    }
}
