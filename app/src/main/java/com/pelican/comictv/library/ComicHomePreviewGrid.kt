package com.pelican.comictv.library

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key as composeKey
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.pelican.comictv.ui.*

internal const val COMIC_HOME_PREVIEW_LIMIT = 10
internal fun comicHomePreviewColumns(textScale: Float): Int = 5
internal fun <T, K> comicHomePreviewItems(items: List<T>, key: (T) -> K): List<T> =
    items.distinctBy(key).take(COMIC_HOME_PREVIEW_LIMIT)

/** A finite preview with stable, mounted focus targets; full destinations keep their own grids. */
@Composable internal fun <T, K> ComicHomePreviewGrid(items: List<T>, key: (T) -> K,
    onMore: () -> Unit, modifier: Modifier = Modifier, moreModifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit) {
    val columns = comicHomePreviewColumns(LocalDensity.current.fontScale)
    val preview = comicHomePreviewItems(items, key)
    val overflow = (LocalHomeFocusSafety.current?.overflowDp ?: 24).dp
    Column(modifier.fillMaxWidth().padding(vertical = overflow), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        preview.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item ->
                    composeKey(key(item)) { Box(Modifier.weight(1f).focusRaised()) { itemContent(item) } }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        TvButton("Ver mais", onMore, moreModifier)
    }
}
