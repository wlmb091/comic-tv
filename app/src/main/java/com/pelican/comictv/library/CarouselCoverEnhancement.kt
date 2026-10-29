package com.pelican.comictv.library

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import com.pelican.comictv.reader.ImagePreset
import com.pelican.comictv.reader.ReaderImageOptions
import com.pelican.comictv.reader.enhancePixels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Reader's strong vintage filter, once per bounded decode, never on originals or every frame. */
internal class CarouselCoverEnhancement(private val options: ReaderImageOptions) : Transformation() {
    override val cacheKey = "carousel-enhance-v1-${options.preset.name}-${options.intensity}"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap = withContext(Dispatchers.Default) {
        val context = currentCoroutineContext()
        context.ensureActive()
        // Avoid transient pixel arrays for exceptionally large/custom decoder results.
        if (input.width.toLong() * input.height > 2_000_000L) return@withContext input
        val pixels = IntArray(input.width * input.height)
        input.getPixels(pixels, 0, input.width, 0, 0, input.width, input.height)
        val filtered = enhancePixels(pixels, input.width, input.height,
            options) { context.ensureActive() }
        context.ensureActive()
        Bitmap.createBitmap(filtered, input.width, input.height, Bitmap.Config.ARGB_8888)
    }
}
