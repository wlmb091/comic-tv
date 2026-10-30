package com.pelican.comictv.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.pelican.comictv.ui.ComicTvTheme
import com.pelican.comictv.ui.TvButton
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SagaEditionsPageTest {
    @get:Rule val compose = createComposeRule()
    private val editions = (0 until 20).map { ComicEntity("$it", "Edição $it", 30, currentPage = it, lastRead = it.toLong()) }

    @Test fun initiallyResumesRequestedEditionAndHeaderReturnsToIt() {
        var opened = ""
        compose.setContent { ComicTvTheme {
            val header = remember { FocusRequester() }
            Box(Modifier.size(960.dp, 540.dp)) {
                SagaEditionsPage("saga", editions, rememberLazyGridState(), "2", header, false, { null }, {},
                    { opened = it.id }, {}, header = { back ->
                        TvButton("Comandos", back, Modifier.focusRequester(header))
                    })
            }
        } }
        compose.onNodeWithTag("saga-edition-2").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Comandos").assertIsFocused().performClick()
        compose.onNodeWithTag("saga-edition-2").assertIsFocused().performClick()
        compose.runOnIdle { assertEquals("2", opened) }
    }

    @Test fun lastEditionCanBeRevealedAndCoverModePreservesSelection() {
        var coverMode by mutableStateOf(false)
        var selected = ""
        compose.setContent { ComicTvTheme {
            val header = remember { FocusRequester() }
            Box(Modifier.size(960.dp, 540.dp)) {
                SagaEditionsPage("saga", editions, rememberLazyGridState(), "19", header, coverMode, { null }, {},
                    { selected = it.id }, {}, header = { back -> TvButton("Comandos", back, Modifier.focusRequester(header)) })
            }
        } }
        compose.onNodeWithTag("saga-edition-19").assertIsFocused().assertIsDisplayed()
        compose.runOnIdle { coverMode = true }
        compose.onNodeWithTag("saga-edition-19").assertIsFocused().performClick()
        compose.runOnIdle { assertEquals("19", selected) }
        compose.onNodeWithTag("saga-edition-19").performKeyInput { pressKey(Key.DirectionDown) }.assertIsFocused()
    }
}
