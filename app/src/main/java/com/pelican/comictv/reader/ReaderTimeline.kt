package com.pelican.comictv.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.pelican.comictv.ui.TvColors

/** Preview IO is separate from reading: progress changes only on confirmation. */
@Composable internal fun ReaderTimeline(state: ReaderState, modifier: Modifier = Modifier, section: Boolean = false,
    previews: List<TimelinePreview> = emptyList()) {
    val count = state.pageCount.coerceAtLeast(1)
    val selected = (state.timelinePage ?: state.page).coerceIn(0, count - 1)
    val fraction = if (count == 1) 0f else selected.toFloat() / (count - 1)
    val label = if (section) "Seção" else "Página"
    Column(modifier.fillMaxWidth().then(if (section) Modifier.padding(24.dp) else Modifier.height(320.dp))
        .background(if (section) TvColors.Panel else Color.Black, RoundedCornerShape(14.dp))
        .border(1.dp, TvColors.Amber.copy(alpha = .6f), RoundedCornerShape(14.dp))
            .padding(horizontal = 24.dp, vertical = if (section) 24.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(if (section) 16.dp else 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Escolher ${label.lowercase()}", color = Color.White, fontSize = if (section) 23.sp else 18.sp)
            Text(if (section) "$label ${selected + 1} de $count" else "Lendo: ${state.page + 1}  ·  $label ${selected + 1} de $count", color = TvColors.Amber, fontSize = if (section) 23.sp else 18.sp)
        }
        Row(Modifier.fillMaxWidth().height(if (section) 174.dp else 224.dp), horizontalArrangement = Arrangement.spacedBy(if (section) 16.dp else 12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically) {
            val ordered = if (state.rtl) previews.reversed() else previews
            ordered.forEach { preview ->
                val active = preview.page == selected
                var decodeFailed by remember(preview.file) { mutableStateOf(false) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (section) 6.dp else 3.dp)) {
                    Box(Modifier.width(if (section) { if (active) 110.dp else 82.dp } else { if (active) 134.dp else 114.dp })
                        .height(if (section) { if (active) 144.dp else 116.dp } else { if (active) 194.dp else 170.dp })
                        .background(Color.Black, RoundedCornerShape(6.dp))
                        .border(if (active) 2.dp else 1.dp, if (active) TvColors.Amber else Color.White.copy(alpha = .25f), RoundedCornerShape(6.dp))
                        .padding(4.dp), contentAlignment = Alignment.Center) {
                        if (preview.file != null && !decodeFailed) {
                            val context = LocalContext.current
                            val request = remember(preview.file) { ImageRequest.Builder(context).data(preview.file).size(240, 360).build() }
                            AsyncImage(request, "$label ${preview.page + 1}${if (active) ", selecionada" else ""}",
                                Modifier.fillMaxSize(), contentScale = ContentScale.Fit, onError = { decodeFailed = true })
                        } else Text(if (preview.loading) "Carregando…" else "Sem prévia", fontSize = 12.sp, color = TvColors.Muted)
                    }
                    Text("${preview.page + 1}", color = if (active) TvColors.Amber else Color.White, fontSize = if (section) 16.sp else 20.sp)
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(if (section) 44.dp else 8.dp).semantics {
            contentDescription = "Barra de ${if (section) "seções" else "páginas"}"
            stateDescription = "$label ${selected + 1} de $count. OK confirma; Voltar cancela."
            progressBarRangeInfo = ProgressBarRangeInfo((selected + 1).toFloat(), 1f..count.toFloat())
        }) {
            val inset = 10.dp.toPx()
            val width = (size.width - inset * 2).coerceAtLeast(0f)
            val y = size.height / 2
            val ratio = if (state.rtl) 1 - fraction else fraction
            val x = inset + width * ratio
            drawLine(TvColors.Muted.copy(alpha = .35f), Offset(inset, y), Offset(inset + width, y), (if (section) 5.dp else 2.dp).toPx())
            val start = if (state.rtl) inset + width else inset
            drawLine(TvColors.Amber, Offset(start, y), Offset(x, y), (if (section) 5.dp else 2.dp).toPx())
            val marks = minOf(count, 80)
            repeat(marks) { i ->
                val tick = if (marks == 1) ratio else i.toFloat() / (marks - 1)
                drawCircle(TvColors.Muted, (if (section) 2.dp else 1.dp).toPx(), Offset(inset + width * tick, y))
            }
            drawCircle(TvColors.Amber, (if (section) 10.dp else 4.dp).toPx(), Offset(x, y))
            drawCircle(Color.Black, (if (section) 3.dp else 2.dp).toPx(), Offset(x, y))
        }
        if (section) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (state.rtl) "$count" else "1", color = TvColors.Muted, fontSize = 16.sp)
            Text("Lendo: ${state.page + 1}", color = TvColors.Muted, fontSize = 16.sp)
            Text(if (state.rtl) "1" else "$count", color = TvColors.Muted, fontSize = 16.sp)
        }
        Text("← →  Escolher  ·  ↑ ↓  −10 / +10  ·  OK  Abrir  ·  Voltar  Cancelar", color = Color.White.copy(alpha = .75f), fontSize = if (section) 17.sp else 14.sp)
    }
}
