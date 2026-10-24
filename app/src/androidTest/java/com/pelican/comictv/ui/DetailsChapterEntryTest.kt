package com.pelican.comictv.ui

import androidx.lifecycle.SavedStateHandle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pelican.comictv.sources.OnlineEditionsScreen
import com.pelican.comictv.sources.OnlineEditionsViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DetailsChapterEntryTest {
    @get:Rule val compose = createComposeRule()
    @Test fun detailsEntryCanReturnWithoutOpeningTheReaderEvenWhenSourceFails() {
        var opened = 0
        var read = 0
        var back = 0
        val viewModel = OnlineEditionsViewModel("online-test", SavedStateHandle(),
            { error("Fonte indisponível") }, { _, _ -> opened++; "reader" })
        compose.setContent {
            ComicTvTheme {
                OnlineEditionsScreen(viewModel, { read++ }, { back++ }, backLabel = "Voltar aos detalhes")
            }
        }
        compose.onNodeWithText("Voltar aos detalhes").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, back)
            assertEquals(0, read)
            assertEquals(0, opened)
        }
    }
}
