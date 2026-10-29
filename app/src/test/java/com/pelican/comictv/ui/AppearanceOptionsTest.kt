package com.pelican.comictv.ui
import org.junit.Assert.*
import org.junit.Test
import com.pelican.comictv.reader.ImagePreset
import com.pelican.comictv.reader.ReaderImageOptions

class AppearanceOptionsTest {
    @Test fun carouselMotionNamesRestoreAllChoicesWithoutChangingOtherControls() {
        assertEquals("Pan", AppearanceOptions().carouselMotion)
        assertEquals("Pan", AppearanceOptions.restore("Flix", 0, false).carouselMotion)
        assertEquals("Pan", AppearanceOptions.restore("Flix", 0, false, carouselMotion = "invalid").carouselMotion)
        for (motion in listOf("None", "Pan", "ZoomIn", "ZoomOut", "Dynamic")) {
            val restored = AppearanceOptions.restore("Flix", 0, false, carouselImagePreset = "Ink",
                carouselImageIntensity = 0, carouselAutoplay = false, carouselVerticalPan = false, carouselMotion = motion)
            assertEquals(motion, restored.carouselMotion)
            assertFalse(restored.carouselAutoplay)
            assertFalse(restored.carouselVerticalPan)
            assertEquals(ReaderImageOptions(ImagePreset.Ink, 0), restored.carouselImageOptions)
        }
    }
    @Test fun carouselMotionFlagsDefaultOnAndRestoreIndependentlyOfTheImageFilter() {
        val legacy = AppearanceOptions.restore("Max", 0, false)
        assertTrue(legacy.carouselAutoplay)
        assertTrue(legacy.carouselVerticalPan)
        for (autoplay in listOf(false, true)) for (pan in listOf(false, true)) {
            val restored = AppearanceOptions.restore("Max", 0, false, carouselImagePreset = "Ink",
                carouselImageIntensity = 1, carouselAutoplay = autoplay, carouselVerticalPan = pan)
            assertEquals(autoplay, restored.carouselAutoplay)
            assertEquals(pan, restored.carouselVerticalPan)
            assertEquals(ReaderImageOptions(ImagePreset.Ink, 1), restored.carouselImageOptions)
            assertEquals(restored.carouselImageOptions, restored.copy(carouselAutoplay = !autoplay,
                carouselVerticalPan = !pan).carouselImageOptions)
        }
    }
    @Test fun carouselFilterDefaultsToStrongVintageAndRestoresEveryIndependentChoice() {
        assertEquals(ReaderImageOptions(ImagePreset.Vintage, 2), AppearanceOptions().carouselImageOptions)
        assertEquals(ReaderImageOptions(ImagePreset.Vintage, 2), AppearanceOptions.restore("Flix", 0, false).carouselImageOptions)
        for (preset in ImagePreset.entries) for (intensity in 0..2) {
            val original = AppearanceOptions(ColorPreset.Max, highContrast = true, carouselImagePreset = preset,
                carouselImageIntensity = intensity)
            val restored = AppearanceOptions.restore(original.preset.name, original.textSize, original.highContrast,
                carouselImagePreset = preset.name, carouselImageIntensity = intensity)
            assertEquals(original, restored)
            assertEquals(ReaderImageOptions(preset, intensity), restored.carouselImageOptions)
        }
        assertEquals(ImagePreset.Vintage, AppearanceOptions.restore(null, 0, false, carouselImagePreset = "unknown").carouselImagePreset)
        assertEquals(0, AppearanceOptions.restore(null, 0, false, carouselImageIntensity = -10).carouselImageIntensity)
        assertEquals(2, AppearanceOptions.restore(null, 0, false, carouselImageIntensity = 10).carouselImageIntensity)
        val disabled = AppearanceOptions(carouselImagePreset = ImagePreset.Original, carouselImageIntensity = 2)
        assertEquals(ImagePreset.Original, disabled.carouselImageOptions.preset)
        assertEquals(2, disabled.carouselImageOptions.intensity)
    }
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
        ColorPreset.entries.filter { it.selectable }.forEach { preset ->
            for (size in 0..2) {
                val original = AppearanceOptions(preset, size, true, coverBackground = true)
                assertEquals(original, AppearanceOptions.restore(preset.name, size, true, coverBackground = true))
            }
        }
    }
    @Test fun retiredThemesRestoreComicWithoutLosingAccessibilityOrArtworkPreference() {
        assertEquals(listOf(ColorPreset.Amber, ColorPreset.Flix, ColorPreset.Prime, ColorPreset.Max, ColorPreset.Cinema),
            ColorPreset.entries.filter { it.selectable })
        for (retired in listOf("Green", "Blue", "Violet")) {
            val restored = AppearanceOptions.restore(retired, 1, true, true, 125, true)
            assertEquals(ColorPreset.Amber, restored.preset)
            assertEquals(125, restored.textPercentage)
            assertTrue(restored.highContrast)
            assertTrue(restored.coverBackground)
            assertTrue(restored.cinemaMode)
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
