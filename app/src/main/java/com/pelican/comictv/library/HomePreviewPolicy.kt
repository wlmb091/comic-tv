package com.pelican.comictv.library

internal const val HOME_PREVIEW_LIMIT = 12
internal fun <T, K> homePreviewItems(items: List<T>, key: (T) -> K): List<T> =
    items.distinctBy(key).take(HOME_PREVIEW_LIMIT)

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

/** Explorer is directly below the hero, before the recent heading/row. */
internal fun homeSourceRowIndex(recentCount: Int, explorer: Boolean, latest: Boolean): Int =
    3 + (if (recentCount > 0) 2 else 0) + (if (explorer) 1 else 0) + (if (latest) 1 else 0)

/** The header includes non-focusable title/art above its actions: reveal it as a unit. */
internal fun homeHeaderBringIntoViewDistance(offset: Float, size: Float, container: Float, headerHasFocus: Boolean): Float =
    if (headerHasFocus) 0f else homeBringIntoViewDistance(offset, size, container)
