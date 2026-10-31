package com.pelican.comictv.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

internal enum class HeroVariant { PosterLeft, FullbleedText, FullbleedCarousel }
internal enum class HeroHeightPolicy { Fraction, ViewportInset, ReservedReadingRow }
internal enum class CollectionCardStyle { Poster, Horizontal, Square, Mini }
internal enum class ContinueCardStyle { ThumbnailRow, Compact, Art16x9, ArtLarge, GlassSerif }
internal enum class ThemeTitleFamily { Default, Serif, Bebas, Barlow }

@Immutable internal data class ThemeNavigationSpec(
    val layout: ThemeNavigationPolicy, val framelessSidebar: Boolean = false,
    val shellBackdrop: Boolean = false, val libraryBrand: Boolean = false,
    val expandedDrawerBrand: Boolean = false, val searchInNavigation: Boolean = false,
    val safeHorizontalDp: Int = HomeSafeArea.horizontalDp, val safeVerticalDp: Int = HomeSafeArea.verticalDp,
    val dockLabelSp: Int = 12, val dockActiveAlpha: Float = .15f,
    val dockFocusScale: Float = 1f, val iconFocusScale: Float = 1.08f,
    val dockSurface: Long? = null, val dockBorder: Long? = null,
    val sidebarInitiallyExpanded: Boolean = false,
    val sidebarExpandedWidthDp: Int = 220, val sidebarCollapsedWidthDp: Int = 64,
    val overlayDurationMs: Int = 220,
    val staticCollapsedRail: Boolean = false,
)
@Immutable internal data class ThemeHeroSpec(
    val variant: HeroVariant, val heightPolicy: HeroHeightPolicy = HeroHeightPolicy.Fraction,
    val viewportFraction: Float = .63f, val viewportInsetDp: Int = 0,
    val minimumHeightDp: Int = 230, val bottomAligned: Boolean = false,
    val horizontalPaddingDp: Int = 14, val verticalPaddingDp: Int = 12,
    val textWidthFraction: Float = .61f, val contentGapDp: Int = 10,
    val titleMaxLines: Int = 3, val progressWidthDp: Int = 240, val carouselChoices: Int = 3,
    val playIcon: Boolean = false,
    val posterWidthDp: Int = 142, val posterHeightDp: Int = 206,
    val posterBorderDp: Int = 1, val posterGapDp: Int = 22,
    val cropPoster: Boolean = false,
    val textOnlyActions: Boolean = false,
) { val editorial get() = variant == HeroVariant.PosterLeft }
@Immutable internal data class ThemeCardSpec(
    val continueStyle: ContinueCardStyle = ContinueCardStyle.ThumbnailRow,
    val collectionStyle: CollectionCardStyle = CollectionCardStyle.Poster,
    val gridMinimumWidthDp: Int = 140, val scaleGridWithText: Boolean = false,
    val recentWidthDp: Int = 288, val recentHeightDp: Int = 0,
    val recentThumbnailWidthDp: Float = 44f, val recentThumbnailHeightDp: Int = 64,
    val recentPaddingDp: Int = 10, val recentGapDp: Int = 12,
    val gridHorizontalPaddingDp: Int = 12, val recentSurface: Long? = null,
    val gridVerticalPaddingDp: Int? = null,
    val recentBorder: Long? = null,
    val cropRecentCover: Boolean = false, val cropCollectionCover: Boolean = false,
)
@Immutable internal data class ThemeFocusSpec(
    val buttonBorderDp: Int = 3, val highContrastBorderDp: Int = 3,
    val cardBorderDp: Int = 4, val buttonScale: Float = 1.03f,
    val recentScale: Float = 1.025f, val collectionScale: Float = 1.03f,
)
@Immutable internal data class ThemeTypographySpec(
    val titleSp: Int, val titleLineHeightSp: Int,
    val titleFamily: ThemeTitleFamily = ThemeTitleFamily.Default, val titleExtraBold: Boolean = true,
    val bodySp: Int = 15, val cardTitleSp: Int = 16, val cardStatusSp: Int = 13,
    val cardLineHeightSp: Int = 20, val badgeSp: Int = 11,
)
@Immutable internal data class ThemeMotionSpec(
    val artworkSettleMs: Long = 180, val artworkCrossfadeMs: Int = 160, val navigationIconMs: Int = 120,
)
@Immutable internal data class ThemeColorSpec(
    val background: Long, val panel: Long, val accent: Long, val action: Long,
    val onAction: Long, val track: Long, val neutral: Boolean, val transparentActions: Boolean,
    val focusAccent: Long = accent,
    val outlinedHeroAction: Boolean = false, val heroPrimarySurface: Long? = null,
    val filledHeroAction: Boolean = false,
    val whitePrimaryAction: Boolean = false, val artworkActionAlpha: Float = .68f, val glassAlpha: Float = .80f,
)
@Immutable internal data class ThemeShapeSpec(val controlRadiusDp: Int = 8, val posterRadiusDp: Int = 6)

