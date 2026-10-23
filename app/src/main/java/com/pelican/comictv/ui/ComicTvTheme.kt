package com.pelican.comictv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*

object TvColors {
    val Background = Color(0xFF101113)
    val Panel = Color(0xFF1C1E21)
    val Muted = Color(0xFFB8BABE)
    val Amber = Color(0xFFFFBD59)
    val Ink = Color(0xFF15120C)
}

@Composable fun ComicTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = TvColors.Amber, onPrimary = TvColors.Ink,
        background = TvColors.Background, surface = TvColors.Panel,
        onBackground = Color(0xFFF5F3EF), onSurface = Color(0xFFF5F3EF),
    ), content = content)
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
            containerColor = if (primary) TvColors.Amber else TvColors.Panel,
            contentColor = if (primary) TvColors.Ink else Color.White,
            focusedContainerColor = TvColors.Amber,
            focusedContentColor = TvColors.Ink,
        ),
        border = ButtonDefaults.border(focusedBorder = Border(BorderStroke(2.dp, TvColors.Amber))),
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
    Box(modifier.height(4.dp).background(Color(0xFF383A3E), RoundedCornerShape(3.dp))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f))
            .background(TvColors.Amber, RoundedCornerShape(3.dp)))
    }
}
