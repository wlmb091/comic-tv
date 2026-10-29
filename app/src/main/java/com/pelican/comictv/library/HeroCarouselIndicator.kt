package com.pelican.comictv.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Decorative only: no D-pad target or accessibility announcements on every clock frame. */
@Composable internal fun HeroCarouselIndicator(modifier: Modifier = Modifier) {
    val info = LocalHeroCarouselInfo.current
    val progress = LocalHeroCarouselProgress.current
    if (!info.enabled || info.count < 2) return
    val indices = HeroCarouselPolicy.indicatorIndices(info.count, info.index)
    Canvas(modifier.width((indices.size * 27).dp).height(8.dp)
        .semantics { contentDescription = "Destaque ${info.index + 1} de ${info.count}" }) {
        val slot = 27.dp.toPx()
        indices.forEachIndexed { position, index ->
            val center = Offset(slot * (position + .5f), size.height / 2)
            if (index == info.index) {
                val width = 20.dp.toPx()
                val height = 6.dp.toPx()
                val origin = Offset(center.x - width / 2, center.y - height / 2)
                drawRoundRect(Color.White.copy(alpha = .35f), origin, Size(width, height), CornerRadius(height / 2))
                val filled = width * progress.value.coerceIn(0f, 1f)
                if (filled > 0f) drawRoundRect(Color.White, origin, Size(filled, height), CornerRadius(height / 2))
            } else drawCircle(Color.White.copy(alpha = .5f), 3.dp.toPx(), center)
        }
    }
}
