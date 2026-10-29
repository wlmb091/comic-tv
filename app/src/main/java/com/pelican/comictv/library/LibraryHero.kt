package com.pelican.comictv.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.pelican.comictv.ui.*

@Composable internal fun LibraryHero(comic: ComicEntity?, repository: LibraryRepository,
    onRead: (String) -> Unit, onOpen: (String) -> Unit, onImport: () -> Unit,
    modifier: Modifier = Modifier, readModifier: Modifier = Modifier,
    comics: List<ComicEntity> = emptyList(), onSelect: (String) -> Unit = {},
    onActionFocused: () -> Unit = {},
    posterHeightLimitDp: Float? = null,
    minimumHeightOverrideDp: Float? = null,
) {
    val theme = LocalThemeSpec.current
    val hero = theme.hero
    val foregroundInsetApplied = LocalHomeFocusSafety.current != null
    val actionFocus = Modifier.onFocusChanged { if (it.isFocused) onActionFocused() }
    if (hero.variant == HeroVariant.FullbleedCarousel) {
        PrimeLibraryHero(comic, comics, repository, onRead, onOpen, onImport, onSelect, modifier, readModifier,
            minimumHeightOverrideDp = minimumHeightOverrideDp)
        return
    }
    val cinema = theme.navigation.layout == ThemeNavigationPolicy.ImmersiveMenu
    val editorial = hero.editorial
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val actionHeight = maxOf(48f, 32f * fontScale + 24f)
    val normalOtherContent = hero.verticalPaddingDp * 2f + 22f * fontScale + actionHeight + 4f + hero.contentGapDp * 3f
    val compact = !editorial && minimumHeightOverrideDp != null &&
        theme.typography.titleLineHeightSp * hero.titleMaxLines * fontScale + normalOtherContent > minimumHeightOverrideDp
    val verticalPadding = if (compact) minOf(hero.verticalPaddingDp, 4) else hero.verticalPaddingDp
    val contentGap = if (compact) minOf(hero.contentGapDp, 4) else hero.contentGapDp
    val titleSize = homeHeroTitleSizeSp(theme.typography.titleSp.toFloat(), theme.typography.titleLineHeightSp.toFloat(),
        hero.titleMaxLines, fontScale, if (editorial || cinema) null else minimumHeightOverrideDp,
        verticalPadding * 2f + 22f * fontScale + actionHeight + 4f + contentGap * 3f)
    Box(modifier.fillMaxWidth().heightIn(min = (minimumHeightOverrideDp ?: hero.minimumHeightDp.toFloat()).dp).testTag("library-hero")) {
        Row(Modifier.align(if (hero.bottomAligned) Alignment.BottomStart else Alignment.CenterStart)
            .fillMaxWidth().padding(start = if (foregroundInsetApplied) 0.dp else hero.horizontalPaddingDp.dp,
                end = if (foregroundInsetApplied || TvColors.options.preset == ColorPreset.Max) 0.dp else hero.horizontalPaddingDp.dp,
                top = verticalPadding.dp, bottom = verticalPadding.dp),
            horizontalArrangement = Arrangement.spacedBy(hero.posterGapDp.dp), verticalAlignment = Alignment.CenterVertically) {
            if (editorial && comic != null) {
                val (posterWidth, posterHeight) = homePosterSizeDp(
                    hero.posterWidthDp.toFloat(), hero.posterHeightDp.toFloat(), posterHeightLimitDp)
                Box(Modifier.width(posterWidth.dp).height(posterHeight.dp).testTag("library-hero-poster")
                    .border(hero.posterBorderDp.dp, TvColors.FocusAccent, RoundedCornerShape(theme.shapes.posterRadiusDp.dp))
                    .background(Color.Black, RoundedCornerShape(6.dp)).padding(3.dp)) {
                    AsyncImage(repository.cover(comic.id), comic.title, Modifier.fillMaxSize(), contentScale = if (hero.cropPoster) ContentScale.Crop else ContentScale.Fit)
                }
            }
            Column(Modifier.fillMaxWidth(hero.textWidthFraction),
                verticalArrangement = Arrangement.spacedBy(contentGap.dp)) {
                if (editorial) Text(if (comic?.lastRead?.let { it > 0 } == true) "CONTINUE SUA HISTÓRIA" else "SUA PRÓXIMA HISTÓRIA",
                    color = TvColors.Amber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(comic?.title ?: "Seu universo de quadrinhos", Modifier.testTag("library-hero-title"),
                    style = TextStyle(shadow = if (TvColors.options.highContrast) null else Shadow(Color.Black.copy(alpha = .8f), Offset(0f, 2f), 6f)),
                    fontSize = titleSize.first.sp,
                    lineHeight = titleSize.second.sp,
                    fontFamily = libraryTitleFont(theme.typography.titleFamily),
                    fontWeight = if (theme.typography.titleExtraBold) FontWeight.ExtraBold else if (theme.typography.titleFamily == ThemeTitleFamily.Serif) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = hero.titleMaxLines, overflow = TextOverflow.Ellipsis)
                if (comic != null) {
                    Text(if (comic.lastRead > 0) "Página ${comic.currentPage + 1} de ${comic.pageCount}"
                        else "${comic.pageCount} páginas para explorar", color = TvColors.Muted, fontSize = 15.sp)
                    if (comic.lastRead > 0 && comic.pageCount > 0) ProgressTrack((comic.currentPage + 1f) / comic.pageCount,
                        Modifier.widthIn(max = hero.progressWidthDp.dp).fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvButton(if (comic.lastRead > 0) "Continuar leitura" else "Começar leitura", { onRead(comic.id) }, readModifier.then(actionFocus), primary = theme.colors.outlinedHeroAction || theme.colors.filledHeroAction, transparentBackground = !editorial, heroAction = true, textOnlyWhenUnfocused = hero.textOnlyActions)
                        TvButton("Ver detalhes", { onOpen(comic.id) }, actionFocus, transparentBackground = !editorial, heroAction = true, textOnlyWhenUnfocused = hero.textOnlyActions)
                    }
                } else {
                    Text("Adicione suas edições ou explore as fontes online.", color = TvColors.Muted, fontSize = 16.sp)
                    TvButton("Importar quadrinhos", onImport, readModifier.then(actionFocus), primary = theme.colors.outlinedHeroAction || theme.colors.filledHeroAction, transparentBackground = !editorial, heroAction = true)
                }
                if (cinema) Text("↓ Biblioteca    ·    → Menu", color = TvColors.Muted, fontSize = 14.sp)
            }
        }

    }
}
