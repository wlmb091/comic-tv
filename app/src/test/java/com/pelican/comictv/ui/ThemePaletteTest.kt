package com.pelican.comictv.ui

import org.junit.Assert.*
import org.junit.Test

class ThemePaletteTest {
    private fun luminance(rgb: Long): Double {
        fun channel(shift: Int): Double {
            val v = ((rgb shr shift) and 255) / 255.0
            return if (v <= .04045) v / 12.92 else Math.pow((v + .055) / 1.055, 2.4)
        }
        return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
    }
    private fun contrast(a: Long, b: Long): Double {
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
    @Test fun themeTextAndActionsRemainReadable() {
        ColorPreset.entries.forEach {
            assertTrue(it.label + " action", contrast(it.action, it.onAction) >= 4.5)
            assertTrue(it.label + " accent", contrast(it.accent, it.panel) >= 4.5)
            assertTrue(it.label + " body", contrast(0xFFFFFFFF, it.panel) >= 7)
        }
    }
    @Test fun namedThemesChangeSurfacesAndPreserveStoredChoices() {
        val themes = listOf(ColorPreset.Amber, ColorPreset.Flix, ColorPreset.Prime, ColorPreset.Max, ColorPreset.Cinema)
        assertEquals(5, themes.map { it.background }.distinct().size)
        assertEquals(4, themes.map { it.panel }.distinct().size)
        listOf("Amber", "Flix", "Prime", "Max", "Cinema").forEach { key ->
            assertEquals(key, AppearanceOptions.restore(key, 1, false, true).preset.name)
        }
        listOf("Green", "Blue", "Violet").forEach { key ->
            val restored = AppearanceOptions.restore(key, 1, false, true)
            assertEquals(ColorPreset.Amber, restored.preset)
            assertTrue(restored.coverBackground)
            assertEquals(115, restored.textPercentage)
        }
    }
}
