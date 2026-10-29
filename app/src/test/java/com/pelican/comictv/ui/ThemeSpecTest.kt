package com.pelican.comictv.ui

import org.junit.Assert.*
import org.junit.Test

class ThemeSpecTest {
    @Test fun fiveThemesKeepTheirShippedComposition() {
        val expected = listOf(
            Triple(ColorPreset.Amber, ThemeNavigationPolicy.Sidebar, HeroVariant.PosterLeft),
            Triple(ColorPreset.Flix, ThemeNavigationPolicy.Sidebar, HeroVariant.FullbleedText),
            Triple(ColorPreset.Prime, ThemeNavigationPolicy.TopBar, HeroVariant.FullbleedCarousel),
            Triple(ColorPreset.Max, ThemeNavigationPolicy.BottomDock, HeroVariant.FullbleedText),
            Triple(ColorPreset.Cinema, ThemeNavigationPolicy.ImmersiveMenu, HeroVariant.FullbleedText),
        )
        expected.forEach { (preset, navigation, hero) ->
            val spec = ThemeSpec.forPreset(preset)
            assertEquals(navigation, spec.navigation.layout)
            assertEquals(hero, spec.hero.variant)
            assertTrue(spec.footerControls)
            assertEquals(preset != ColorPreset.Amber, spec.artwork)
        }
    }

    @Test fun approvedSearchCarouselDrawerAndCurrentGeometryArePreserved() {
        val prime = ThemeSpec.forPreset(ColorPreset.Prime)
        assertTrue(prime.navigation.searchInNavigation)
        assertFalse(prime.topSearch)
        assertEquals(3, prime.hero.carouselChoices)
        assertEquals(.70f, prime.hero.viewportFraction, 0f)
        assertTrue(prime.colors.whitePrimaryAction)
        val max = ThemeSpec.forPreset(ColorPreset.Max)
        assertTrue(max.navigation.searchInNavigation) // Six destinations plus Buscar.
        assertTrue(max.navigation.libraryBrand)
        assertEquals(CollectionCardStyle.Poster, max.cards.collectionStyle)
        assertEquals(156, max.cards.gridMinimumWidthDp)
        assertEquals(203, max.cards.recentWidthDp)
        assertEquals(196, max.hero.viewportInsetDp)
        assertEquals(180, max.hero.minimumHeightDp)
        assertTrue(ThemeSpec.forPreset(ColorPreset.Cinema).navigation.expandedDrawerBrand)
    }

    @Test fun primeHasCondensedTitleAndLandscapeReadingCardsWithoutChangingCarousel() {
        val prime = ThemeSpec.forPreset(ColorPreset.Prime)
        assertEquals(ThemeTitleFamily.Barlow, prime.typography.titleFamily)
        assertEquals(48, prime.typography.titleSp)
        assertEquals(2, prime.hero.titleMaxLines)
        assertEquals(ContinueCardStyle.Art16x9, prime.cards.continueStyle)
        assertEquals(CollectionCardStyle.Mini, prime.cards.collectionStyle)
        assertEquals(203, prime.cards.recentWidthDp)
        assertTrue(prime.cards.gridMinimumWidthDp >= 140)
        assertTrue(prime.typography.cardStatusSp >= 14)
        assertTrue(prime.typography.badgeSp >= 14)
        assertEquals(3, prime.hero.carouselChoices)
        assertEquals(.70f, prime.hero.viewportFraction, 0f)
    }

    @Test fun maxCollectionHasLargerPortraitCoversThanRecentThumbnails() {
        val max = ThemeSpec.forPreset(ColorPreset.Max)
        assertEquals(CollectionCardStyle.Poster, max.cards.collectionStyle)
        assertTrue(max.cards.gridMinimumWidthDp > max.cards.recentThumbnailWidthDp * 2)
        assertEquals(ContinueCardStyle.Compact, max.cards.continueStyle)
    }

    @Test fun legacyIdsRetainEditorialPolicyAndOwnColors() {
        listOf(ColorPreset.Green, ColorPreset.Blue, ColorPreset.Violet).forEach { preset ->
            val spec = ThemeSpec.forPreset(preset)
            assertEquals(ThemeNavigationPolicy.Sidebar, spec.navigation.layout)
            assertEquals(HeroVariant.PosterLeft, spec.hero.variant)
            assertEquals(206, spec.hero.minimumHeightDp)
            assertEquals(28, spec.typography.titleSp)
            assertFalse(spec.footerControls)
            assertFalse(spec.colors.transparentActions)
            assertEquals(preset.background, spec.colors.background)
            assertEquals(preset.accent, spec.colors.accent)
            assertEquals(ColorPreset.Amber, AppearanceOptions.restore(preset.name, 0, false).preset)
        }
    }

