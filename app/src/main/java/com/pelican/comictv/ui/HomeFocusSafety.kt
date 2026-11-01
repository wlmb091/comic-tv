package com.pelican.comictv.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.ceil

/** Space for the *existing* Card transform and decoration; no extra scale or glow is drawn. */
internal fun focusOverflowDp(cardSizeDp: Float, scale: Float, borderWidthDp: Float,
    glowRadiusDp: Float = 0f): Int =
    maxOf(24, ceil((scale - 1f).coerceAtLeast(0f) / 2f * cardSizeDp + borderWidthDp + glowRadiusDp).toInt())

internal object HomeSafeArea {
    const val horizontalDp = 48
    const val verticalDp = 27
    fun horizontalInsetDp(overflowDp: Int) = maxOf(horizontalDp, overflowDp)
    fun bottomInsetDp(overflowDp: Int) = verticalDp + overflowDp
}

@Stable internal class HomeFocusSafety(private val baseHorizontalDp: Int = HomeSafeArea.horizontalDp) {
    var overflowDp by mutableIntStateOf(24)
        private set
    fun include(cardSizeDp: Float, scale: Float, borderWidthDp: Float, glowRadiusDp: Float = 0f) {
        overflowDp = maxOf(overflowDp, focusOverflowDp(cardSizeDp, scale, borderWidthDp, glowRadiusDp))
    }
    val horizontalDp get() = maxOf(baseHorizontalDp, overflowDp)
    val rowPadding get() = PaddingValues(horizontal = horizontalDp.dp, vertical = overflowDp.dp)
}

internal val LocalHomeFocusSafety = staticCompositionLocalOf<HomeFocusSafety?> { null }

/** Elevate the same Card that owns TV Material's border/glow/scale. */
internal fun Modifier.focusRaised(): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this.onFocusChanged { focused = it.hasFocus }.zIndex(if (focused) 1f else 0f)
}

internal fun Modifier.homeFocusCard(scale: Float, borderWidthDp: Float, glowRadiusDp: Float = 0f): Modifier = composed {
    val safety = LocalHomeFocusSafety.current
    val density = LocalDensity.current.density
    this.focusRaised().onSizeChanged {
        safety?.include(maxOf(it.width, it.height) / density, scale, borderWidthDp, glowRadiusDp)
    }
}

/**
 * The grid owns the foreground inset once. A lazy row extends its viewport back through that
 * inset, then reserves the same inset as scrollable content padding: its first card and the
 * heading share an origin, while both end cards have room inside the row's clipping viewport.
 */
internal fun Modifier.homeRowViewport(): Modifier = composed {
    val safety = LocalHomeFocusSafety.current
    if (safety == null) this else this.layout { measurable, constraints ->
        val inset = safety.horizontalDp.dp.roundToPx()
        val width = constraints.maxWidth
        val placeable = measurable.measure(constraints.copy(minWidth = width + inset * 2, maxWidth = width + inset * 2))
        layout(width, placeable.height) { placeable.placeRelative(-inset, 0) }
    }
}
