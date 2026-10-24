package com.pelican.comictv.ui
import org.junit.Assert.*
import org.junit.Test

class AppearanceOptionsTest {
    @Test fun percentageStepsPreserveLegacySettingsAndClampTheAllowedRange() {
        assertEquals(listOf(100, 115, 130), (0..2).map { AppearanceOptions(textSize = it).textPercentage })
        for (percent in 85..130 step 5) {
            val options = AppearanceOptions(ColorPreset.Prime, highContrast = true, coverBackground = true).withTextPercentage(percent)
            assertEquals(percent, options.textPercentage)
            assertEquals(percent / 100f, options.textScale, .001f)
            assertEquals(options, AppearanceOptions.restore(options.preset.name, options.textSize, true, true, options.textPercent))
            assertEquals(ColorPreset.Prime, options.preset)
        }
        assertEquals(85, AppearanceOptions().withTextPercentage(-100).textPercentage)
        assertEquals(130, AppearanceOptions().withTextPercentage(1000).textPercentage)
        assertEquals(100, AppearanceOptions().withTextPercentage(102).textPercentage)
    }
    @Test fun invalidStoredValuesRestoreSafeDefaults() {
        val restored = AppearanceOptions.restore("unknown", -100, true)
        assertEquals(ColorPreset.Amber, restored.preset)
        assertEquals(1f, restored.textScale, .001f)
        assertTrue(restored.highContrast)
        assertEquals(1.3f, AppearanceOptions.restore("Green", 100, false).textScale, .001f)
    }
    @Test fun presetAndTextChoicesRoundTripByStableName() {
        ColorPreset.entries.forEach { preset ->
            for (size in 0..2) {
                val original = AppearanceOptions(preset, size, true, coverBackground = true)
                assertEquals(original, AppearanceOptions.restore(preset.name, size, true, coverBackground = true))
            }
        }
    }
    @Test fun everyAccentHasStrongContrastAgainstBlack() {
        fun linear(channel: Long): Double {
            val value = channel / 255.0
            return if (value <= .04045) value / 12.92 else Math.pow((value + .055) / 1.055, 2.4)
        }
        ColorPreset.entries.forEach {
            val rgb = it.accent
            val luminance = .2126 * linear((rgb shr 16) and 255) + .7152 * linear((rgb shr 8) and 255) + .0722 * linear(rgb and 255)
            assertTrue(it.name, (luminance + .05) / .05 >= 7)
        }
    }
}
