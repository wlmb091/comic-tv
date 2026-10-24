package com.pelican.comictv.reader
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.io.File

@Composable internal fun ReaderBook(state: ReaderState, current: PageImage?, partner: PageImage?, partnerExpected: Boolean,
    cache: ReaderTileCache, options: ReaderImageOptions,
    modifier: Modifier = Modifier, onDisplayed: (File) -> Unit, onFailed: (File) -> Unit,
    onPartnerDisplayed: (File) -> Unit, onPartnerFailed: (File) -> Unit) {
    if (current == null) return
    val ordered = if (!partnerExpected) listOf(current) else if (state.rtl) listOf(partner, current) else listOf(current, partner)
    val gap = with(LocalDensity.current) { 2.dp.toPx() }
    HighResolutionPages(state, ordered, cache, options, modifier.padding(12.dp), gap,
        onDisplayed = { file -> if (file == current.file) onDisplayed(file) else onPartnerDisplayed(file) },
        onFailed = { file -> if (file == current.file) onFailed(file) else onPartnerFailed(file) })
}
