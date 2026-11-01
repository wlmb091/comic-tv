package com.pelican.comictv.library

internal const val HOME_PREVIEW_LIMIT = 12
internal fun <T, K> homePreviewItems(items: List<T>, key: (T) -> K): List<T> =
    items.distinctBy(key).take(HOME_PREVIEW_LIMIT)

/** First-page titles kept for the source rows; only their covers are extra downloads. */
internal const val HOME_SOURCE_POOL_LIMIT = 48
internal const val HOME_SOURCE_CYCLE_MS = 10_000L
internal const val HOME_SOURCE_PRELOAD_MS = 2_000L
/** The visible slice of a source row, wrapping around; a short list stays as it is. */
internal fun <T> homeCycleWindow(pool: List<T>, start: Int, size: Int): List<T> =
    if (pool.size <= size) pool else List(size) { pool[(start + it) % pool.size] }
internal fun homeCycleNext(count: Int, start: Int, size: Int): Int = if (count <= size) 0 else (start + size) % count

/** Hero, optional recent heading/row, collection heading/row, two source rows and shortcuts. */
internal fun homeGridFooterIndex(recentCount: Int, explorer: Boolean): Int =
    5 + (if (recentCount > 0) 2 else 0) + (if (explorer) 1 else 0)

/** Keep already visible controls in place; TV pivot alignment can otherwise hide the hero title. */
internal fun homeBringIntoViewDistance(offset: Float, size: Float, container: Float): Float = when {
    size > container -> offset
    offset < 0 -> offset
    offset + size > container -> offset + size - container
    else -> 0f
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal object HomeBringIntoViewSpec : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        homeBringIntoViewDistance(offset, size, containerSize)
}

/** Explorer follows the optional recent heading/row; collection/catalog/latest keep their indices. */
internal fun homeSourceRowIndex(recentCount: Int, explorer: Boolean, latest: Boolean, collectionItemCount: Int = 1): Int =
    libraryGridFooterIndex(false, recentCount, collectionItemCount, explorer) + (if (latest) 1 else 0)

/** The header includes non-focusable title/art above its actions: reveal it as a unit. */
internal fun homeHeaderBringIntoViewDistance(offset: Float, size: Float, container: Float, headerHasFocus: Boolean): Float =
    if (headerHasFocus) 0f else homeBringIntoViewDistance(offset, size, container)

/** The Max explorer moves after Continue reading, including its heading. */
internal fun homeExplorerRowIndex(recentCount: Int): Int = 1 + if (recentCount > 0) 2 else 0

/** Small bring-into-view offsets do not hide a still visible hero. */
internal fun homeHeroVisible(offset: Int, height: Int, viewportStart: Int, viewportEnd: Int): Boolean =
    height > 0 && offset < viewportEnd && offset + height > viewportStart
