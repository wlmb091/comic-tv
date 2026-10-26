package com.pelican.comictv.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pelican.comictv.reader.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ReaderModeNoticeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun modeNoticeExpiresAndPreservesReaderFocusAcrossRapidToggles() {
        compose.setContent { ComicTvTheme {
            var mode by remember { mutableStateOf(ReaderMode.Reading) }
            val page = remember { FocusRequester() }
            TvButton("Página", { mode = if (mode == ReaderMode.Reading) ReaderMode.Pan else ReaderMode.Reading }, Modifier.focusRequester(page))
            ReaderModeNotice(mode)
            LaunchedEffect(Unit) { page.requestFocus() }
        } }
        compose.onNodeWithText("Mover página", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Página").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Mover página", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Página").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Zoom ·", substring = true).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(2_100)
        compose.onNodeWithText("Zoom ·", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Página").assertIsFocused()
    }
}
