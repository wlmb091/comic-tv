package com.pelican.comictv.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.pelican.comictv.ui.LocalAppOrientation
import com.pelican.comictv.ui.LocalThemeSpec
import com.pelican.comictv.ui.ProgressTrack
import com.pelican.comictv.ui.TvButton
import com.pelican.comictv.ui.TvColors
import com.pelican.comictv.ui.homeHeroTitleSizeSp
import com.pelican.comictv.ui.LocalHomeFocusSafety
import com.pelican.comictv.ui.homeFocusCard

/** Editions the Prime strip rotates through, a group at a time. */
internal const val PRIME_STRIP_EDITIONS = 12
/** Same interval as the other carousels. */
internal const val PRIME_STRIP_INTERVAL_MS = HeroCarouselPolicy.INTERVAL_MS

/** Only complete groups rotate; fewer editions than one group (or one spare group) leave the strip still. */
internal fun primeStripGroups(choices: List<ComicEntity>, groupSize: Int): List<List<ComicEntity>> =
    choices.chunked(groupSize).filterIndexed { index, group -> index == 0 || group.size == groupSize }
internal fun primeStripNext(page: Int, groups: Int) = if (groups > 1) (page + 1) % groups else 0

/** Prime's broad banner shares the screen artwork and adds real edition cards over its right side. */
@Composable internal fun PrimeLibraryHero(
    comic: ComicEntity?, comics: List<ComicEntity>, repository: LibraryRepository,
    onRead: (String) -> Unit, onOpen: (String) -> Unit, onImport: () -> Unit,
    onSelect: (String) -> Unit, modifier: Modifier = Modifier, readModifier: Modifier = Modifier,
    minimumHeightOverrideDp: Float? = null, onRotate: (String) -> Unit = {}, rotationPaused: Boolean = false,
) {
    val theme = LocalThemeSpec.current
    // The strip does not reorder when focus changes the selected banner edition.
    val choices = remember(comics, if (comics.isEmpty()) comic else null) {
        (comics.ifEmpty { listOfNotNull(comic) }).distinctBy { it.id }.take(PRIME_STRIP_EDITIONS)
    }
    val highContrast = TvColors.options.highContrast
    BoxWithConstraints(modifier.fillMaxWidth()
        .heightIn(min = (minimumHeightOverrideDp ?: theme.hero.minimumHeightDp.toFloat()).dp).testTag("library-hero")) {
        val fontScale = LocalDensity.current.fontScale
        val portrait = LocalAppOrientation.current.portrait
        val compact = !portrait && minimumHeightOverrideDp != null && minimumHeightOverrideDp < 300f
        val titleSize = homeHeroTitleSizeSp(theme.typography.titleSp.toFloat(), theme.typography.titleLineHeightSp.toFloat(),
            theme.hero.titleMaxLines, fontScale, if (portrait) null else minimumHeightOverrideDp,
            (if (compact) 4f else 48f) + 40f * fontScale + maxOf(48f, 32f * fontScale + 24f) + 4f +
                (if (compact) 8f else 40f))
        val wide = maxWidth >= 720.dp * fontScale
        val groupSize = if (wide || portrait) 3 else 2
        val groups = remember(choices, groupSize) { primeStripGroups(choices, groupSize) }
        var page by rememberSaveable { mutableIntStateOf(0) }
        var stripFocused by remember { mutableStateOf(false) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        val shownPage = page.coerceIn(0, (groups.size - 1).coerceAtLeast(0))
        // A banner edition chosen elsewhere brings its own group into the strip.
        LaunchedEffect(comic?.id, groups) {
            val index = groups.indexOfFirst { group -> group.any { it.id == comic?.id } }
            if (index >= 0) page = index
        }
        val rotating = TvColors.options.carouselAutoplay && !highContrast && groups.size > 1 && resumed && !stripFocused && !rotationPaused
        // Any pause or manual choice restarts a full interval; focus and progress are never touched.
        LaunchedEffect(rotating, shownPage, groups) {
            if (rotating) {
                delay(PRIME_STRIP_INTERVAL_MS)
                val next = primeStripNext(shownPage, groups.size)
                page = next
                groups.getOrNull(next)?.firstOrNull()?.let { onRotate(it.id) }
            }
        }
        val visibleChoices = groups.getOrNull(shownPage) ?: choices.take(groupSize)
        val ordinaryCardWidth = if (portrait) (maxWidth - 24.dp) / 3 else (maxWidth * if (wide) .165f else .20f).coerceIn(82.dp, 136.dp)
        // Account for the caption and position markers as well as the image itself.
        val cardWidth = if (compact) minOf(ordinaryCardWidth,
            ((minimumHeightOverrideDp!! - 4f - 12f - (36f * fontScale + 16f)) * .68f).coerceAtLeast(52f).dp)
            else ordinaryCardWidth
        if (highContrast) Box(Modifier.matchParentSize().background(Color.Black))
        @Composable fun Summary(modifier: Modifier) {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 10.dp)) {
                Text(if (comic?.lastRead?.let { it > 0 } == true) "CONTINUAR SUA LEITURA" else "NA SUA BIBLIOTECA",
                    color = TvColors.Amber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(comic?.title ?: "Seu universo de quadrinhos", Modifier.testTag("library-hero-title"),
                    style = TextStyle(shadow = if (highContrast) null else Shadow(Color.Black.copy(alpha = .8f), Offset(0f, 2f), 6f)),
                    fontSize = titleSize.first.sp,
                    lineHeight = titleSize.second.sp,
                    fontFamily = libraryTitleFont(theme.typography.titleFamily),
                    fontWeight = FontWeight.ExtraBold, maxLines = theme.hero.titleMaxLines, overflow = TextOverflow.Ellipsis)
                if (comic != null) {
                    Text(if (comic.lastRead > 0) "Página ${comic.currentPage + 1} de ${comic.pageCount}"
                        else "${comic.pageCount} páginas para explorar", color = TvColors.Muted, fontSize = 15.sp)
                    if (comic.lastRead > 0 && comic.pageCount > 0) ProgressTrack((comic.currentPage + 1f) / comic.pageCount,
                        Modifier.widthIn(max = 240.dp).fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvButton(if (comic.lastRead > 0) "Continuar leitura" else "Começar leitura", { onRead(comic.id) }, readModifier, primary = true, heroAction = true)
                        TvButton("Ver detalhes", { onOpen(comic.id) }, transparentBackground = true, heroAction = true)
                    }
                } else {
                    Text("Adicione suas edições ou explore as fontes online.", color = TvColors.Muted, fontSize = 16.sp)
                    TvButton("Importar quadrinhos", onImport, readModifier)
                }
            }
        }
        @Composable fun Choices(modifier: Modifier) {
            if (visibleChoices.isNotEmpty()) Column(modifier,
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 18.dp)) {
                Row(Modifier.fillMaxWidth().onFocusChanged { stripFocused = it.hasFocus }, horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.Bottom) {
                    visibleChoices.forEach { edition -> key(edition.id) {
                        PrimeEditionCard(edition, repository, edition.id == comic?.id, cardWidth,
                            onSelect = { onSelect(edition.id) }, onOpen = { onOpen(edition.id) })
                    } }
                }
                // With several groups the markers are the group pages; a single group keeps the per-card markers that follow D-pad selection.
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (groups.size > 1) groups.indices.forEach { index ->
                        val active = index == shownPage
                        Box(Modifier.size(width = if (active) 16.dp else 6.dp, height = 6.dp)
                            .background(if (active) Color.White else TvColors.Muted.copy(alpha = .5f), RoundedCornerShape(3.dp)))
                    } else visibleChoices.forEach { edition ->
                        val active = edition.id == comic?.id
                        Box(Modifier.size(width = if (active) 16.dp else 6.dp, height = 6.dp)
                            .background(if (active) Color.White else TvColors.Muted.copy(alpha = .5f), RoundedCornerShape(3.dp)))
                    }
                }
            }
        }
        val paddedHero = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = if (LocalHomeFocusSafety.current != null) 0.dp else 18.dp,
            end = if (LocalHomeFocusSafety.current != null) 0.dp else 10.dp,
            top = if (compact) 2.dp else 24.dp, bottom = if (compact) 2.dp else 24.dp)
        if (portrait) Column(paddedHero, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Summary(Modifier.fillMaxWidth())
            Choices(Modifier.fillMaxWidth())
        } else Row(paddedHero, horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
            Summary(Modifier.weight(if (wide) .43f else .51f))
            Choices(Modifier.weight(if (wide) .57f else .49f))
        }
    }
}

