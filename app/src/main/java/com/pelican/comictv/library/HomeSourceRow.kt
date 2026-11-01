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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@Composable
internal fun HomeSourceRow(label: String, titles: List<OnlineTitle>, state: LazyListState,
    onOpen: (OnlineTitle) -> Unit, onMore: () -> Unit, onFocus: (String) -> Unit,
    gridPreview: Boolean = false, restoreKey: String? = null, onRestored: () -> Unit = {}) {
    val theme = LocalThemeSpec.current
    val horizontal = theme.cards.collectionStyle in setOf(CollectionCardStyle.Horizontal, CollectionCardStyle.Mini)
    val restore = remember { FocusRequester() }
    val pool = remember(titles) { titles.distinctBy { it.key } }
    val windowSize = if (gridPreview) COMIC_HOME_PREVIEW_LIMIT else HOME_PREVIEW_LIMIT
    var cycleStart by remember(pool) { mutableIntStateOf(0) }
    // Returning from a title shows the slice that holds it, so its card can take focus back.
    val restoreStart = restoreKey?.let { key -> pool.indexOfFirst { it.key == key }.takeIf { it >= 0 } }
        ?.takeIf { homeCycleWindow(pool, cycleStart, windowSize).none { it.key == restoreKey } }
    val start = restoreStart ?: cycleStart
    val sample = remember(pool, start, windowSize) { homeCycleWindow(pool, start, windowSize) }
    val effectiveRestoreKey = restoreKey?.let { key -> if (key == "more" || sample.any { it.key == key }) key else "more" }
    var positioned by remember { mutableStateOf<String?>(null) }
    var focusInRow by remember { mutableStateOf(false) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val resumed by lifecycle.currentStateFlow.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val cycling = TvColors.options.carouselAutoplay && !TvColors.options.highContrast && !focusInRow && restoreKey == null &&
        resumed.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) && pool.size > windowSize
    LaunchedEffect(cycling, pool, cycleStart, windowSize) {
        if (!cycling) return@LaunchedEffect
        delay(HOME_SOURCE_CYCLE_MS - HOME_SOURCE_PRELOAD_MS)
        val next = homeCycleNext(pool.size, cycleStart, windowSize)
        homeCycleWindow(pool, next, windowSize).forEach { title ->
            coil3.SingletonImageLoader.get(context).enqueue(coil3.request.ImageRequest.Builder(context).data(title.coverUrl).build())
        }
        delay(HOME_SOURCE_PRELOAD_MS)
        cycleStart = next
    }
    LaunchedEffect(effectiveRestoreKey) {
        if (effectiveRestoreKey == null) return@LaunchedEffect
        cycleStart = start
        val index = if (effectiveRestoreKey == "more") sample.size else sample.indexOfFirst { it.key == effectiveRestoreKey }
        if (index < 0) { onRestored(); return@LaunchedEffect }
        if (!gridPreview) state.scrollToItem(index)
        snapshotFlow { positioned == effectiveRestoreKey }.first { it }
        restore.requestFocus()
        onRestored()
    }
    Column(Modifier.onFocusChanged { focusInRow = it.hasFocus }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, fontSize = 20.sp)
        if (gridPreview) {
            ComicHomePreviewGrid(sample, { it.key }, onMore = { onFocus("more"); onMore() },
                modifier = Modifier.testTag("home-source-$label"),
                moreModifier = (if (effectiveRestoreKey == "more") Modifier.focusRequester(restore)
                    .onGloballyPositioned { positioned = "more" } else Modifier)
                    .onFocusChanged { if (it.isFocused) onFocus("more") }) { title ->
                Card(onClick = { onFocus(title.key); onOpen(title) },
                    modifier = Modifier.homeFocusCard(theme.focus.collectionScale, theme.focus.cardBorderDp.toFloat()).fillMaxWidth()
                        .then(if (effectiveRestoreKey == title.key) Modifier.focusRequester(restore)
                            .onGloballyPositioned { positioned = title.key } else Modifier)
                        .onFocusChanged { if (it.isFocused) onFocus(title.key) },
                    scale = CardDefaults.scale(focusedScale = theme.focus.collectionScale),
                    border = CardDefaults.border(focusedBorder = Border(BorderStroke(theme.focus.cardBorderDp.dp, TvColors.FocusAccent))),
                    colors = CardDefaults.colors(containerColor = TvColors.Panel)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(TvColors.Background)) {
                        if (!TvColors.options.highContrast) AsyncImage(title.coverUrl, title.title,
                            Modifier.fillMaxSize().testTag("home-source-cover-${title.key}"), contentScale = ContentScale.Crop)
                    }
                    Text(title.title, Modifier.padding(8.dp), maxLines = 2, minLines = 2,
                        overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
                }
            }
        } else {
        // No focusRestorer: entering from another row keeps the D-pad column, not the last visited card.
        LazyRow(state = state, modifier = Modifier.homeRowViewport().fillMaxWidth().testTag("home-source-$label"),
            contentPadding = LocalHomeFocusSafety.current?.rowPadding ?: PaddingValues(horizontal = 48.dp, vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            items(sample, key = { it.key }) { title ->
                Card(onClick = { onFocus(title.key); onOpen(title) }, modifier = Modifier.homeFocusCard(theme.focus.collectionScale, theme.focus.cardBorderDp.toFloat()).width(if (horizontal) 220.dp else 132.dp)
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
        }
        if (sample.isEmpty()) Text("Abra a seção para escolher uma fonte ou consultar os títulos disponíveis.", color = TvColors.Muted, fontSize = 15.sp)
    }
}
