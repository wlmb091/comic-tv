package com.pelican.comictv.ui

import org.junit.Assert.*
import org.junit.Test

class HomeFocusSafetyTest {
    @Test fun overflowRoundsUpAndIncludesBorderAndGlow() {
        assertEquals(24, focusOverflowDp(260f, 1.05f, 3f))
        assertEquals(78, focusOverflowDp(1200f, 1.125f, 3f))
        assertEquals(37, focusOverflowDp(399f, 1.125f, 3f, 9f))
        assertEquals(24, focusOverflowDp(200f, 1f, 0f))
    }

    @Test fun oversizeCardsIncreaseBothViewportAndFinalScrollReserve() {
        val overflow = focusOverflowDp(1600f, 1.1f, 3f, 6f)
        assertTrue(overflow >= 89)
        assertEquals(overflow, HomeSafeArea.horizontalInsetDp(overflow))
        assertEquals(27 + overflow, HomeSafeArea.bottomInsetDp(overflow))
        assertEquals(48, HomeSafeArea.horizontalInsetDp(24))
        assertEquals(51, HomeSafeArea.bottomInsetDp(24))
    }

    @Test fun actualCardMeasurementsKeepEveryThemeInsideMinimumReserve() {
        listOf(ColorPreset.Amber, ColorPreset.Flix, ColorPreset.Prime, ColorPreset.Max, ColorPreset.Cinema).forEach { preset ->
            val theme = ThemeSpec.forPreset(preset)
            assertEquals(48, theme.navigation.safeHorizontalDp)
            assertEquals(27, theme.navigation.safeVerticalDp)
            // At 85–130%, reserve includes the larger measured dimension and shipped scale/border.
            listOf(.85f, 1f, 1.3f).forEach { textScale ->
                val dimension = maxOf(theme.cards.recentWidthDp.toFloat(), theme.cards.recentHeightDp * textScale)
                val overflow = focusOverflowDp(dimension, theme.focus.recentScale, theme.focus.cardBorderDp.toFloat())
                assertTrue(overflow >= (theme.focus.recentScale - 1f) / 2f * dimension + theme.focus.cardBorderDp)
            }
        }
    }
}
