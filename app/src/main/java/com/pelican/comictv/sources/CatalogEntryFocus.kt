package com.pelican.comictv.sources

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.pelican.comictv.ui.preferredContentEntryModifier

internal enum class CatalogEntryFocus(val firstIndex: Int) { Wait(0), Title(3), Source(0) }

internal fun catalogEntryFocus(titleCount: Int, busy: Boolean, loaded: Boolean) = when {
    titleCount > 0 -> CatalogEntryFocus.Title
    busy || !loaded -> CatalogEntryFocus.Wait
    else -> CatalogEntryFocus.Source
}

@Composable internal fun catalogFirstEntryModifier(titleCount: Int, busy: Boolean, loaded: Boolean,
    grid: LazyGridState): Modifier {
    val destination = catalogEntryFocus(titleCount, busy, loaded)
    // Folder and first cover cannot share a stale LayoutCoordinates/requester registration.
    return key(destination) {
        preferredContentEntryModifier(enabled = destination != CatalogEntryFocus.Wait,
            prepare = { grid.scrollToItem(destination.firstIndex) })
    }
}
