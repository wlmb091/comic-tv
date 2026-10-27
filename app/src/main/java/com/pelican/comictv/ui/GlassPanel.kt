package com.pelican.comictv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Frosted appearance over the existing blurred backdrop; no live capture or RenderEffect. */
@Composable internal fun Modifier.glassPanel(surface: Color = TvColors.FrostedPanel, reflection: () -> Float = { 1f }): Modifier {
    val shape = RoundedCornerShape(8.dp)
    return if (TvColors.options.highContrast) {
        background(Color.Black, shape).border(1.dp, Color.White, shape)
    } else {
        background(surface, shape)
            .drawWithCache {
                val sheen = Brush.linearGradient(listOf(Color.White.copy(alpha = .04f),
                    Color.White.copy(alpha = .01f), Color.Transparent))
                val corner = CornerRadius(8.dp.toPx())
                onDrawBehind { drawRoundRect(sheen, cornerRadius = corner, alpha = reflection().coerceIn(0f, 1f)) }
            }
            .border(1.dp, Color.White.copy(alpha = .14f), shape)
    }
}
