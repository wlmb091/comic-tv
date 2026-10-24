package com.pelican.comictv.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

internal data class TileKey(val file: File, val stamp: String, val region: TileRegion, val options: ReaderImageOptions)
internal data class TileBitmap(val bitmap: Bitmap, val region: TileRegion)

/** CPU decoded blocks; Android uploads only visible blocks to its graphics texture cache. */
internal class ReaderTileCache(private val budget: Long) {
    private val entries = ByteBudgetCache<TileKey, TileBitmap>(budget) { it.bitmap.allocationByteCount.toLong() }
    private val decoderLock = Mutex()
    @Volatile private var closed = false
    fun peek(key: TileKey): TileBitmap? = entries.get(key)
    private fun put(key: TileKey, value: TileBitmap) { if (!closed) entries.put(key, value) }
    fun clear() = entries.clear()
    fun close() { closed = true; entries.clear() }

    @Suppress("DEPRECATION")
    suspend fun load(key: TileKey): TileBitmap = withContext(Dispatchers.Default) {
        decoderLock.withLock {
            peek(key)?.let { return@withLock it }
            check(!closed)
            currentCoroutineContext().ensureActive()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(key.file.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0)
            val r = key.region
            val expanded = Rect((r.left - r.sample).coerceAtLeast(0), (r.top - r.sample).coerceAtLeast(0),
                (r.right + r.sample).coerceAtMost(bounds.outWidth), (r.bottom + r.sample).coerceAtMost(bounds.outHeight))
            val bitmap = try {
                val decoder = BitmapRegionDecoder.newInstance(key.file.path, false) ?: error("Decodificador indisponível")
                try { decoder.decodeRegion(expanded, BitmapFactory.Options().apply { inSampleSize = r.sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
                    ?: error("Não foi possível decodificar o recorte") }
                finally { decoder.recycle() }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                // Unsupported region formats use a bounded full decode, never an unrestricted bitmap.
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
            try {
                val context = currentCoroutineContext()
                context.ensureActive()
                val result = if (key.options.preset == ImagePreset.Original) bitmap else {
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    val filtered = enhancePixels(pixels, bitmap.width, bitmap.height, key.options) { context.ensureActive() }
                    Bitmap.createBitmap(filtered, bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { bitmap.recycle() }
                }
                context.ensureActive()
                TileBitmap(result, TileRegion(expanded.left, expanded.top, expanded.right, expanded.bottom, r.sample)).also { put(key, it) }
            } catch (e: Exception) { if (!bitmap.isRecycled) bitmap.recycle(); throw e }
        }
    }
}
