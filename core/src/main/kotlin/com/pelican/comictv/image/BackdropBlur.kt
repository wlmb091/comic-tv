package com.pelican.comictv.image

/** Small separable software blur, usable on Android 8 without RenderEffect. */
fun blurBackdropPixels(input: IntArray, width: Int, height: Int, radius: Int = 6): IntArray {
    require(width > 0 && height > 0 && width.toLong() * height <= 65_536)
    require(input.size == width * height && radius in 1..16)
    var pixels = input.copyOf()
    repeat(2) {
        fun pass(source: IntArray, horizontal: Boolean): IntArray {
            val result = IntArray(source.size)
            for (y in 0 until height) for (x in 0 until width) {
                var alpha = 0; var red = 0; var green = 0; var blue = 0
                for (offset in -radius..radius) {
                    val sx = if (horizontal) (x + offset).coerceIn(0, width - 1) else x
                    val sy = if (horizontal) y else (y + offset).coerceIn(0, height - 1)
                    val color = source[sy * width + sx]
                    alpha += color ushr 24
                    red += (color ushr 16) and 255
                    green += (color ushr 8) and 255
                    blue += color and 255
                }
                val count = radius * 2 + 1
                result[y * width + x] = ((alpha / count) shl 24) or ((red / count) shl 16) or
                    ((green / count) shl 8) or (blue / count)
            }
            return result
        }
        pixels = pass(pass(pixels, true), false)
    }
    return pixels
}
