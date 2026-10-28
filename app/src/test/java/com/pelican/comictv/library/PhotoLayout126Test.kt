package com.pelican.comictv.library

import com.pelican.comictv.ui.*
import org.junit.Assert.*
import org.junit.Test

class PhotoLayout126Test {
    @Test fun textOnlyActionsApplyOnlyToTheThreeThemesNamedByTheUser() {
        ColorPreset.entries.forEach { preset ->
            assertEquals(preset in setOf(ColorPreset.Flix, ColorPreset.Cinema, ColorPreset.Max),
                ThemeSpec.forPreset(preset).hero.textOnlyActions)
        }
    }
    @Test fun croppingIsLimitedToTheExplicitlyMarkedFrames() {
        val comic = ThemeSpec.forPreset(ColorPreset.Amber)
        val flix = ThemeSpec.forPreset(ColorPreset.Flix)
        val prime = ThemeSpec.forPreset(ColorPreset.Prime)
        assertTrue(comic.hero.cropPoster && comic.cards.cropRecentCover && comic.cards.cropCollectionCover)
        assertTrue(flix.cards.cropRecentCover)
        assertFalse(flix.cards.cropCollectionCover)
        assertTrue(prime.cards.cropRecentCover && prime.cards.cropCollectionCover)
        assertFalse(prime.hero.cropPoster)
        listOf(ColorPreset.Max, ColorPreset.Cinema, ColorPreset.Green).forEach {
            val spec = ThemeSpec.forPreset(it)
            assertFalse(spec.cards.cropRecentCover || spec.cards.cropCollectionCover || spec.hero.cropPoster)
        }
    }
    @Test fun comicControlsLeaveRoomForBiggerPosterAndFlixSearchLivesInNavigation() {
        val comic = ThemeSpec.forPreset(ColorPreset.Amber)
        assertTrue(comic.footerControls && comic.navigation.libraryBrand && comic.navigation.staticCollapsedRail)
        assertTrue(comic.hero.posterHeightDp > 170 && comic.hero.posterWidthDp > 147)
        val flix = ThemeSpec.forPreset(ColorPreset.Flix)
        assertFalse(flix.topSearch)
        assertTrue(flix.navigation.searchInNavigation)
    }
    @Test fun footerManageIndexIncludesEveryPrecedingAction() {
        assertEquals(4, libraryFooterManageIndex(0, false))
        assertEquals(9, libraryFooterManageIndex(5, false))
        assertEquals(10, libraryFooterManageIndex(5, true))
    }
}
