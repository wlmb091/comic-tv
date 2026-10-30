package com.pelican.comictv.reader

import java.io.Closeable

/** Decode a fitted page once when the exact existing sample fits the same 2MP bound. */
fun readerRenderRegions(page: ZoomPlacement, viewWidth: Int, viewHeight: Int, maximumFramePixels: Long = 2_000_000L): List<TileRegion> {
    val visible = visibleTiles(page, viewWidth, viewHeight)
    if (visible.isEmpty()) return emptyList()
    val sample = visible.first().sample
    val width = (page.source.width.toLong() + sample - 1) / sample
    val height = (page.source.height.toLong() + sample - 1) / sample
    val pixels = width * height
    val complete = page.left >= -.01f && page.top >= -.01f && page.left + page.width <= viewWidth + .01f && page.top + page.height <= viewHeight + .01f
    return if (complete && width <= 2048 && height <= 2048 && pixels <= maximumFramePixels.coerceAtMost(2_000_000L))
        listOf(TileRegion(0,0,page.source.width,page.source.height,sample)) else visible
}

fun readerNeighborRegions(page: ZoomPlacement, viewWidth: Int, viewHeight: Int): List<TileRegion> {
    val visible = visibleTiles(page,viewWidth,viewHeight).toSet()
    return visibleTiles(page,viewWidth,viewHeight,1).filterNot { it in visible }
}

/** Caller serializes access. Native decoder lifetime is independent of individual image blocks. */
class ReaderDecoderSessions<K, V : Closeable>(private val capacity: Int) {
    private val entries = LinkedHashMap<K,V>(4,.75f,true)
    init { require(capacity > 0) }
    fun get(key: K, create: () -> V): V {
        entries[key]?.let { return it }
        val value = create()
        entries[key] = value
        while (entries.size > capacity) {
            val old = entries.entries.first(); entries.remove(old.key); old.value.close()
        }
        return value
    }
    fun clear() { entries.values.forEach { runCatching { it.close() } }; entries.clear() }
}
