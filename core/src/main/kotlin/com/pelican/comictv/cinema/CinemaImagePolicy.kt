package com.pelican.comictv.cinema

import kotlin.math.sqrt

object CinemaImagePolicy {
    fun validate(width: Int, height: Int, mime: String?) {
        require(mime in setOf("image/jpeg", "image/png", "image/webp")) { "Escolha uma imagem JPEG, PNG ou WebP estática." }
        require(width in 1280..16384 && height in 720..16384 && width.toLong() * height <= 40_000_000L) {
            "Use uma arte horizontal de pelo menos 1280×720, até 40 megapixels." }
        require(width.toDouble() / height in 1.3..2.5) { "Escolha uma arte horizontal; a proporção 16:9 é recomendada." }
    }

    fun decodeSize(viewWidth: Int, viewHeight: Int, lowMemory: Boolean): Pair<Int, Int> {
        val width = viewWidth.coerceIn(1, 8192)
        val height = viewHeight.coerceIn(1, 8192)
        val pixels = if (lowMemory) 1920L * 1080 else 3840L * 2160
        val factor = minOf(1.0, sqrt(pixels.toDouble() / (width.toLong() * height)))
        return (width * factor).toInt().coerceAtLeast(1) to (height * factor).toInt().coerceAtLeast(1)
    }
}
