package com.pelican.comictv.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.math.roundToInt

internal fun tileIdentity(page: PageImage) = page.file.absolutePath + ":" + page.file.length() + ":" + page.width + "x" + page.height
internal fun overviewRegion(page: PageImage): TileRegion {
    var sample = 1
    while (maxOf(page.width, page.height) / sample > 512) sample *= 2
    return TileRegion(0, 0, page.width, page.height, sample)
}

/** Lets the phone pinch know how much of the view the shown pages fill at zoom 1. */
internal val LocalReaderPageFill = staticCompositionLocalOf<(Float, Float) -> Unit> { { _, _ -> } }

/** Display coordinates change immediately; visible source pixels are decoded asynchronously. */
@Composable internal fun HighResolutionPages(state: ReaderState, pages: List<PageImage?>,
    cache: ReaderTileCache, options: ReaderImageOptions, modifier: Modifier = Modifier, gap: Float = 0f,
    onDisplayed: (File) -> Unit, onFailed: (File) -> Unit) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val extents = pages.map { ImageExtent(it?.width ?: pages.filterNotNull().firstOrNull()?.width ?: 1,
        it?.height ?: pages.filterNotNull().firstOrNull()?.height ?: 1) }
    val placements = zoomPlacements(extents, size.width, size.height, state.zoom, state.fit, state.panX, state.panY, gap)
    val reportPageFill = LocalReaderPageFill.current
    SideEffect {
        val fitted = zoomPlacements(extents, size.width, size.height, 1f, state.fit, 0f, 0f, gap)
        if (fitted.isNotEmpty()) reportPageFill((fitted.last().left + fitted.last().width - fitted.first().left) / size.width,
            fitted.maxOf { it.height } / size.height)
    }
    val stamps = remember(pages.map { it?.file }) { pages.map { it?.let(::tileIdentity) } }
    val visible = placements.flatMapIndexed { index, placement -> pages[index]?.let { page ->
        readerRenderRegions(placement, size.width, size.height, cache.framePixelLimit(options)).map { TileKey(page.file, stamps[index].orEmpty(), it, options) }
    }.orEmpty() }
    val overview = pages.mapIndexedNotNull { index, page -> page?.let { TileKey(it.file, stamps[index].orEmpty(), overviewRegion(it), options) } }
    val requested = if (size.width == 0) emptyList() else (overview + visible).distinct()
    var tiles by remember(stamps, options) { mutableStateOf<Map<TileKey, TileBitmap>>(emptyMap()) }
    var failed by remember(stamps, options) { mutableStateOf(false) }
    LaunchedEffect(requested) {
        if (requested.isEmpty()) return@LaunchedEffect
        val previous = tiles
        val prepared = requested.mapNotNull { key -> (previous[key] ?: cache.peek(key))?.let { key to it } }.toMap().toMutableMap()
        // Keep a complete overview while details are prepared, then publish the visible set together.
        for (key in overview) {
            if (key !in prepared) {
                try { prepared[key] = cache.load(key) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { failed = true; onFailed(key.file); return@LaunchedEffect }
            }
        }
        tiles = prepared.toMap()
        for (key in requested) {
            ensureActive()
            if (key in prepared) continue
            try { val tile = cache.load(key); ensureActive(); prepared[key] = tile }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed = true; onFailed(key.file); return@LaunchedEffect }
        }
        tiles = prepared.toMap()
        pages.filterNotNull().forEach { onDisplayed(it.file) }
        // Two neighboring image blocks are optional, never block readiness or reading progress.
        val nearby = placements.flatMapIndexed { index, placement -> pages[index]?.let { page ->
            readerNeighborRegions(placement, size.width, size.height).map { TileKey(page.file, stamps[index].orEmpty(), it, options) }
        }.orEmpty() }.take(2)
        for (key in nearby) {
            try { cache.prefetch(key) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
    }
    Box(modifier.clipToBounds().onSizeChanged { size = it }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Página " + (state.page + 1) + ", zoom de alta resolução" }) {
            fun paint(key: TileKey) {
                val tile = tiles[key] ?: return
                val index = pages.indexOfFirst { it?.file == key.file }
                val placement = placements.getOrNull(index) ?: return
                val scale = placement.width / placement.source.width
                val r = tile.region
                drawImage(tile.bitmap.asImageBitmap(), dstOffset = IntOffset((placement.left + r.left * scale + state.dragX * size.width).roundToInt(),
                    (placement.top + r.top * scale).roundToInt()),
                    dstSize = IntSize(((r.right-r.left) * scale).roundToInt().coerceAtLeast(1), ((r.bottom-r.top) * scale).roundToInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.High)
            }
            overview.forEach(::paint)
            visible.filterNot { it in overview }.forEach(::paint)
        }
        if (tiles.isEmpty() && !failed) Text("Preparando detalhes da imagem…", fontSize = 16.sp)
    }
}
