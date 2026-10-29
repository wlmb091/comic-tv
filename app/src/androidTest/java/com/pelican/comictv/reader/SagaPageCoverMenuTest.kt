package com.pelican.comictv.reader

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pelican.comictv.ui.ComicTvTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SagaPageCoverMenuTest {
    @get:Rule val compose = createComposeRule()
    @Test fun saveActionRemainsMountedAndDoesNotRunTwiceWhileBusy() {
        var busy by mutableStateOf(false)
        var calls = 0
        compose.setContent { ComicTvTheme {
            ReaderTools(ReaderState(pageCount = 30, mode = ReaderMode.Tools), false, {}, {}, {},
                onUsePageCover = { calls++; busy = true }, pageCoverBusy = busy)
        } }
        compose.onNodeWithContentDescription("Imagem e mais opções").performClick()
        compose.onNodeWithText("Usar página atual como capa da saga").performScrollTo().performClick()
        compose.onNodeWithText("Salvando capa…").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, calls) }
        compose.onNodeWithText("Voltar ao menu").performScrollTo().assertIsDisplayed()
    }
    @Test fun unloadedPageCannotTriggerSave() {
        var calls = 0
        compose.setContent { ComicTvTheme {
            ReaderTools(ReaderState(pageCount = 30, mode = ReaderMode.Tools), false, {}, {}, {},
                onUsePageCover = { calls++ }, pageCoverReady = false)
        } }
        compose.onNodeWithContentDescription("Imagem e mais opções").performClick()
        compose.onNodeWithText("Usar página atual como capa da saga").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, calls) }
    }
}
