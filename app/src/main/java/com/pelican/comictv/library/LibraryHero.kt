package com.pelican.comictv.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.pelican.comictv.ui.*

@Composable internal fun LibraryHero(comic: ComicEntity?, repository: LibraryRepository,
    onRead: (String) -> Unit, onOpen: (String) -> Unit, onImport: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 204.dp)) {
        Box(Modifier.matchParentSize()) {
        comic?.takeUnless { TvColors.options.highContrast }?.let {
            AsyncImage(repository.cover(it.id), null, Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(.56f),
                contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                listOf(Color.Black, Color.Black.copy(alpha = .94f), Color.Black.copy(alpha = .1f)))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .8f)))))
        }
        }
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(.68f).padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (comic?.lastRead?.let { it > 0 } == true) "CONTINUE SUA HISTÓRIA" else "SUA PRÓXIMA HISTÓRIA",
                color = TvColors.Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(comic?.title ?: "Seu universo de quadrinhos",
                fontSize = 29.sp, lineHeight = 33.sp, fontWeight = FontWeight.ExtraBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (comic != null) {
                Text(if (comic.lastRead > 0) "Página " + (comic.currentPage + 1) + " de " + comic.pageCount
                    else comic.pageCount.toString() + " páginas para explorar", color = TvColors.Muted, fontSize = 15.sp)
                if (comic.lastRead > 0 && comic.pageCount > 0) {
                    ProgressTrack((comic.currentPage + 1f) / comic.pageCount,
                        Modifier.widthIn(max = 240.dp).fillMaxWidth())
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(if (comic.lastRead > 0) "Continuar leitura" else "Começar leitura", { onRead(comic.id) })
                    TvButton("Ver detalhes", { onOpen(comic.id) })
                }
            } else {
                Text("Adicione suas edições ou explore as fontes online.", color = TvColors.Muted, fontSize = 16.sp)
                TvButton("Importar quadrinhos", onImport)
            }
        }
    }
}
