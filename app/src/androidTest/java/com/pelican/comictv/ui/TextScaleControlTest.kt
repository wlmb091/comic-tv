package com.pelican.comictv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TextScaleControlTest {
    @get:Rule val compose = createComposeRule()
    @Test fun arrowsAdjustTheLiveScaleWithinBoundsAndDownEscapes() {
        compose.setContent {
            var options by remember { mutableStateOf(AppearanceOptions()) }
            val first = remember { FocusRequester() }
            ComicTvTheme(options) {
                Column {
                    TextScaleControl(options.textPercentage,
                        { options = options.withTextPercentage(options.textPercentage + it * 5) },
                        { options = options.withTextPercentage(it) }, Modifier.focusRequester(first))
                    TvButton("Próxima opção", {})
                }
            }
            LaunchedEffect(Unit) { first.requestFocus() }
        }
        val control = compose.onNodeWithTag("text-scale-control")
        repeat(4) { control.performKeyInput { pressKey(Key.DirectionLeft) } }
        control.assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(85f, 85f..130f, 8)).assertIsFocused()
        repeat(12) { control.performKeyInput { pressKey(Key.DirectionRight) } }
        control.assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(130f, 85f..130f, 8)).assertIsFocused()
        control.performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Próxima opção").assertIsFocused()
    }
}