    @Test fun neutralPaletteAndMaterialDescriptorsKeepExistingValues() {
        ColorPreset.entries.forEach { preset ->
            val spec = ThemeSpec.forPreset(preset)
            val neutral = preset in setOf(ColorPreset.Amber, ColorPreset.Flix, ColorPreset.Cinema)
            assertEquals(if (neutral) 0xFF0F0F0F else if (preset == ColorPreset.Max) 0xFF0B0818 else preset.background, spec.colors.background)
            assertEquals(8, spec.shapes.controlRadiusDp)
            assertEquals(160, spec.motion.artworkCrossfadeMs)
            assertEquals(180L, spec.motion.artworkSettleMs)
            assertEquals(if (preset == ColorPreset.Max) 1.05f else 1.03f, spec.focus.buttonScale, 0f)
            assertEquals(.68f, spec.colors.artworkActionAlpha, 0f)
        }
    }
    @Test fun maxFourRecentCardsFitSafeViewportWithoutReducingReadableText() {
        val max = ThemeSpec.forPreset(ColorPreset.Max)
        val available = 960 - max.navigation.safeHorizontalDp * 2 - max.cards.gridHorizontalPaddingDp * 2
        val occupied = 4 * max.cards.recentWidthDp + 3 * 14 + 2 * 4
        assertTrue("Four cards plus gaps fit the safe viewport", occupied <= available)
        assertEquals(96, max.cards.recentHeightDp)
        assertEquals(52.5f, max.cards.recentThumbnailWidthDp, 0f)
        assertEquals(81, max.cards.recentThumbnailHeightDp)
        assertTrue(max.typography.cardTitleSp >= 14)
        assertTrue(max.typography.cardStatusSp >= 14)
        assertTrue(max.typography.badgeSp >= 14)
        assertTrue(max.navigation.dockLabelSp >= 14)
    }

    @Test fun maxCondensedTitleAndFocusPreserveApprovedDirection() {
        val max = ThemeSpec.forPreset(ColorPreset.Max)
        assertEquals(ThemeTitleFamily.Bebas, max.typography.titleFamily)
        assertEquals(40, max.typography.titleSp)
        assertEquals(44, max.typography.titleLineHeightSp)
        assertEquals(2, max.hero.titleMaxLines)
        assertEquals(0xFF9B6DFF, max.colors.focusAccent)
        assertTrue(max.colors.outlinedHeroAction)
        assertEquals(1.05f, max.navigation.dockFocusScale, 0f)
        assertEquals(1.05f, max.focus.recentScale, 0f)
        assertTrue(max.motion.navigationIconMs in 150..300)
        assertEquals(27, max.navigation.safeVerticalDp)
    }

    @Test fun remainingThemesHaveDistinctReadableCardCompositions() {
        val comic = ThemeSpec.forPreset(ColorPreset.Amber)
        val flix = ThemeSpec.forPreset(ColorPreset.Flix)
        val cinema = ThemeSpec.forPreset(ColorPreset.Cinema)
        assertEquals(CollectionCardStyle.Square, comic.cards.collectionStyle)
        assertEquals(ContinueCardStyle.Compact, comic.cards.continueStyle)
        assertTrue(comic.hero.posterWidthDp < comic.hero.posterHeightDp)
        assertTrue(comic.cards.gridMinimumWidthDp > comic.cards.recentThumbnailWidthDp)
        assertEquals(ContinueCardStyle.ArtLarge, flix.cards.continueStyle)
        assertEquals(ThemeTitleFamily.Bebas, flix.typography.titleFamily)
        assertEquals(ContinueCardStyle.GlassSerif, cinema.cards.continueStyle)
        assertEquals(ThemeTitleFamily.Serif, cinema.typography.titleFamily)
        assertTrue(flix.hero.bottomAligned && cinema.hero.bottomAligned)
        listOf(comic, flix, cinema).forEach { spec ->
            assertTrue(spec.typography.cardTitleSp >= 14)
            assertTrue(spec.typography.cardStatusSp >= 14)
            assertTrue(spec.typography.badgeSp >= 14)
            assertEquals(2, spec.hero.titleMaxLines)
            assertTrue(spec.cards.scaleGridWithText)
        }
    }

    @Test fun flixPrimaryFillRemainsDistinctFromFocusAndReadableTextAccent() {
        val flix = ThemeSpec.forPreset(ColorPreset.Flix)
        assertTrue(flix.colors.filledHeroAction)
        assertFalse(flix.colors.outlinedHeroAction)
        assertTrue(flix.hero.playIcon)
        assertNotNull(flix.colors.heroPrimarySurface)
        assertNotEquals(flix.colors.accent, flix.colors.focusAccent)
        assertFalse(ThemeSpec.forPreset(ColorPreset.Cinema).colors.filledHeroAction)
    }

}
