package com.pelican.comictv.reader

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The tone and color of one edition, kept as numbers: the original file never changes.
 * The phone panel has a copy of this math in companion/index.html; ImageAdjustmentsTest and companion-ui.test.mjs hold the same vectors.
 * Order: temperature, tint, exposure, contrast, highlights, shadows, whites, blacks, saturation, vibrance,
 * then hue, saturation and luminance of each of the 8 color bands. Exposure is in hundredths of a stop, the rest in -100..100.
 */
data class ImageAdjustments(val values: List<Int> = IDENTITY) {
    init { require(values.size == SIZE) { "Ajustes incompletos." } }
    val identity: Boolean get() = values.all { it == 0 }
    /** Built once per set of values: a page then only looks each pixel up, however many sliders moved. */
    private val lut: FloatArray by lazy { buildLut(values) }
    fun encode(): String = "$FORMAT:" + values.joinToString(",")
    fun adjust(pixels: IntArray, checkActive: () -> Unit = {}): IntArray {
        if (identity) return pixels.copyOf()
        val table = lut
        return IntArray(pixels.size) { index ->
            if (index and 0xFFFF == 0) checkActive()
            lutPixel(table, pixels[index])
        }
    }
    companion object {
        const val SIZE = 34
        private const val FORMAT = "1"
        private const val GRID = 33
        private val IDENTITY = List(SIZE) { 0 }
        private val BAND_HUES = floatArrayOf(0f, 30f, 60f, 120f, 180f, 240f, 280f, 320f)
        /** An unknown or damaged value means no adjustment, never a failure while reading. */
        fun restore(text: String?): ImageAdjustments = text?.let(::parse) ?: ImageAdjustments()
        fun parse(text: String): ImageAdjustments? {
            if (text.length > 400 || !text.startsWith("$FORMAT:")) return null
            val numbers = text.substringAfter(':').split(',').map { it.toIntOrNull() ?: return null }
            if (numbers.size != SIZE) return null
            if (numbers.withIndex().any { (i, value) -> abs(value) > if (i == 2) 300 else 100 }) return null
            return ImageAdjustments(numbers)
        }
        private fun toLinear(c: Float) = if (c <= .04045f) c / 12.92f else ((c + .055f) / 1.055f).pow(2.4f)
        private fun toGamma(c: Float) = if (c <= .0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - .055f
        private fun unit(c: Float) = c.coerceIn(0f, 1f)
        private fun tone(input: Float, v: List<Int>): Float {
            val black = -v[7] / 100f * .25f
            val white = 1f - v[6] / 100f * .25f
            var x = unit((input - black) / (white - black))
            x += v[5] / 100f * .9f * x * (1f - x) * (1f - x)
            x += v[4] / 100f * .9f * x * x * (1f - x)
            x = unit(x)
            val k = v[3] / 100f
            return unit(if (k >= 0f) x + k * (x * x * (3f - 2f * x) - x) else .5f + (x - .5f) * (1f + k * .6f))
        }
        private fun hueOf(r: Float, g: Float, b: Float, high: Float, chroma: Float): Float {
            val h = when (high) {
                r -> ((g - b) / chroma).mod(6f)
                g -> (b - r) / chroma + 2f
                else -> (r - g) / chroma + 4f
            }
            return h * 60f
        }
        private fun channel(p: Float, q: Float, turn: Float): Float {
            val t = turn - floor(turn)
            return when {
                t < 1f / 6f -> p + (q - p) * 6f * t
                t < .5f -> q
                t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
                else -> p
            }
        }
        private fun mix(rgb: FloatArray, v: List<Int>) {
            val (r, g, b) = rgb
            val high = max(r, max(g, b)); val low = min(r, min(g, b)); val chroma = high - low
            if (chroma < 1e-4f) return
            var l = (high + low) / 2f
            var s = chroma / (1f - abs(2f * l - 1f)).coerceAtLeast(1e-4f)
            var h = hueOf(r, g, b, high, chroma)
            var band = BAND_HUES.size - 1
            for (i in BAND_HUES.indices) if (h >= BAND_HUES[i]) band = i
            val next = (band + 1) % BAND_HUES.size
            val end = if (next == 0) 360f else BAND_HUES[next]
            val t = (h - BAND_HUES[band]) / (end - BAND_HUES[band])
            fun blend(part: Int) = (v[10 + band * 3 + part] * (1f - t) + v[10 + next * 3 + part] * t) / 100f
            // Nearly gray pixels have no real hue: paper and ink stay out of the color bands.
            val weight = min(1f, s * 4f)
            h = (h + blend(0) * 30f * weight).mod(360f)
            s = unit(s * max(0f, 1f + blend(1) * weight))
            l = unit(l + blend(2) * .3f * weight * 4f * l * (1f - l))
            val q = if (l < .5f) l * (1f + s) else l + s - l * s
            val p = 2f * l - q
            val turn = h / 360f
            rgb[0] = channel(p, q, turn + 1f / 3f); rgb[1] = channel(p, q, turn); rgb[2] = channel(p, q, turn - 1f / 3f)
        }
        /** One color, 0..1 per channel, through all the steps. */
        fun adjustRgb(v: List<Int>, red: Float, green: Float, blue: Float): FloatArray {
            val gain = 2f.pow(v[2] / 100f)
            val warm = v[0] / 100f * .5f
            val rgb = floatArrayOf(toLinear(red) * gain * (1f + warm), toLinear(green) * gain * (1f - v[1] / 100f * .4f), toLinear(blue) * gain * (1f - warm))
            for (i in 0..2) rgb[i] = tone(unit(toGamma(unit(rgb[i]))), v)
            val luma = .2126f * rgb[0] + .7152f * rgb[1] + .0722f * rgb[2]
            val spread = max(rgb[0], max(rgb[1], rgb[2])) - min(rgb[0], min(rgb[1], rgb[2]))
            val vibrance = v[9] / 100f
            val scale = max(0f, 1f + v[8] / 100f + vibrance * if (vibrance > 0f) (1f - spread) * (1f - spread) else 1f)
            for (i in 0..2) rgb[i] = unit(luma + (rgb[i] - luma) * scale)
            if ((10 until SIZE).any { v[it] != 0 }) mix(rgb, v)
            for (i in 0..2) rgb[i] = unit(rgb[i])
            return rgb
        }
        private fun buildLut(v: List<Int>): FloatArray {
            val table = FloatArray(GRID * GRID * GRID * 3)
            var at = 0
            val step = 1f / (GRID - 1)
            for (r in 0 until GRID) for (g in 0 until GRID) for (b in 0 until GRID) {
                val out = adjustRgb(v, r * step, g * step, b * step)
                table[at++] = out[0]; table[at++] = out[1]; table[at++] = out[2]
            }
            return table
        }
        private fun lutPixel(t: FloatArray, p: Int): Int {
            val top = GRID - 1
            val rf = ((p ushr 16) and 255) / 255f * top; val gf = ((p ushr 8) and 255) / 255f * top; val bf = (p and 255) / 255f * top
            val r0 = min(rf.toInt(), top - 1); val g0 = min(gf.toInt(), top - 1); val b0 = min(bf.toInt(), top - 1)
            val dr = rf - r0; val dg = gf - g0; val db = bf - b0
            val base = (r0 * GRID * GRID + g0 * GRID + b0) * 3
            val stepR = GRID * GRID * 3; val stepG = GRID * 3
            fun at(c: Int): Int {
                val c00 = t[base + c] * (1 - db) + t[base + 3 + c] * db
                val c01 = t[base + stepG + c] * (1 - db) + t[base + stepG + 3 + c] * db
                val c10 = t[base + stepR + c] * (1 - db) + t[base + stepR + 3 + c] * db
                val c11 = t[base + stepR + stepG + c] * (1 - db) + t[base + stepR + stepG + 3 + c] * db
                return (((c00 * (1 - dg) + c01 * dg) * (1 - dr) + (c10 * (1 - dg) + c11 * dg) * dr) * 255f).roundToInt().coerceIn(0, 255)
            }
            return (p and -0x1000000) or (at(0) shl 16) or (at(1) shl 8) or at(2)
        }
    }
}
