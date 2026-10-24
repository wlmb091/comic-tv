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
    modifier: Modifier = Modifier, onDisplayed: (File) -> Unit, onFailed: (File) -> Unit) {
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
        BoxWithConstraints(Modifier.weight(.48f).fillMaxHeight().clipToBounds(), contentAlignment = Alignment.Center) {
            image?.let { page ->
                val fitted = minOf(constraints.maxWidth.toFloat() / page.width, constraints.maxHeight.toFloat() / page.height)
                val adjustment = when (state.fit) {
                    FitMode.Page -> 1f
                    FitMode.Width -> constraints.maxWidth / (page.width * fitted)
                    FitMode.Height -> constraints.maxHeight / (page.height * fitted)
                }
                val scale = state.zoom * adjustment
                val maxX = ((page.width * fitted * scale - constraints.maxWidth) / 2).coerceAtLeast(0f)
                val maxY = ((page.height * fitted * scale - constraints.maxHeight) / 2).coerceAtLeast(0f)
                AsyncImage(page.file, "Página " + (state.page + 1), Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = -state.panX * maxX; translationY = -state.panY * maxY
                }, contentScale = ContentScale.Fit,
                    onSuccess = { onDisplayed(page.file) }, onError = { onFailed(page.file) })
            }
        }
        Neighbor(right, Modifier.weight(.26f))
    }
}
