package com.pelican.comictv.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.pelican.comictv.ui.TvColors
import kotlinx.coroutines.delay

internal fun readerModeNotice(previous: ReaderMode, current: ReaderMode): String? = when {
    previous == ReaderMode.Reading && current == ReaderMode.Pan -> "Mover página · Setas deslocam a imagem"
    previous == ReaderMode.Pan && current == ReaderMode.Reading -> "Zoom · ↑ ↓ ajustam a ampliação"
    else -> null
}

/** Passive notice: no dialog, focus target or key handler is added to the reader. */
@Composable internal fun ReaderModeNotice(mode: ReaderMode, modifier: Modifier = Modifier) {
    var previous by remember { mutableStateOf(mode) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(mode) {
        val next = readerModeNotice(previous, mode)
        previous = mode
        message = next
        if (next != null) { delay(2_000); message = null }
    }
    message?.let { Text(it, modifier.widthIn(max = 480.dp)
        .background(TvColors.Panel.copy(alpha = .96f), RoundedCornerShape(8.dp))
        .padding(horizontal = 18.dp, vertical = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
        color = TvColors.Amber, fontSize = 17.sp, lineHeight = 24.sp) }
}
