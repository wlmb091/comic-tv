package com.pelican.comictv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeNavigationPolicyTest {
    @Test fun primaryThemesUseTheirApprovedNavigationPlacement() {
        assertEquals(ThemeNavigationPolicy.Sidebar, ThemeNavigationPolicy.forPreset(ColorPreset.Amber))
        assertEquals(ThemeNavigationPolicy.Sidebar, ThemeNavigationPolicy.forPreset(ColorPreset.Flix))
        assertEquals(ThemeNavigationPolicy.TopBar, ThemeNavigationPolicy.forPreset(ColorPreset.Prime))
        assertEquals(ThemeNavigationPolicy.BottomDock, ThemeNavigationPolicy.forPreset(ColorPreset.Max))
        assertEquals(ThemeNavigationPolicy.ImmersiveMenu, ThemeNavigationPolicy.forPreset(ColorPreset.Cinema))
    }

    @Test fun legacyPersistedColorsRetainSidebarNavigation() {
        listOf(ColorPreset.Green, ColorPreset.Blue, ColorPreset.Violet).forEach {
            assertEquals(ThemeNavigationPolicy.Sidebar, ThemeNavigationPolicy.forPreset(it))
        }
    }
}
