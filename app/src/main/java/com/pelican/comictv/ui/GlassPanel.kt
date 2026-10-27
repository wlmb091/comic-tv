package com.pelican.comictv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Frosted appearance over the existing blurred backdrop; no live capture or RenderEffect. */
@Composable internal fun Modifier.glassPanel(): Modifier {
    val shape = RoundedCornerShape(8.dp)
    return if (TvColors.options.highContrast) {
        background(Color.Black, shape).border(1.dp, Color.White, shape)
    } else {
        background(TvColors.Panel.copy(alpha = .78f), shape)
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .07f),
                Color.White.copy(alpha = .015f), Color.Transparent)), shape)
            .border(1.dp, Color.White.copy(alpha = .16f), shape)
    }
}
