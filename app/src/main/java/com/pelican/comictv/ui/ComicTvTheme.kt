package com.pelican.comictv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*

object TvColors {
    internal var options by mutableStateOf(AppearanceOptions())
    private val colors get() = ThemeSpec.forPreset(options.preset).colors
    val NeutralBackground = Color(0xFF0F0F0F)
    val Background get() = if (options.highContrast) Color.Black else
        Color(colors.background)
    val Panel get() = if (options.highContrast) Color.Black else Color(colors.panel)
    internal val useNeutralButtons get() = !options.highContrast && options.preset.hasNeutralBackground
    val ButtonSurface get() = if (useNeutralButtons) Color(0xFF181818) else Panel
    internal val FrostedPanel get() = if (options.highContrast) Color.Black else Panel.copy(alpha = colors.glassAlpha)
    internal val FrostedSidebar get() = if (options.highContrast) Color.Black else
        if (options.preset.hasNeutralBackground) Color(0xFF404040).copy(alpha = .80f) else FrostedPanel
    val Muted get() = if (options.highContrast) Color.White else Color(0xFFCFD1D5)
    val Amber get() = Color(colors.accent)
    val FocusAccent get() = Color(colors.focusAccent)
    val Ink = Color(0xFF101010)
    val Action get() = if (useNeutralButtons) Color(0xFF303030) else Color(colors.action)
    val OnAction get() = if (useNeutralButtons) Color.White else Color(colors.onAction)
    val Track get() = if (options.highContrast) Color(0xFF606060) else Color(colors.track)
}

internal val ColorPreset.hasNeutralBackground: Boolean
    get() = ThemeSpec.forPreset(this).colors.neutral

/** Opt-in material for actions laid over streaming artwork; never fades labels or icons. */
internal fun transparentButtonSurface(options: AppearanceOptions): Color? {
    val preset = options.preset
    if (!ThemeSpec.forPreset(preset).colors.transparentActions) return null
    if (options.highContrast) return Color.Black
    // Dark enough for opaque white labels even over the brightest possible artwork.
    val tint = if (preset.hasNeutralBackground) Color.Black else Color(preset.panel)
    return tint.copy(alpha = ThemeSpec.forPreset(preset).colors.artworkActionAlpha)
}

@Composable fun ComicTvTheme(options: AppearanceOptions = AppearanceOptions(), content: @Composable () -> Unit) {
    SideEffect { TvColors.options = options }
    val density = LocalDensity.current
    MaterialTheme(colorScheme = darkColorScheme(
        primary = TvColors.Action, onPrimary = TvColors.OnAction,
        background = TvColors.Background, surface = TvColors.Panel,
        onBackground = Color(0xFFF5F3EF), onSurface = Color(0xFFF5F3EF),
    )) {
        CompositionLocalProvider(LocalThemeSpec provides ThemeSpec.forPreset(options.preset),
            LocalContentColor provides Color.White,
            LocalDensity provides Density(density.density, density.fontScale * options.textScale), content = content)
    }
}

@Composable fun TvButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    transparentBackground: Boolean = false,
    heroAction: Boolean = false,
) {
    val theme = LocalThemeSpec.current
    val outlinedHero = heroAction && theme.colors.outlinedHeroAction && !TvColors.options.highContrast
    val artworkSurface = if (outlinedHero) {
        if (primary) Color(theme.colors.heroPrimarySurface!!) else Color.Transparent
    } else if (transparentBackground) transparentButtonSurface(TvColors.options) else null
    val primePrimary = primary && theme.colors.whitePrimaryAction && !TvColors.options.highContrast
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(theme.shapes.controlRadiusDp.dp)),
        colors = ButtonDefaults.colors(
            containerColor = artworkSurface ?: if (primePrimary) Color.White else if (primary) TvColors.Action else TvColors.ButtonSurface,
            contentColor = if (artworkSurface != null) Color.White else if (primePrimary) TvColors.Ink else if (primary) TvColors.OnAction else Color.White,
            focusedContainerColor = TvColors.Action,
            focusedContentColor = TvColors.OnAction,
        ),
        border = ButtonDefaults.border(
            border = if (outlinedHero) {
                if (primary) Border(BorderStroke(2.dp, TvColors.FocusAccent)) else Border.None
            } else if (artworkSurface != null && !TvColors.options.highContrast)
                Border(BorderStroke(1.dp, Color.White.copy(alpha = .28f))) else Border.None,
            focusedBorder = Border(BorderStroke(if (TvColors.options.highContrast) theme.focus.highContrastBorderDp.dp else theme.focus.buttonBorderDp.dp, TvColors.FocusAccent)),
        ),
        scale = ButtonDefaults.scale(focusedScale = theme.focus.buttonScale),
    ) { Text(text, fontSize = 17.sp) }
}

@Composable fun ScreenTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 30.sp)
        Text(subtitle, color = TvColors.Muted, fontSize = 16.sp)
    }
}

@Composable fun ProgressTrack(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(4.dp).background(TvColors.Track, RoundedCornerShape(3.dp))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f))
            .background(TvColors.FocusAccent, RoundedCornerShape(3.dp)))
    }
}
