package com.pelican.comictv.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import com.pelican.comictv.ui.*
import kotlinx.coroutines.flow.first

/** Collection names are user data: never infer a publisher or character from a title. */
internal data class ExploreEntry(val key: String, val label: String, val comicId: String?, val collectionId: String?)

internal fun maxExploreEntries(comics: List<ComicEntity>, collections: List<CollectionEntity>,
    memberships: List<CollectionMembership>): List<ExploreEntry> {
    val ids = comics.mapTo(hashSetOf()) { it.id }
    val firstCover = mutableMapOf<String, String>()
    memberships.forEach { if (it.comicId in ids && it.collectionId !in firstCover) firstCover[it.collectionId] = it.comicId }
    return if (collections.isNotEmpty()) collections.map { collection ->
        ExploreEntry("collection-${collection.id}", collection.name,
            firstCover[collection.id], collection.id)
    } else comics.take(12).map { ExploreEntry("comic-${it.id}", it.title, it.id, null) }
}

@Composable internal fun MaxExploreRow(comics: List<ComicEntity>, collections: List<CollectionEntity>,
    memberships: List<CollectionMembership>, repository: LibraryRepository, filter: String,
    onFilter: (String) -> Unit, onOpen: (String) -> Unit,
    state: LazyListState, onItemFocused: (String, String?) -> Unit,
    onCollectionOpen: (String) -> Unit = {},
    restoreKey: String? = null, restoreRequest: Int = 0, onRestoreHandled: () -> Unit = {},
) {
    val entries = remember(comics, collections, memberships) { maxExploreEntries(comics, collections, memberships) }
    // Requesters exist only while their keyed lazy item is mounted; never save them across navigation.
    val requesters = remember { mutableStateMapOf<String, FocusRequester>() }
    val positioned = remember { mutableStateMapOf<String, Boolean>() }
    val currentHandled by rememberUpdatedState(onRestoreHandled)
    LaunchedEffect(restoreRequest, restoreKey) {
        if (restoreRequest == 0 || restoreKey == null) return@LaunchedEffect
        val index = entries.indexOfFirst { it.key == restoreKey }
        if (index < 0) { currentHandled(); return@LaunchedEffect }
        if (state.layoutInfo.visibleItemsInfo.none { it.key == restoreKey }) state.scrollToItem(index)
        snapshotFlow {
            requesters[restoreKey]?.takeIf { positioned[restoreKey] == true &&
                state.layoutInfo.visibleItemsInfo.any { it.key == restoreKey } }
        }.first { it != null }
        withFrameNanos { }
        requesters[restoreKey]?.requestFocus()
        currentHandled()
    }
    Column(Modifier.fillMaxWidth().testTag("max-explore"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (collections.isNotEmpty()) "Explore suas coleções" else "Explore suas leituras",
            modifier = Modifier,
            fontSize = 20.sp, fontWeight = FontWeight.Bold)
        LazyRow(modifier = Modifier.homeRowViewport().fillMaxWidth().testTag("max-explore-row"), state = state, contentPadding = LocalHomeFocusSafety.current?.rowPadding ?: PaddingValues(horizontal = 48.dp, vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(entries, key = { it.key }) { entry ->
                var focused by remember { mutableStateOf(false) }
                val requester = remember(entry.key) { FocusRequester() }
                DisposableEffect(entry.key, requester) {
                    requesters[entry.key] = requester
                    onDispose { requesters.remove(entry.key); positioned.remove(entry.key) }
                }
                val active = entry.collectionId != null && filter == "collection:${entry.collectionId}"
                Card(onClick = {
                    if (entry.collectionId != null) onCollectionOpen(entry.collectionId)
                    else entry.comicId?.let(onOpen)
                }, modifier = Modifier.homeFocusCard(1.03f, 3f).width(116.dp).testTag("max-explore-${entry.key}")
                    .focusRequester(requester).onGloballyPositioned { positioned[entry.key] = true }
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) onItemFocused(entry.key, entry.comicId)
                    }.semantics { selected = active },
                    shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
                    border = CardDefaults.border(border = Border.None, focusedBorder = Border.None),
                    colors = CardDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
                    scale = CardDefaults.scale(focusedScale = 1.03f)) {
                    Column(Modifier.fillMaxWidth().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(88.dp).clip(CircleShape)
                            .background(if (TvColors.options.highContrast) Color.Black else TvColors.Panel)
                            .border(if (focused) 3.dp else 1.dp,
                                if (focused) TvColors.FocusAccent else TvColors.Muted.copy(alpha = .4f), CircleShape),
                            contentAlignment = Alignment.Center) {
                            if (!TvColors.options.highContrast && entry.comicId != null) AsyncImage(repository.cover(entry.comicId),
                                null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Text(entry.label.take(2).uppercase(), fontSize = 24.sp, color = Color.White)
                        }
                        Text((if (active) "✓ " else "") + entry.label, minLines = 2, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, fontSize = 14.sp, lineHeight = 18.sp)
                    }
                }
            }
        }
    }
}
