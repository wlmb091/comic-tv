package com.pelican.comictv.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.pelican.comictv.ui.TvColors

/** Only a cursor is drawn: page IO and progress updates happen on confirmation. */
@Composable internal fun ReaderTimeline(state: ReaderState, modifier: Modifier = Modifier, section: Boolean = false) {
    val count = state.pageCount.coerceAtLeast(1)
    val selected = (state.timelinePage ?: state.page).coerceIn(0, count - 1)
    val fraction = if (count == 1) 0f else selected.toFloat() / (count - 1)
    val label = if (section) "Seção" else "Página"
    Column(modifier.fillMaxWidth().padding(24.dp)
        .background(TvColors.Panel, RoundedCornerShape(14.dp))
        .border(2.dp, TvColors.Amber, RoundedCornerShape(14.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Escolher ${label.lowercase()}", fontSize = 23.sp)
            Text("$label ${selected + 1} de $count", color = TvColors.Amber, fontSize = 23.sp)
        }
        Canvas(Modifier.fillMaxWidth().height(44.dp).semantics {
            contentDescription = "Barra de ${if (section) "seções" else "páginas"}"
            stateDescription = "$label ${selected + 1} de $count. OK confirma; Voltar cancela."
            progressBarRangeInfo = ProgressBarRangeInfo((selected + 1).toFloat(), 1f..count.toFloat())
        }) {
            val inset = 10.dp.toPx()
            val width = (size.width - inset * 2).coerceAtLeast(0f)
            val y = size.height / 2
            val ratio = if (state.rtl) 1 - fraction else fraction
            val x = inset + width * ratio
            drawLine(TvColors.Muted.copy(alpha = .35f), Offset(inset, y), Offset(inset + width, y), 5.dp.toPx())
            val start = if (state.rtl) inset + width else inset
            drawLine(TvColors.Amber, Offset(start, y), Offset(x, y), 5.dp.toPx())
            val marks = minOf(count, 80)
            repeat(marks) { i ->
                val tick = if (marks == 1) ratio else i.toFloat() / (marks - 1)
                drawCircle(TvColors.Muted, 2.dp.toPx(), Offset(inset + width * tick, y))
            }
            drawCircle(TvColors.Amber, 10.dp.toPx(), Offset(x, y))
            drawCircle(TvColors.Ink, 3.dp.toPx(), Offset(x, y))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (state.rtl) "$count" else "1", color = TvColors.Muted, fontSize = 16.sp)
            Text("Lendo: ${state.page + 1}", color = TvColors.Muted, fontSize = 16.sp)
            Text(if (state.rtl) "1" else "$count", color = TvColors.Muted, fontSize = 16.sp)
        }
        Text("← →  Escolher  ·  ↑ ↓  −10 / +10  ·  OK  Abrir  ·  Voltar  Cancelar", fontSize = 17.sp)
    }
}
