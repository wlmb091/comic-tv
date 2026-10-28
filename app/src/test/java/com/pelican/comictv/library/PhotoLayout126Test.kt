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
    @Test fun stretchingIsLimitedToTheExplicitlyMarkedFrames() {
        val comic = ThemeSpec.forPreset(ColorPreset.Amber)
        val flix = ThemeSpec.forPreset(ColorPreset.Flix)
        val prime = ThemeSpec.forPreset(ColorPreset.Prime)
        assertTrue(comic.hero.stretchPoster && comic.cards.stretchRecentCover)
        assertTrue(flix.cards.stretchRecentCover)
        assertFalse(flix.cards.stretchCollectionCover)
        assertTrue(prime.cards.stretchRecentCover && prime.cards.stretchCollectionCover)
        assertFalse(prime.hero.stretchPoster)
        listOf(ColorPreset.Max, ColorPreset.Cinema, ColorPreset.Green).forEach {
            val spec = ThemeSpec.forPreset(it)
            assertFalse(spec.cards.stretchRecentCover || spec.cards.stretchCollectionCover || spec.hero.stretchPoster)
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
