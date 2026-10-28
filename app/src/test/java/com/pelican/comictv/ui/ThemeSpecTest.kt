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
            assertEquals(preset != ColorPreset.Amber, spec.footerControls)
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
        assertEquals(CollectionCardStyle.Horizontal, max.cards.collectionStyle)
        assertEquals(220, max.cards.gridMinimumWidthDp)
        assertEquals(203, max.cards.recentWidthDp)
        assertEquals(196, max.hero.viewportInsetDp)
        assertEquals(180, max.hero.minimumHeightDp)
        assertTrue(ThemeSpec.forPreset(ColorPreset.Cinema).navigation.expandedDrawerBrand)
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
        assertEquals(45, max.typography.titleSp)
        assertEquals(49, max.typography.titleLineHeightSp)
        assertEquals(2, max.hero.titleMaxLines)
        assertEquals(0xFF9B6DFF, max.colors.focusAccent)
        assertTrue(max.colors.outlinedHeroAction)
        assertEquals(1.05f, max.navigation.dockFocusScale, 0f)
        assertEquals(1.05f, max.focus.recentScale, 0f)
        assertTrue(max.motion.navigationIconMs in 150..300)
        assertEquals(27, max.navigation.safeVerticalDp)
    }

}