/** Native composition and accessible geometry for each approved Home theme. */
@Immutable internal data class ThemeSpec(
    val navigation: ThemeNavigationSpec, val hero: ThemeHeroSpec,
    val cards: ThemeCardSpec, val typography: ThemeTypographySpec, val colors: ThemeColorSpec,
    val focus: ThemeFocusSpec = ThemeFocusSpec(), val shapes: ThemeShapeSpec = ThemeShapeSpec(),
    val motion: ThemeMotionSpec = ThemeMotionSpec(), val footerControls: Boolean = false,
    val topSearch: Boolean = false, val artwork: Boolean = false,
    val exploreCollections: Boolean = false,
) {
    companion object {
        private val specs = ColorPreset.entries.associateWith { preset ->
            val neutral = preset in setOf(ColorPreset.Amber, ColorPreset.Flix, ColorPreset.Cinema)
            val colors = ThemeColorSpec(if (neutral) 0xFF0F0F0F else preset.background,
                preset.panel, preset.accent, preset.action, preset.onAction, preset.track, neutral,
                preset in setOf(ColorPreset.Flix, ColorPreset.Prime, ColorPreset.Max, ColorPreset.Cinema),
                whitePrimaryAction = preset == ColorPreset.Prime)
            val editorial = ThemeSpec(ThemeNavigationSpec(ThemeNavigationPolicy.Sidebar),
                ThemeHeroSpec(HeroVariant.PosterLeft, minimumHeightDp = 206, textWidthFraction = .94f,
                    progressWidthDp = 280), ThemeCardSpec(), ThemeTypographySpec(28, 32), colors)
            when (preset) {
                ColorPreset.Amber -> editorial.copy(
                    navigation = editorial.navigation.copy(libraryBrand = true, staticCollapsedRail = true, searchInNavigation = true),
                    footerControls = true,
                    hero = editorial.hero.copy(minimumHeightDp = 194, titleMaxLines = 2,
                        posterWidthDp = 190, posterHeightDp = 250, cropPoster = true, posterBorderDp = 2,
                        posterGapDp = 15, progressWidthDp = 225, contentGapDp = 8, playIcon = true),
                    cards = ThemeCardSpec(continueStyle = ContinueCardStyle.Compact,
                        collectionStyle = CollectionCardStyle.Square, gridMinimumWidthDp = 146,
                        scaleGridWithText = true, recentWidthDp = 248, recentHeightDp = 90,
                        recentThumbnailWidthDp = 82.5f, recentThumbnailHeightDp = 72,
                        recentPaddingDp = 8, recentGapDp = 8, cropRecentCover = true, cropCollectionCover = true),
                    typography = ThemeTypographySpec(33, 37, cardTitleSp = 14,
                        cardStatusSp = 14, cardLineHeightSp = 18, badgeSp = 14),
                    colors = colors.copy(focusAccent = 0xFFFFE14D, outlinedHeroAction = true, heroPrimarySurface = 0x99000000))
                ColorPreset.Flix -> editorial.copy(
                    navigation = ThemeNavigationSpec(ThemeNavigationPolicy.Sidebar, framelessSidebar = true, shellBackdrop = true,
                        sidebarInitiallyExpanded = true, searchInNavigation = true),
                    hero = ThemeHeroSpec(HeroVariant.FullbleedText, viewportFraction = .72f,
                        minimumHeightDp = 284, bottomAligned = true, titleMaxLines = 2,
                        playIcon = true, verticalPaddingDp = 18, contentGapDp = 8, textOnlyActions = true),
                    cards = ThemeCardSpec(continueStyle = ContinueCardStyle.ArtLarge,
                        collectionStyle = CollectionCardStyle.Poster, recentWidthDp = 180,
                        recentHeightDp = 114, gridMinimumWidthDp = 140, scaleGridWithText = true, cropRecentCover = true),
                    typography = ThemeTypographySpec(50, 51, ThemeTitleFamily.Bebas,
                        titleExtraBold = false, cardTitleSp = 16, cardStatusSp = 14,
                        cardLineHeightSp = 18, badgeSp = 14),
                    colors = colors.copy(focusAccent = 0xFFFF2E3A, filledHeroAction = true,
                        heroPrimarySurface = 0xFFB20710),
                    footerControls = true, topSearch = false, artwork = true)
                ColorPreset.Prime -> editorial.copy(
                    navigation = ThemeNavigationSpec(ThemeNavigationPolicy.TopBar, shellBackdrop = true, searchInNavigation = true),
                    hero = ThemeHeroSpec(HeroVariant.FullbleedCarousel, viewportFraction = .70f, minimumHeightDp = 284, titleMaxLines = 2),
                    cards = ThemeCardSpec(continueStyle = ContinueCardStyle.Art16x9,
                        collectionStyle = CollectionCardStyle.Mini, gridMinimumWidthDp = 203,
                        scaleGridWithText = true, recentWidthDp = 203, recentHeightDp = 114,
                        cropRecentCover = true, cropCollectionCover = true),
                    typography = ThemeTypographySpec(48, 51, ThemeTitleFamily.Barlow,
                        cardTitleSp = 16, cardStatusSp = 14, badgeSp = 14),
                    colors = colors.copy(focusAccent = 0xFF9DE8FF), footerControls = true, artwork = true)
                ColorPreset.Max -> editorial.copy(
                    navigation = ThemeNavigationSpec(ThemeNavigationPolicy.BottomDock, shellBackdrop = true,
                        libraryBrand = true, searchInNavigation = true,
                        safeHorizontalDp = 48, safeVerticalDp = 27, dockLabelSp = 14,
                        dockActiveAlpha = .4f, dockFocusScale = 1.05f, iconFocusScale = 1f,
                        dockSurface = 0xD11E1440, dockBorder = 0x739B6DFF),
                    hero = ThemeHeroSpec(HeroVariant.FullbleedText, HeroHeightPolicy.ReservedReadingRow,
                        viewportInsetDp = 196, minimumHeightDp = 180, bottomAligned = true, titleMaxLines = 2,
                        horizontalPaddingDp = 12, verticalPaddingDp = 6, contentGapDp = 8,
                        textWidthFraction = .68f, progressWidthDp = 255, textOnlyActions = true),
                    cards = ThemeCardSpec(continueStyle = ContinueCardStyle.Compact,
                        collectionStyle = CollectionCardStyle.Poster,
                        gridMinimumWidthDp = 156, scaleGridWithText = true, recentWidthDp = 203,
                        recentHeightDp = 96, recentThumbnailWidthDp = 52.5f, recentThumbnailHeightDp = 81,
                        recentPaddingDp = 7, recentGapDp = 8, gridHorizontalPaddingDp = 0, gridVerticalPaddingDp = 0,
                        recentSurface = 0xB81E1440, recentBorder = 0x669B6DFF),
                    typography = ThemeTypographySpec(40, 44, ThemeTitleFamily.Bebas, titleExtraBold = false,
                        cardTitleSp = 14, cardStatusSp = 14, cardLineHeightSp = 18, badgeSp = 14),
                    colors = colors.copy(background = 0xFF0B0818, focusAccent = 0xFFD9C2FF,
                        action = 0xFF281850, onAction = 0xFFFFFFFF, outlinedHeroAction = true,
                        heroPrimarySurface = 0xE6281850),
                    focus = ThemeFocusSpec(buttonScale = 1.05f, recentScale = 1.05f, collectionScale = 1.05f),
                    motion = ThemeMotionSpec(navigationIconMs = 180), footerControls = true, artwork = true,
                    exploreCollections = true)
                ColorPreset.Cinema -> editorial.copy(
                    navigation = ThemeNavigationSpec(ThemeNavigationPolicy.ImmersiveMenu,
                        expandedDrawerBrand = true, searchInNavigation = true, sidebarExpandedWidthDp = 245),
                    hero = ThemeHeroSpec(HeroVariant.FullbleedText, HeroHeightPolicy.ViewportInset,
                        viewportInsetDp = 32, bottomAligned = true, horizontalPaddingDp = 32,
                        verticalPaddingDp = 38, textWidthFraction = .65f, contentGapDp = 13,
                        titleMaxLines = 2, textOnlyActions = true),
                    cards = ThemeCardSpec(continueStyle = ContinueCardStyle.GlassSerif,
                        recentWidthDp = 207, recentHeightDp = 96,
                        recentThumbnailWidthDp = 48f, recentThumbnailHeightDp = 81,
                        recentPaddingDp = 7, recentGapDp = 8, recentSurface = 0xB816161C,
                        recentBorder = 0x29FFFFFF, scaleGridWithText = true),
                    typography = ThemeTypographySpec(40, 45, ThemeTitleFamily.Serif,
                        titleExtraBold = false, cardTitleSp = 14, cardStatusSp = 14,
                        cardLineHeightSp = 19, badgeSp = 14),
                    colors = colors.copy(focusAccent = 0xFFFFFFFF),
                    footerControls = true, artwork = true)
                else -> editorial // Legacy colors retain their shipped editorial composition.
            }
        }
        fun forPreset(preset: ColorPreset): ThemeSpec = specs.getValue(preset)
    }
}

