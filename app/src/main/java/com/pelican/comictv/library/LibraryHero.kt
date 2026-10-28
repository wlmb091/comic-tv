package com.pelican.comictv.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.Font
import com.pelican.comictv.R
import androidx.compose.ui.text.font.FontFamily
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
) {
    val theme = LocalThemeSpec.current
    val hero = theme.hero
    if (hero.variant == HeroVariant.FullbleedCarousel) {
        PrimeLibraryHero(comic, comics, repository, onRead, onOpen, onImport, onSelect, modifier, readModifier)
        return
    }
    val cinema = theme.navigation.layout == ThemeNavigationPolicy.ImmersiveMenu
    val editorial = hero.editorial
    Box(modifier.fillMaxWidth().heightIn(min = hero.minimumHeightDp.dp).testTag("library-hero")) {
        Row(Modifier.align(if (hero.bottomAligned) Alignment.BottomStart else Alignment.CenterStart)
            .fillMaxWidth().padding(horizontal = hero.horizontalPaddingDp.dp, vertical = hero.verticalPaddingDp.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            if (editorial && comic != null) {
                Box(Modifier.width(142.dp).height(206.dp)
                    .border(1.dp, TvColors.Amber.copy(alpha = .65f), RoundedCornerShape(6.dp))
                    .background(Color.Black, RoundedCornerShape(6.dp)).padding(3.dp)) {
                    AsyncImage(repository.cover(comic.id), comic.title, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
            Column(Modifier.fillMaxWidth(hero.textWidthFraction),
                verticalArrangement = Arrangement.spacedBy(hero.contentGapDp.dp)) {
                if (editorial) Text(if (comic?.lastRead?.let { it > 0 } == true) "CONTINUE SUA HISTÓRIA" else "SUA PRÓXIMA HISTÓRIA",
                    color = TvColors.Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(comic?.title ?: "Seu universo de quadrinhos", Modifier.testTag("library-hero-title"),
                    fontSize = theme.typography.titleSp.sp,
                    lineHeight = theme.typography.titleLineHeightSp.sp,
                    fontFamily = when (theme.typography.titleFamily) {
                        ThemeTitleFamily.Serif -> FontFamily.Serif
                        ThemeTitleFamily.Bebas -> FontFamily(Font(R.font.bebas_neue_regular))
                        ThemeTitleFamily.Default -> FontFamily.Default
                    },
                    fontWeight = if (theme.typography.titleExtraBold) FontWeight.ExtraBold else FontWeight.Normal,
                    maxLines = hero.titleMaxLines, overflow = TextOverflow.Ellipsis)
                if (comic != null) {
                    Text(if (comic.lastRead > 0) "Página ${comic.currentPage + 1} de ${comic.pageCount}"
                        else "${comic.pageCount} páginas para explorar", color = TvColors.Muted, fontSize = 15.sp)
                    if (comic.lastRead > 0 && comic.pageCount > 0) ProgressTrack((comic.currentPage + 1f) / comic.pageCount,
                        Modifier.widthIn(max = hero.progressWidthDp.dp).fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvButton(if (comic.lastRead > 0) "Continuar leitura" else "Começar leitura", { onRead(comic.id) }, readModifier, primary = theme.colors.outlinedHeroAction, transparentBackground = !editorial, heroAction = true)
                        TvButton("Ver detalhes", { onOpen(comic.id) }, transparentBackground = !editorial, heroAction = true)
                    }
                } else {
                    Text("Adicione suas edições ou explore as fontes online.", color = TvColors.Muted, fontSize = 16.sp)
                    TvButton("Importar quadrinhos", onImport, readModifier, primary = theme.colors.outlinedHeroAction, transparentBackground = !editorial, heroAction = true)
                }
                if (cinema) Text("↓ Biblioteca    ·    → Menu", color = TvColors.Muted, fontSize = 13.sp)
            }
        }
    }
}
