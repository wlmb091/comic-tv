package com.pelican.comictv.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.pelican.comictv.R
import com.pelican.comictv.ui.*

private val maxTitleFont = FontFamily(Font(R.font.bebas_neue_regular))
private val primeTitleFont = FontFamily(Font(R.font.barlow_condensed_extra_bold, FontWeight.ExtraBold))

internal fun libraryTitleFont(family: ThemeTitleFamily): FontFamily = when (family) {
    ThemeTitleFamily.Default -> ComicFonts.Body
    ThemeTitleFamily.Serif -> ComicFonts.Cinema
    ThemeTitleFamily.Bebas -> maxTitleFont
    ThemeTitleFamily.Barlow -> primeTitleFont
}

/** Content only: the caller retains the stable card keys, D-pad handlers and focus requesters. */
@Composable internal fun RecentCardContent(comic: ComicEntity, repository: LibraryRepository) {
    val theme = LocalThemeSpec.current
    val cards = theme.cards
    when (cards.continueStyle) {
        ContinueCardStyle.Art16x9, ContinueCardStyle.ArtLarge -> {
            // Covers are inexpensive existing files. Never extract original pages or fetch artwork per card.
            // Crop fills the approved frames with uniform scaling; Fit retains the whole cover elsewhere.
            val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            Box(Modifier.fillMaxWidth().heightIn(min = cards.recentHeightDp.coerceAtLeast(114).dp * fontScale)
                .testTag("library-recent-art-${comic.id}").background(if (TvColors.options.highContrast) Color.Black else TvColors.Panel)) {
                if (!TvColors.options.highContrast) AsyncImage(repository.cover(comic.id), null,
                    Modifier.matchParentSize(), contentScale = if (cards.cropRecentCover) ContentScale.Crop else ContentScale.Fit)
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(cardScrim()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(comic.title, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        fontFamily = libraryTitleFont(theme.typography.titleFamily),
                        fontSize = theme.typography.cardTitleSp.coerceAtLeast(14).sp,
                        lineHeight = theme.typography.cardLineHeightSp.sp, fontWeight = FontWeight.SemiBold)
                    ProgressTrack(readingFraction(comic), Modifier.fillMaxWidth())
                }
            }
        }
        ContinueCardStyle.ThumbnailRow, ContinueCardStyle.Compact, ContinueCardStyle.GlassSerif -> {
            val material = if (TvColors.options.highContrast) Modifier.background(Color.Black)
                else if (cards.recentSurface != null) Modifier.background(Color(cards.recentSurface), RoundedCornerShape(9.dp))
                    .then(cards.recentBorder?.let { Modifier.border(1.dp, Color(it), RoundedCornerShape(9.dp)) } ?: Modifier)
                else Modifier.glassPanel()
            Row(Modifier.fillMaxWidth().heightIn(min = cards.recentHeightDp.dp).then(material)
                .padding(cards.recentPaddingDp.dp), horizontalArrangement = Arrangement.spacedBy(cards.recentGapDp.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (!TvColors.options.highContrast) AsyncImage(repository.cover(comic.id), null,
                    Modifier.width(cards.recentThumbnailWidthDp.dp).height(cards.recentThumbnailHeightDp.dp), contentScale = if (cards.cropRecentCover) ContentScale.Crop else ContentScale.Fit)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(comic.title, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        fontFamily = if (cards.continueStyle == ContinueCardStyle.GlassSerif) libraryTitleFont(ThemeTitleFamily.Serif) else ComicFonts.Body,
                        fontSize = theme.typography.cardTitleSp.coerceAtLeast(14).sp,
                        lineHeight = theme.typography.cardLineHeightSp.sp, fontWeight = FontWeight.SemiBold)
                    Text(readingStatus(comic), color = TvColors.Muted, fontSize = theme.typography.cardStatusSp.coerceAtLeast(14).sp)
                    ProgressTrack(readingFraction(comic), Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable internal fun CollectionCardContent(comic: ComicEntity, repository: LibraryRepository, offline: Boolean) {
    val theme = LocalThemeSpec.current
    val style = theme.cards.collectionStyle
    if (style == CollectionCardStyle.Horizontal) {
        Row(Modifier.fillMaxWidth().heightIn(min = 96.dp).glassPanel().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!TvColors.options.highContrast) AsyncImage(repository.cover(comic.id), null,
                Modifier.width(52.dp).height(76.dp).testTag("library-thumbnail-${comic.id}"), contentScale = if (theme.cards.cropCollectionCover) ContentScale.Crop else ContentScale.Fit)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CollectionTitle(comic)
                Text(readingStatus(comic), color = TvColors.Muted, fontSize = theme.typography.cardStatusSp.coerceAtLeast(14).sp)
                if (comic.lastRead > 0) ProgressTrack(readingFraction(comic), Modifier.fillMaxWidth())
                if (offline) OfflineBadge()
                if (comic.favorite) FavoriteBadge()
            }
        }
        return
    }
    val ratio = when (style) {
        CollectionCardStyle.Square -> 1f
        CollectionCardStyle.Mini -> 16f / 9f
        else -> 2f / 3f
    }
    Box(Modifier.fillMaxWidth().aspectRatio(ratio).background(if (TvColors.options.highContrast) Color.Black else TvColors.Panel)) {
        if (!TvColors.options.highContrast) AsyncImage(repository.cover(comic.id), comic.title,
            Modifier.fillMaxSize().testTag("library-thumbnail-${comic.id}"), contentScale = if (theme.cards.cropCollectionCover) ContentScale.Crop else ContentScale.Fit)
        if (offline) Box(Modifier.align(Alignment.TopStart).background(Color.Black).padding(5.dp)) { OfflineBadge() }
        if (comic.favorite) Box(Modifier.align(Alignment.TopEnd).background(Color.Black).padding(6.dp)) { FavoriteBadge() }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(cardScrim())
            .padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CollectionTitle(comic)
            if (comic.lastRead > 0) ProgressTrack(readingFraction(comic), Modifier.fillMaxWidth())
        }
    }
}

@Composable private fun CollectionTitle(comic: ComicEntity) {
    val typography = LocalThemeSpec.current.typography
    Text(comic.title, Modifier.testTag("library-title-${comic.id}"), minLines = 2, maxLines = 2,
        overflow = TextOverflow.Ellipsis, fontSize = typography.cardTitleSp.coerceAtLeast(14).sp,
        lineHeight = typography.cardLineHeightSp.sp)
}

@Composable private fun OfflineBadge() {
    Text("OFFLINE", fontSize = LocalThemeSpec.current.typography.badgeSp.coerceAtLeast(14).sp, color = TvColors.Amber)
}

@Composable private fun FavoriteBadge() {
    Text("★", Modifier.semantics { contentDescription = "Favorito" }, color = TvColors.Amber, fontSize = 19.sp)
}

@Composable private fun cardScrim(): Brush = Brush.verticalGradient(
    if (TvColors.options.highContrast) listOf(Color.Black, Color.Black)
    else listOf(Color.Transparent, Color.Black.copy(alpha = .94f), Color.Black))
