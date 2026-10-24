package com.pelican.comictv.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import com.pelican.comictv.library.LibraryRepository
import com.pelican.comictv.sources.RelatedComic
import com.pelican.comictv.ui.TvColors

@Composable internal fun RelatedComicsRow(comics: List<RelatedComic>, repository: LibraryRepository, onOpen: (RelatedComic) -> Unit) {
    LazyRow(contentPadding = PaddingValues(10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        items(comics, key = { it.source.packageName + ":" + it.source.id + ":" + it.title.key }) { item ->
            Card(onClick = { onOpen(item) }, modifier = Modifier.width(148.dp),
                colors = CardDefaults.colors(containerColor = TvColors.Panel),
                scale = CardDefaults.scale(focusedScale = 1.03f),
                border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, TvColors.Amber)))) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(TvColors.Background)) {
                        AsyncImage(item.libraryId?.let(repository::cover) ?: item.title.coverUrl, item.title.title,
                            Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                    Text(item.title.title, fontSize = 16.sp, lineHeight = 21.sp, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.reasons.joinToString(" • "), color = TvColors.Amber, fontSize = 13.sp, lineHeight = 18.sp,
                        maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (item.libraryId != null) "Na biblioteca" else item.source.name, color = TvColors.Muted, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}
