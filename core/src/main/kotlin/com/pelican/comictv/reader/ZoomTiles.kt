package com.pelican.comictv.reader
import kotlin.math.*

data class ImageExtent(val width: Int, val height: Int)
data class ZoomPlacement(val source: ImageExtent, val left: Float, val top: Float, val width: Float, val height: Float)
data class TileRegion(val left: Int, val top: Int, val right: Int, val bottom: Int, val sample: Int)

fun zoomPlacements(images: List<ImageExtent>, viewWidth: Int, viewHeight: Int, zoom: Float,
    fit: FitMode, panX: Float, panY: Float, gap: Float = 0f): List<ZoomPlacement> {
    if (images.isEmpty() || viewWidth <= 0 || viewHeight <= 0 || images.any { it.width <= 0 || it.height <= 0 }) return emptyList()
    val actualGap = if (images.size > 1) gap else 0f
    val combinedWidth = images.sumOf { it.width }.toFloat()
    val combinedHeight = images.maxOf { it.height }.toFloat()
    val fitted = min((viewWidth - actualGap).coerceAtLeast(1f) / combinedWidth, viewHeight / combinedHeight)
    val rowWidth = combinedWidth * fitted + actualGap
    val rowHeight = combinedHeight * fitted
    val adjustment = when (fit) { FitMode.Page -> 1f; FitMode.Width -> viewWidth / rowWidth; FitMode.Height -> viewHeight / rowHeight }
    val scale = zoom.coerceIn(1f, 3f) * adjustment
    val pixelsPerSource = fitted * scale
    val maxX = ((rowWidth * scale - viewWidth) / 2).coerceAtLeast(0f)
    val maxY = ((rowHeight * scale - viewHeight) / 2).coerceAtLeast(0f)
    var x = (viewWidth - rowWidth * scale) / 2 - panX.coerceIn(-1f, 1f) * maxX
    return images.map { image ->
        ZoomPlacement(image, x, (viewHeight - image.height * pixelsPerSource) / 2 - panY.coerceIn(-1f, 1f) * maxY,
            image.width * pixelsPerSource, image.height * pixelsPerSource).also { x += it.width + actualGap * scale }
    }
}

fun sampleSizeForScale(scale: Float): Int {
    var sample = 1
    while (sample < 128 && sample * 2 * scale <= 1f) sample *= 2
    return sample
}

fun visibleTiles(page: ZoomPlacement, viewWidth: Int, viewHeight: Int, paddingTiles: Int = 0): List<TileRegion> {
    if (page.width <= 0f || page.height <= 0f || page.left >= viewWidth || page.top >= viewHeight || page.left + page.width <= 0 || page.top + page.height <= 0) return emptyList()
    val scale = page.width / page.source.width
    val sample = sampleSizeForScale(scale)
    val span = 512 * sample
    val x0 = floor((-page.left / scale).coerceAtLeast(0f) / span).toInt() - paddingTiles
    val y0 = floor((-page.top / scale).coerceAtLeast(0f) / span).toInt() - paddingTiles
    val x1 = ceil(((viewWidth - page.left) / scale).coerceAtMost(page.source.width.toFloat()) / span).toInt() - 1 + paddingTiles
    val y1 = ceil(((viewHeight - page.top) / scale).coerceAtMost(page.source.height.toFloat()) / span).toInt() - 1 + paddingTiles
    return buildList {
        for (y in y0.coerceAtLeast(0)..y1.coerceAtMost((page.source.height - 1) / span))
            for (x in x0.coerceAtLeast(0)..x1.coerceAtMost((page.source.width - 1) / span))
                add(TileRegion(x * span, y * span, minOf((x + 1) * span, page.source.width), minOf((y + 1) * span, page.source.height), sample))
    }
}
