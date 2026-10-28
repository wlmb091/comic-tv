package com.pelican.comictv.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

internal enum class HeroVariant { PosterLeft, FullbleedText, FullbleedCarousel }
internal enum class HeroHeightPolicy { Fraction, ViewportInset, ReservedReadingRow }
internal enum class CollectionCardStyle { Poster, Horizontal }
internal enum class ContinueCardStyle { ThumbnailRow }
internal enum class ThemeTitleFamily { Default, Serif, Bebas }

@Immutable internal data class ThemeNavigationSpec(
    val layout: ThemeNavigationPolicy, val framelessSidebar: Boolean = false,
    val shellBackdrop: Boolean = false, val libraryBrand: Boolean = false,
    val expandedDrawerBrand: Boolean = false, val searchInNavigation: Boolean = false,
    val safeHorizontalDp: Int = 20, val safeVerticalDp: Int = 16,
    val dockLabelSp: Int = 12, val dockActiveAlpha: Float = .15f,
    val dockFocusScale: Float = 1f, val iconFocusScale: Float = 1.08f,
    val dockSurface: Long? = null, val dockBorder: Long? = null,
)
@Immutable internal data class ThemeHeroSpec(
    val variant: HeroVariant, val heightPolicy: HeroHeightPolicy = HeroHeightPolicy.Fraction,
    val viewportFraction: Float = .63f, val viewportInsetDp: Int = 0,
    val minimumHeightDp: Int = 230, val bottomAligned: Boolean = false,
    val horizontalPaddingDp: Int = 14, val verticalPaddingDp: Int = 12,
    val textWidthFraction: Float = .61f, val contentGapDp: Int = 10,
    val titleMaxLines: Int = 3, val progressWidthDp: Int = 240, val carouselChoices: Int = 3,
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
)
@Immutable internal data class ThemeFocusSpec(
    val buttonBorderDp: Int = 2, val highContrastBorderDp: Int = 3,
    val cardBorderDp: Int = 3, val buttonScale: Float = 1.03f,
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
    val whitePrimaryAction: Boolean = false, val artworkActionAlpha: Float = .68f, val glassAlpha: Float = .80f,
)
@Immutable internal data class ThemeShapeSpec(val controlRadiusDp: Int = 8, val posterRadiusDp: Int = 6)

/** Shipped composition, before applying the frontend package's new geometry. */
@Immutable internal data class ThemeSpec(
    val navigation: ThemeNavigationSpec, val hero: ThemeHeroSpec,
    val cards: ThemeCardSpec, val typography: ThemeTypographySpec, val colors: ThemeColorSpec,
    val focus: ThemeFocusSpec = ThemeFocusSpec(), val shapes: ThemeShapeSpec = ThemeShapeSpec(),
    val motion: ThemeMotionSpec = ThemeMotionSpec(), val footerControls: Boolean = false,
    val topSearch: Boolean = false, val artwork: Boolean = false,
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
                ColorPreset.Flix -> editorial.copy(
                    navigation = ThemeNavigationSpec(ThemeNavigationPolicy.Sidebar, framelessSidebar = true, shellBackdrop = true),
                    hero = ThemeHeroSpec(HeroVariant.FullbleedText), typography = ThemeTypographySpec(34, 37),
                    footerControls = true, topSearch = true, artwork = true)
                ColorPreset.Prime -> editorial.copy(
                    navigation = ThemeNavigationSpec(ThemeNavigationPolicy.TopBar, shellBackdrop = true, searchInNavigation = true),
                    hero = ThemeHeroSpec(HeroVariant.FullbleedCarousel, viewportFraction = .70f, minimumHeightDp = 284),
                    typography = ThemeTypographySpec(32, 36), footerControls = true, artwork = true)
                ColorPreset.Max -> editorial.copy(
                    navigation = ThemeNavigationSpec(ThemeNavigationPolicy.BottomDock, shellBackdrop = true,
                        libraryBrand = true, searchInNavigation = true,
                        safeHorizontalDp = 48, safeVerticalDp = 27, dockLabelSp = 14,
                        dockActiveAlpha = .4f, dockFocusScale = 1.05f, iconFocusScale = 1f,
                        dockSurface = 0xD11E1440, dockBorder = 0x739B6DFF),
                    hero = ThemeHeroSpec(HeroVariant.FullbleedText, HeroHeightPolicy.ReservedReadingRow,
                        viewportInsetDp = 196, minimumHeightDp = 180, bottomAligned = true, titleMaxLines = 2,
                        horizontalPaddingDp = 0, verticalPaddingDp = 6, contentGapDp = 8,
                        textWidthFraction = .68f, progressWidthDp = 255),
                    cards = ThemeCardSpec(collectionStyle = CollectionCardStyle.Horizontal,
                        gridMinimumWidthDp = 220, scaleGridWithText = true, recentWidthDp = 203,
                        recentHeightDp = 96, recentThumbnailWidthDp = 52.5f, recentThumbnailHeightDp = 81,
                        recentPaddingDp = 7, recentGapDp = 8, gridHorizontalPaddingDp = 0, gridVerticalPaddingDp = 0,
                        recentSurface = 0xB81E1440, recentBorder = 0x669B6DFF),
                    typography = ThemeTypographySpec(45, 49, ThemeTitleFamily.Bebas, titleExtraBold = false,
                        cardTitleSp = 14, cardStatusSp = 14, cardLineHeightSp = 18, badgeSp = 14),
                    colors = colors.copy(background = 0xFF0B0818, focusAccent = 0xFF9B6DFF,
                        action = 0xFF281850, onAction = 0xFFFFFFFF, outlinedHeroAction = true,
                        heroPrimarySurface = 0xE6281850),
                    focus = ThemeFocusSpec(buttonScale = 1.05f, recentScale = 1.05f, collectionScale = 1.05f),
                    motion = ThemeMotionSpec(navigationIconMs = 180), footerControls = true, artwork = true)
                ColorPreset.Cinema -> editorial.copy(
                    navigation = ThemeNavigationSpec(ThemeNavigationPolicy.ImmersiveMenu,
                        expandedDrawerBrand = true, searchInNavigation = true),
                    hero = ThemeHeroSpec(HeroVariant.FullbleedText, HeroHeightPolicy.ViewportInset,
                        viewportInsetDp = 32, bottomAligned = true, horizontalPaddingDp = 32,
                        verticalPaddingDp = 38, textWidthFraction = .65f, contentGapDp = 13),
                    typography = ThemeTypographySpec(42, 46, ThemeTitleFamily.Serif, titleExtraBold = false),
                    footerControls = true, artwork = true)
                else -> editorial // Legacy colors retain their shipped editorial composition.
            }
        }
        fun forPreset(preset: ColorPreset): ThemeSpec = specs.getValue(preset)
    }
}

internal val LocalThemeSpec = staticCompositionLocalOf { ThemeSpec.forPreset(ColorPreset.Amber) }
