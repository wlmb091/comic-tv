package com.pelican.comictv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOrientationTest {
    @Test fun restoreFallsBackToLandscape() {
        assertEquals(AppOrientation.Portrait270, AppOrientation.restore("Portrait270"))
        assertEquals(AppOrientation.Landscape, AppOrientation.restore("Sideways"))
        assertEquals(AppOrientation.Landscape, AppOrientation.restore(null))
    }

    @Test fun phoneShortcutCyclesThroughEveryOrientation() {
        assertEquals(AppOrientation.Portrait90, AppOrientation.Landscape.next())
        assertEquals(AppOrientation.Portrait270, AppOrientation.Portrait90.next())
        assertEquals(AppOrientation.Landscape, AppOrientation.Portrait270.next())
    }

    @Test fun viewerCornersMapToThePhysicalWindow() {
        // Clockwise: what the viewer sees at the top-left sits at the window's top-right.
        assertEquals(1f to 0f, AppOrientation.Portrait90.windowPoint(0f, 0f))
        assertEquals(1f to 1f, AppOrientation.Portrait90.windowPoint(1f, 0f))
        assertEquals(0f to 1f, AppOrientation.Portrait270.windowPoint(0f, 0f))
        assertEquals(0f to 0f, AppOrientation.Portrait270.windowPoint(1f, 0f))
        assertEquals(.25f to .75f, AppOrientation.Landscape.windowPoint(.25f, .75f))
    }

    @Test fun landscapeSpecsStayUntouched() {
        ColorPreset.entries.forEach { assertEquals(ThemeSpec.forPreset(it), ThemeSpec.forPreset(it, false)) }
    }

    @Test fun portraitKeepsEachThemesMenuIdentity() {
        val comic = ThemeSpec.forPreset(ColorPreset.Amber, true).navigation
        assertEquals(ThemeNavigationPolicy.BottomDock, comic.layout)
        assertTrue(comic.dockIconsOnly)
        val flix = ThemeSpec.forPreset(ColorPreset.Flix, true).navigation
        assertEquals(ThemeNavigationPolicy.Sidebar, flix.layout)
        assertFalse(flix.sidebarInitiallyExpanded)
        assertTrue(ThemeSpec.forPreset(ColorPreset.Prime, true).navigation.topBarStacked)
        val max = ThemeSpec.forPreset(ColorPreset.Max, true).navigation
        assertFalse(max.dockIconsOnly)
        assertEquals(92, max.dockScrollItemWidthDp)
        assertEquals(ThemeNavigationPolicy.ImmersiveMenu, ThemeSpec.forPreset(ColorPreset.Cinema, true).navigation.layout)
        assertNull(ThemeSpec.forPreset(ColorPreset.Cinema, true).navigation.dockScrollItemWidthDp)
    }
}
