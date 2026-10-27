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
) {
    val preset = TvColors.options.preset
    val cinema = preset == ColorPreset.Cinema
    val editorial = preset == ColorPreset.Amber || preset in listOf(ColorPreset.Green, ColorPreset.Blue, ColorPreset.Violet)
    Box(modifier.fillMaxWidth().heightIn(min = if (editorial) 206.dp else 230.dp)) {
        if (!editorial && !cinema) CinemaBackdrop(comic?.id, repository, Modifier.matchParentSize())
        Row(Modifier.align(if (cinema) Alignment.BottomStart else Alignment.CenterStart)
            .fillMaxWidth().padding(horizontal = if (cinema) 32.dp else 14.dp, vertical = if (cinema) 38.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            if (editorial && comic != null) {
                Box(Modifier.width(142.dp).height(206.dp)
                    .border(1.dp, TvColors.Amber.copy(alpha = .65f), RoundedCornerShape(6.dp))
                    .background(Color.Black, RoundedCornerShape(6.dp)).padding(3.dp)) {
                    AsyncImage(repository.cover(comic.id), comic.title, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
            Column(Modifier.fillMaxWidth(if (editorial) .94f else if (cinema) .65f else .61f),
                verticalArrangement = Arrangement.spacedBy(if (cinema) 13.dp else 10.dp)) {
                if (editorial) Text(if (comic?.lastRead?.let { it > 0 } == true) "CONTINUE SUA HISTÓRIA" else "SUA PRÓXIMA HISTÓRIA",
                    color = TvColors.Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(comic?.title ?: "Seu universo de quadrinhos",
                    fontSize = if (cinema) 42.sp else if (editorial) 28.sp else 34.sp,
                    lineHeight = if (cinema) 46.sp else if (editorial) 32.sp else 37.sp,
                    fontFamily = if (cinema) FontFamily.Serif else FontFamily.Default,
                    fontWeight = if (cinema) FontWeight.Normal else FontWeight.ExtraBold,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (comic != null) {
                    Text(if (comic.lastRead > 0) "Página ${comic.currentPage + 1} de ${comic.pageCount}"
                        else "${comic.pageCount} páginas para explorar", color = TvColors.Muted, fontSize = 15.sp)
                    if (comic.lastRead > 0 && comic.pageCount > 0) ProgressTrack((comic.currentPage + 1f) / comic.pageCount,
                        Modifier.widthIn(max = if (editorial) 280.dp else 240.dp).fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvButton(if (comic.lastRead > 0) "Continuar leitura" else "Começar leitura", { onRead(comic.id) }, readModifier)
                        TvButton("Ver detalhes", { onOpen(comic.id) })
                    }
                } else {
                    Text("Adicione suas edições ou explore as fontes online.", color = TvColors.Muted, fontSize = 16.sp)
                    TvButton("Importar quadrinhos", onImport, readModifier)
                }
                if (cinema) Text("↓ Biblioteca    ·    ← Menu", color = TvColors.Muted, fontSize = 13.sp)
            }
        }
    }
}
