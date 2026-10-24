package com.pelican.comictv.reader
import kotlin.math.pow
import kotlin.math.roundToInt

enum class ImagePreset(val label: String) { Original("Original"), Ink("Texto e traços"), Vintage("HQ antiga") }
data class ReaderImageOptions(val preset: ImagePreset = ImagePreset.Original, val intensity: Int = 1) {
    companion object {
        fun restore(name: String?, intensity: Int): ReaderImageOptions {
            val preset = ImagePreset.entries.firstOrNull { it.name == name } ?: return ReaderImageOptions()
            return ReaderImageOptions(preset, intensity.coerceIn(0, 2))
        }
    }
}

fun enhancePixels(input: IntArray, width: Int, height: Int, options: ReaderImageOptions, checkActive: () -> Unit = {}): IntArray {
    require(width > 0 && height > 0 && width.toLong() * height == input.size.toLong())
    if (options.preset == ImagePreset.Original) return input.copyOf()
    val intensity = options.intensity.coerceIn(0, 2)
    val black = if (options.preset == ImagePreset.Vintage) listOf(5, 10, 16)[intensity] else 0
    val gamma = if (options.preset == ImagePreset.Vintage) listOf(1.03, 1.07, 1.11)[intensity] else 1.0
    val table = IntArray(256) { (((it - black).toDouble() / (255 - black)).coerceIn(0.0, 1.0).pow(gamma) * 255).roundToInt() }
    val leveled = IntArray(input.size) { index ->
        val p = input[index]
        (p and -0x1000000) or (table[(p ushr 16) and 255] shl 16) or (table[(p ushr 8) and 255] shl 8) or table[p and 255]
    }
    val amount = if (options.preset == ImagePreset.Ink) listOf(.18f, .32f, .48f)[intensity] else listOf(.1f, .18f, .26f)[intensity]
    val limit = listOf(8, 12, 18)[intensity]
    val output = leveled.copyOf()
    val channels = intArrayOf(16, 8, 0)
    for (y in 1 until height - 1) {
        checkActive()
        for (x in 1 until width - 1) {
            val i = y * width + x
            var result = leveled[i] and -0x1000000
            for (shift in channels) {
                val center = (leveled[i] ushr shift) and 255
                val mean = ((leveled[i-1] ushr shift and 255) + (leveled[i+1] ushr shift and 255) +
                    (leveled[i-width] ushr shift and 255) + (leveled[i+width] ushr shift and 255)) / 4f
                val delta = ((center - mean) * amount).roundToInt().coerceIn(-limit, limit)
                result = result or ((center + delta).coerceIn(0, 255) shl shift)
            }
            output[i] = result
        }
    }
    return output
}
