package com.pelican.comictv.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSurfaceTest {
    @Test fun darkButtonPaletteOnlyAffectsNeutralThemesOutsideHighContrast() {
        val previous = TvColors.options
        try {
            for (preset in ColorPreset.entries) {
                for (contrast in listOf(false, true)) {
                    TvColors.options = AppearanceOptions(preset = preset, highContrast = contrast)
                    val neutral = !contrast && preset in setOf(ColorPreset.Amber, ColorPreset.Flix, ColorPreset.Cinema)
                    assertEquals(preset.name, if (neutral) Color(0xFF303030) else Color(preset.action), TvColors.Action)
                    assertEquals(preset.name, if (neutral) Color.White else Color(preset.onAction), TvColors.OnAction)
                    val surface = if (contrast) Color.Black else if (neutral) Color(0xFF181818) else Color(preset.panel)
                    assertEquals(preset.name, surface, TvColors.ButtonSurface)
                }
            }
        } finally { TvColors.options = previous }
    }
    @Test fun frostedSurfaceLimitsUnderlyingBlackWhiteContrastToEightLevels() {
        val previous = TvColors.options
        try {
            for (preset in ColorPreset.entries) {
                TvColors.options = AppearanceOptions(preset = preset)
                for (surface in listOf(TvColors.FrostedPanel, TvColors.FrostedSidebar)) {
                    val white = surface.compositeOver(Color.White)
                    val black = surface.compositeOver(Color.Black)
                    val limit = 8f / 255f + .000001f
                    assertTrue(preset.name, white.red - black.red <= limit)
                    assertTrue(preset.name, white.green - black.green <= limit)
                    assertTrue(preset.name, white.blue - black.blue <= limit)
                }
            }
            TvColors.options = AppearanceOptions(highContrast = true)
            assertEquals(Color.Black, TvColors.FrostedPanel)
            assertEquals(Color.Black, TvColors.FrostedSidebar)
        } finally { TvColors.options = previous }
    }
    @Test fun neutralBackgroundIsExactGrayAndColoredPresetsStayUnchanged() {
        val previous = TvColors.options
        try {
            for (preset in ColorPreset.entries) {
                TvColors.options = AppearanceOptions(preset = preset)
                val expected = if (preset in setOf(ColorPreset.Amber, ColorPreset.Flix, ColorPreset.Cinema))
                    Color(0xFF0F0F0F) else Color(preset.background)
                assertEquals(preset.name, expected, TvColors.Background)
                val sidebar = if (preset in setOf(ColorPreset.Amber, ColorPreset.Flix, ColorPreset.Cinema))
                    Color(0xF7404040) else Color(preset.panel).copy(alpha = 247f / 255f)
                assertEquals(preset.name, sidebar, TvColors.FrostedSidebar)
            }
            TvColors.options = AppearanceOptions(highContrast = true)
            assertEquals(Color.Black, TvColors.Background)
        } finally { TvColors.options = previous }
    }
}