@Composable private fun PrimeEditionCard(
    edition: ComicEntity, repository: LibraryRepository, selected: Boolean, width: androidx.compose.ui.unit.Dp,
    onSelect: () -> Unit, onOpen: () -> Unit,
) {
    Card(onClick = onOpen,
        modifier = Modifier.homeFocusCard(1.025f, if (TvColors.options.highContrast) 3f else 2f).width(width).onFocusChanged { if (it.isFocused) onSelect() }.semantics { this.selected = selected },
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
        border = CardDefaults.border(border = Border(BorderStroke(1.dp, Color.White.copy(alpha = .3f))),
            focusedBorder = Border(BorderStroke(if (TvColors.options.highContrast) 3.dp else 2.dp, TvColors.Amber))),
        scale = CardDefaults.scale(focusedScale = 1.025f),
        colors = CardDefaults.colors(containerColor = TvColors.Background, focusedContainerColor = TvColors.Background)) {
        Column(Modifier.fillMaxWidth()) {
            if (!TvColors.options.highContrast) AsyncImage(repository.cover(edition.id), null,
                Modifier.fillMaxWidth().aspectRatio(.68f), contentScale = ContentScale.Fit)
            Text(edition.title, Modifier.padding(horizontal = 8.dp, vertical = 8.dp), fontSize = 14.sp,
                lineHeight = 18.sp, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold)
        }
    }
}
