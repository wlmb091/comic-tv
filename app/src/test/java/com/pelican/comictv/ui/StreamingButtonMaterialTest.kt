package com.pelican.comictv.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class StreamingButtonMaterialTest {
    private val streaming = listOf(ColorPreset.Flix, ColorPreset.Prime, ColorPreset.Max, ColorPreset.Cinema)

    @Test fun translucentActionsKeepWhiteLabelsReadableOverBrightArtwork() {
        streaming.forEach { preset ->
            val surface = requireNotNull(transparentButtonSurface(AppearanceOptions(preset = preset)))
            assertTrue("${preset.name} should reveal artwork", surface.alpha > 0f && surface.alpha < 1f)
            val brightestComposite = surface.compositeOver(Color.White)
            val contrast = (Color.White.luminance() + .05f) / (brightestComposite.luminance() + .05f)
            assertTrue("${preset.name} white label contrast is $contrast", contrast >= 4.5f)
        }
    }

    @Test fun highContrastActionsNeverRevealArtwork() {
        streaming.forEach { preset ->
            assertEquals(Color.Black, transparentButtonSurface(AppearanceOptions(preset = preset, highContrast = true)))
        }
    }

    @Test fun originalComicAndLegacyPalettesKeepExistingButtonMaterial() {
        listOf(ColorPreset.Amber, ColorPreset.Green, ColorPreset.Blue, ColorPreset.Violet).forEach { preset ->
            assertNull(transparentButtonSurface(AppearanceOptions(preset = preset)))
        }
    }
}