internal val LocalThemeSpec = staticCompositionLocalOf { ThemeSpec.forPreset(ColorPreset.Amber) }

/** The hero shares the initial viewport with its brand and first complete content row. */
internal fun homeHeroViewportBudget(viewportDp: Float, requestedHeroDp: Float,
    brandDp: Float, followingRowDp: Float, gridTopDp: Float, gridBottomDp: Float,
    brandGapDp: Float = 10f, rowGapsDp: Float = 20f): Float =
    minOf(requestedHeroDp, (viewportDp - brandDp - (if (brandDp > 0f) brandGapDp else 0f) -
        followingRowDp - gridTopDp - gridBottomDp - (if (followingRowDp > 0f) rowGapsDp else 0f))
        .coerceAtLeast(0f))

/** Budget the complete two-line title after the other hero content, preserving readable sp. */
internal fun homeHeroTitleSizeSp(titleSp: Float, lineHeightSp: Float, maxLines: Int,
    fontScale: Float, availableHeightDp: Float?, otherContentHeightDp: Float): Pair<Float, Float> {
    if (availableHeightDp == null) return titleSp to lineHeightSp
    val lineBudget = ((availableHeightDp - otherContentHeightDp) / maxLines.coerceAtLeast(1) /
        fontScale.coerceAtLeast(1f)).coerceAtLeast(0f)
    val size = minOf(titleSp, lineBudget * titleSp / lineHeightSp).coerceAtLeast(24f)
    return size to maxOf(size * lineHeightSp / titleSp, 26f)
}

/** Shrink the whole Comic poster to the viewport budget, retaining its portrait frame. */
internal fun homePosterSizeDp(widthDp: Float, heightDp: Float, heightLimitDp: Float?): Pair<Float, Float> {
    val height = minOf(heightDp, heightLimitDp?.coerceAtLeast(150f) ?: heightDp)
    return widthDp * height / heightDp to height
}
