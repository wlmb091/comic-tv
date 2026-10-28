package com.pelican.comictv.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import com.pelican.comictv.sources.OnlineTitle
import com.pelican.comictv.ui.*
import kotlinx.coroutines.flow.first

@Composable
internal fun HomeSourceRow(label: String, titles: List<OnlineTitle>, state: LazyListState,
    onOpen: (OnlineTitle) -> Unit, onMore: () -> Unit, onFocus: (String) -> Unit,
    restoreKey: String? = null, onRestored: () -> Unit = {}) {
    val theme = LocalThemeSpec.current
    val horizontal = theme.cards.collectionStyle in setOf(CollectionCardStyle.Horizontal, CollectionCardStyle.Mini)
    val restore = remember { FocusRequester() }
    val effectiveRestoreKey = restoreKey?.let { key -> if (key == "more" || titles.any { it.key == key }) key else "more" }
    var positioned by remember { mutableStateOf<String?>(null) }
    val sample = remember(titles) { homePreviewItems(titles) { it.key } }
    LaunchedEffect(effectiveRestoreKey) {
        if (effectiveRestoreKey == null) return@LaunchedEffect
        val index = if (effectiveRestoreKey == "more") sample.size else sample.indexOfFirst { it.key == effectiveRestoreKey }
        if (index < 0) { onRestored(); return@LaunchedEffect }
        state.scrollToItem(index)
        snapshotFlow { positioned == effectiveRestoreKey }.first { it }
        restore.requestFocus()
        onRestored()
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, fontSize = 20.sp)
        LazyRow(state = state, modifier = Modifier.focusRestorer().testTag("home-source-$label"),
            contentPadding = PaddingValues(6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(sample, key = { it.key }) { title ->
                Card(onClick = { onFocus(title.key); onOpen(title) }, modifier = Modifier.width(if (horizontal) 220.dp else 132.dp)
                    .then(if (effectiveRestoreKey == title.key) Modifier.focusRequester(restore).onGloballyPositioned { positioned = title.key } else Modifier)
                    .onFocusChanged { if (it.isFocused) onFocus(title.key) },
                    scale = CardDefaults.scale(focusedScale = theme.focus.collectionScale),
                    border = CardDefaults.border(focusedBorder = Border(BorderStroke(theme.focus.cardBorderDp.dp, TvColors.FocusAccent))),
                    colors = CardDefaults.colors(containerColor = TvColors.Panel)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(if (horizontal) 16f / 9f else 2f / 3f).background(TvColors.Background)) {
                        if (!TvColors.options.highContrast) AsyncImage(title.coverUrl, title.title,
                            Modifier.fillMaxSize().testTag("home-source-cover-${title.key}"),
                            contentScale = if (horizontal) ContentScale.Crop else ContentScale.Fit)
                    }
                    Text(title.title, Modifier.padding(8.dp), maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
                }
            }
            item(key = "more") {
                TvButton("Ver mais", { onFocus("more"); onMore() }, Modifier
                    .then(if (effectiveRestoreKey == "more") Modifier.focusRequester(restore).onGloballyPositioned { positioned = "more" } else Modifier)
                    .onFocusChanged { if (it.isFocused) onFocus("more") })
            }
        }
        if (sample.isEmpty()) Text("Abra a seção para escolher uma fonte ou consultar os títulos disponíveis.", color = TvColors.Muted, fontSize = 15.sp)
    }
}
