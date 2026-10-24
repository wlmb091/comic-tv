package com.pelican.comictv.reader
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import java.io.File

@Composable internal fun ReaderGallery(state: ReaderState, image: PageImage?, previews: List<TimelinePreview>,
    cache: ReaderTileCache, options: ReaderImageOptions, modifier: Modifier = Modifier, onDisplayed: (File) -> Unit, onFailed: (File) -> Unit) {
    Row(modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        val left = if (state.rtl) state.page + 1 else state.page - 1
        val right = if (state.rtl) state.page - 1 else state.page + 1
        @Composable fun Neighbor(index: Int, modifier: Modifier) {
            Box(modifier.fillMaxHeight(.68f), contentAlignment = Alignment.Center) {
                if (index in 0 until state.pageCount) {
                    val preview = previews.firstOrNull { it.page == index }
                    if (preview?.file != null) AsyncImage(preview.file, "Prévia da página " + (index + 1),
                        Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else Text(if (preview?.failed == true) "Prévia indisponível" else "Carregando prévia…")
                }
            }
        }
        Neighbor(left, Modifier.weight(.26f))
        Box(Modifier.weight(.48f).fillMaxHeight().clipToBounds(), contentAlignment = Alignment.Center) {
            image?.let { page -> HighResolutionPages(state, listOf(page), cache, options,
                Modifier.fillMaxSize(), onDisplayed = onDisplayed, onFailed = onFailed) }
        }
        Neighbor(right, Modifier.weight(.26f))
    }
}
