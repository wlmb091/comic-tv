package com.pelican.comictv.reader

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import java.io.File

/** Both pages share one scale, preserving their aspect ratios and the binding in the middle. */
@Composable internal fun ReaderBook(state: ReaderState, current: PageImage?, partner: PageImage?, partnerExpected: Boolean,
    modifier: Modifier = Modifier, onDisplayed: (File) -> Unit, onFailed: (File) -> Unit,
    onPartnerDisplayed: (File) -> Unit, onPartnerFailed: (File) -> Unit) {
    BoxWithConstraints(modifier.padding(12.dp).clipToBounds(), contentAlignment = Alignment.Center) {
        current?.let { page ->
            val density = LocalDensity.current
            val secondWidth = if (partnerExpected) partner?.width ?: page.width else 0
            val secondHeight = if (partnerExpected) partner?.height ?: page.height else 0
            val width = (page.width + secondWidth).coerceAtLeast(1)
            val height = maxOf(page.height, secondHeight).coerceAtLeast(1)
            val gap = if (partnerExpected) with(density) { 2.dp.toPx() } else 0f
            val fitted = minOf((constraints.maxWidth - gap).coerceAtLeast(1f) / width, constraints.maxHeight.toFloat() / height)
            val rowWidth = width * fitted + gap
            val rowHeight = height * fitted
            val adjustment = when (state.fit) {
                FitMode.Page -> 1f
                FitMode.Width -> constraints.maxWidth / rowWidth
                FitMode.Height -> constraints.maxHeight / rowHeight
            }
            val scale = state.zoom * adjustment
            val maxX = ((rowWidth * scale - constraints.maxWidth) / 2).coerceAtLeast(0f)
            val maxY = ((rowHeight * scale - constraints.maxHeight) / 2).coerceAtLeast(0f)
            @Composable fun Page(second: Boolean) {
                val item = if (second) partner else page
                val w = if (second) secondWidth else page.width
                val h = if (second) secondHeight else page.height
                Box(Modifier.width(with(density) { (w * fitted).toDp() })
                    .height(with(density) { (h * fitted).toDp() }), contentAlignment = Alignment.Center) {
                    if (item != null) AsyncImage(item.file, "Página " + (state.page + if (second) 2 else 1),
                        Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                        onSuccess = { if (second) onPartnerDisplayed(item.file) else onDisplayed(item.file) },
                        onError = { if (second) onPartnerFailed(item.file) else onFailed(item.file) })
                    else Text("Carregando segunda página…")
                }
            }
            Row(Modifier.width(with(density) { rowWidth.toDp() }).height(with(density) { rowHeight.toDp() })
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = -state.panX * maxX; translationY = -state.panY * maxY
                }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (partnerExpected) 2.dp else 0.dp)) {
                if (state.rtl && partnerExpected) Page(true)
                Page(false)
                if (!state.rtl && partnerExpected) Page(true)
            }
        }
    }
}
