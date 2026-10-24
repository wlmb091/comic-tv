package com.pelican.comictv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

@Composable internal fun TextScaleControl(percent: Int, onStep: (Int) -> Unit, onSet: (Int) -> Unit,
    modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val value = percent.coerceIn(85, 130)
    Column(modifier.widthIn(max = 460.dp).fillMaxWidth().testTag("text-scale-control")
        .border(if (focused) 2.dp else 1.dp, if (focused) TvColors.Amber else TvColors.Track, RoundedCornerShape(8.dp))
        .onFocusChanged { focused = it.isFocused }
        .onPreviewKeyEvent { event ->
            if (!focused || event.key !in listOf(Key.DirectionLeft, Key.DirectionRight)) false
            else {
                if (event.type == KeyEventType.KeyDown) onStep(if (event.key == Key.DirectionRight) 1 else -1)
                true
            }
        }
        .semantics(mergeDescendants = true) {
            contentDescription = "Tamanho dos textos"
            stateDescription = "$value%"
            progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 85f..130f, 8)
            setProgress { onSet(it.toInt()); true }
        }.focusable().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("← Menor", color = TvColors.Muted, fontSize = 15.sp)
            Text("$value%", color = TvColors.Amber, fontSize = 21.sp)
            Text("Maior →", color = TvColors.Muted, fontSize = 15.sp)
        }
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val radius = 7.dp.toPx()
            val start = Offset(radius, size.height / 2)
            val end = Offset(size.width - radius, size.height / 2)
            val thumb = Offset(start.x + (end.x - start.x) * (value - 85) / 45f, start.y)
            drawLine(TvColors.Track, start, end, 4.dp.toPx(), StrokeCap.Round)
            drawLine(TvColors.Amber, start, thumb, 4.dp.toPx(), StrokeCap.Round)
            drawCircle(TvColors.Amber, radius, thumb)
        }
        Text("85% a 130% · ajuste com ← →", color = TvColors.Muted, fontSize = 14.sp)
    }
}
