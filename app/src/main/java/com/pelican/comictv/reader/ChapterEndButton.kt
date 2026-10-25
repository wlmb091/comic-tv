package com.pelican.comictv.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import com.pelican.comictv.ui.TvButton

@Composable internal fun ChapterEndShortcut(focus: FocusRequester, onFocus: (Boolean) -> Unit,
    onNext: () -> Unit, onReturnToPage: () -> Unit, modifier: Modifier = Modifier) {
    TvButton("Próximo capítulo →", onNext, modifier.focusRequester(focus)
        .onFocusChanged { onFocus(it.isFocused) }.onPreviewKeyEvent { event ->
            if (event.key in listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)) {
                if (event.type == KeyEventType.KeyDown) onReturnToPage()
                true
            } else false
        })
}
