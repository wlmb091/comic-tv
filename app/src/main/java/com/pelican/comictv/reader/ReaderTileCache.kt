package com.pelican.comictv.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger

internal data class TileKey(val file: File, val stamp: String, val region: TileRegion, val options: ReaderImageOptions)
internal data class TileBitmap(val bitmap: Bitmap, val region: TileRegion)

/** CPU decoded blocks; Android uploads only visible blocks to its graphics texture cache. */
@Suppress("DEPRECATION")
internal class ReaderTileCache(private val budget: Long) {
    private val entries = ByteBudgetCache<TileKey, TileBitmap>(budget) { it.bitmap.allocationByteCount.toLong() }
    private val decoderLock = Mutex()
    private val sessions = ReaderDecoderSessions<String, DecodeSession>(2)
    private val foreground = AtomicInteger()
    @Volatile private var closed = false
    @Volatile private var resetRequested = false
    private class DecodeSession(val file: File) : Closeable {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(file.path, it) }
        private var tried = false
        private var decoder: BitmapRegionDecoder? = null
        init { require(bounds.outWidth > 0 && bounds.outHeight > 0) }
        fun region(): BitmapRegionDecoder? {
            if (!tried) { tried = true; decoder = runCatching { BitmapRegionDecoder.newInstance(file.path, false) }.getOrNull() }
            return decoder
        }
        override fun close() { decoder?.recycle(); decoder = null }
    }
    fun peek(key: TileKey): TileBitmap? = entries.get(key)
    // Filtered frames need several ARGB work buffers. Bound them using the existing RAM budget.
    fun framePixelLimit(options: ReaderImageOptions): Long = minOf(2_000_000L, budget / if (options.preset == ImagePreset.Original) 8L else 16L)
    fun clear() { entries.clear(); resetRequested = true; releaseIfIdle() }
    fun close() { closed = true; entries.clear(); releaseIfIdle() }
    private fun releaseIfIdle() {
        if (decoderLock.tryLock()) try { finishLocked() } finally { decoderLock.unlock() }
    }
    private fun finishLocked() { if (closed || resetRequested) { sessions.clear(); resetRequested = false } }

    suspend fun load(key: TileKey): TileBitmap {
        foreground.incrementAndGet()
        return try { withContext(Dispatchers.Default) { decoderLock.withLock {
            try { decodeLocked(key) } finally { finishLocked() }
        } } } finally { foreground.decrementAndGet() }
    }
    /** Speculative pixels never queue in front of visible work. Original-page prefetch is unchanged. */
    suspend fun prefetch(key: TileKey) {
        if (closed || foreground.get() > 0 || peek(key) != null) return
        withContext(Dispatchers.Default) {
            if (foreground.get() > 0 || !decoderLock.tryLock()) return@withContext
            try { if (!closed && foreground.get() == 0) decodeLocked(key) }
            finally { finishLocked(); decoderLock.unlock() }
        }
    }
    private suspend fun decodeLocked(key: TileKey): TileBitmap {
        peek(key)?.let { return it }
        check(!closed)
        currentCoroutineContext().ensureActive()
        if (resetRequested) { sessions.clear(); resetRequested = false }
        val session = sessions.get(key.stamp) { DecodeSession(key.file) }
        val bounds = session.bounds
        val r = key.region
        val expanded = Rect((r.left - r.sample).coerceAtLeast(0), (r.top - r.sample).coerceAtLeast(0),
            (r.right + r.sample).coerceAtMost(bounds.outWidth), (r.bottom + r.sample).coerceAtMost(bounds.outHeight))
        val whole = expanded.left == 0 && expanded.top == 0 && expanded.right == bounds.outWidth && expanded.bottom == bounds.outHeight
        var owned: Bitmap? = null
        try {
            val bitmap = if (whole) BitmapFactory.decodeFile(key.file.path, BitmapFactory.Options().apply {
                inSampleSize = r.sample; inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: error("Não foi possível decodificar a página") else try {
                session.region()?.decodeRegion(expanded, BitmapFactory.Options().apply { inSampleSize = r.sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
                    ?: error("Formato sem recortes")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                // Unsupported region formats keep the previous bounded full-decode fallback.
                var sample = r.sample
                while (bounds.outWidth.toLong() * bounds.outHeight / sample / sample > 2_000_000L) sample *= 2
                val full = BitmapFactory.decodeFile(key.file.path, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
                    ?: error("Formato de imagem indisponível")
                try {
                    val x = (expanded.left.toFloat() / bounds.outWidth * full.width).toInt().coerceIn(0, full.width - 1)
                    val y = (expanded.top.toFloat() / bounds.outHeight * full.height).toInt().coerceIn(0, full.height - 1)
                    val right = kotlin.math.ceil(expanded.right.toDouble() / bounds.outWidth * full.width).toInt().coerceIn(x + 1, full.width)
                    val bottom = kotlin.math.ceil(expanded.bottom.toDouble() / bounds.outHeight * full.height).toInt().coerceIn(y + 1, full.height)
                    Bitmap.createBitmap(full, x, y, right - x, bottom - y).let { if (it === full) requireNotNull(it.copy(Bitmap.Config.ARGB_8888, false)) else it }
                } finally { full.recycle() }
            }
            owned = bitmap
            val context = currentCoroutineContext()
            context.ensureActive()
            val result = if (key.options.preset == ImagePreset.Original) bitmap else {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val filtered = enhancePixels(pixels, bitmap.width, bitmap.height, key.options) { context.ensureActive() }
                Bitmap.createBitmap(filtered, bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { bitmap.recycle(); owned = it }
            }
            context.ensureActive()
            check(!closed)
            return TileBitmap(result, TileRegion(expanded.left, expanded.top, expanded.right, expanded.bottom, r.sample)).also {
                entries.put(key, it); owned = null // Cache/canvas owns published frames; never recycle them on eviction.
            }
        } finally { owned?.let { if (!it.isRecycled) it.recycle() } }
    }
}
