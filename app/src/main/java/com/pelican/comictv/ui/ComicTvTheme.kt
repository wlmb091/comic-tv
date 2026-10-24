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
    val Background get() = if (options.highContrast) Color.Black else Color(options.preset.background)
    val Panel get() = if (options.highContrast) Color.Black else Color(options.preset.panel)
    val Muted get() = if (options.highContrast) Color.White else Color(0xFFCFD1D5)
    val Amber get() = Color(options.preset.accent)
    val Ink = Color(0xFF101010)
    val Action get() = Color(options.preset.action)
    val OnAction get() = Color(options.preset.onAction)
    val Track get() = if (options.highContrast) Color(0xFF606060) else Color(options.preset.track)
}

@Composable fun ComicTvTheme(options: AppearanceOptions = AppearanceOptions(), content: @Composable () -> Unit) {
    SideEffect { TvColors.options = options }
    val density = LocalDensity.current
    MaterialTheme(colorScheme = darkColorScheme(
        primary = TvColors.Action, onPrimary = TvColors.OnAction,
        background = TvColors.Background, surface = TvColors.Panel,
        onBackground = Color(0xFFF5F3EF), onSurface = Color(0xFFF5F3EF),
    )) {
        CompositionLocalProvider(LocalContentColor provides Color.White,
            LocalDensity provides Density(density.density, density.fontScale * options.textScale), content = content)
    }
}

@Composable fun TvButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ButtonDefaults.colors(
            containerColor = if (primary) TvColors.Action else TvColors.Panel,
            contentColor = if (primary) TvColors.OnAction else Color.White,
            focusedContainerColor = TvColors.Action,
            focusedContentColor = TvColors.OnAction,
        ),
        border = ButtonDefaults.border(focusedBorder = Border(BorderStroke(if (TvColors.options.highContrast) 3.dp else 2.dp, TvColors.Amber))),
        scale = ButtonDefaults.scale(focusedScale = 1.03f),
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
            .background(TvColors.Amber, RoundedCornerShape(3.dp)))
    }
}
