package com.pelican.comictv.sources

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pelican.comictv.ui.ComicTvTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ChapterOrderUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun orderControlsReorderTheDetailsListAndOpenExactChapter() {
        var opened: String?=null
        val chapters=listOf(OnlineChapter("ten","Chapter 10",10f),OnlineChapter("two","Chapter 2",2f),OnlineChapter("one","Chapter 1",1f))
        compose.setContent {ComicTvTheme {SourceDetails(OnlineDetails(OnlineTitle("title","Title"),chapters),false){opened=it.key}}}
        compose.onNodeWithText("Do início").performClick().assertIsSelected()
        val one=compose.onNodeWithText("Chapter 1").fetchSemanticsNode().boundsInRoot.top
        val ten=compose.onNodeWithText("Chapter 10").fetchSemanticsNode().boundsInRoot.top
        assertTrue(one<ten)
        compose.onNodeWithText("Chapter 1").performClick()
        compose.runOnIdle {assertEquals("one",opened)}
        compose.onNodeWithText("Do final").performClick().assertIsSelected()
        assertTrue(compose.onNodeWithText("Chapter 10").fetchSemanticsNode().boundsInRoot.top < compose.onNodeWithText("Chapter 1").fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithText("Ordem da fonte").performClick().assertIsSelected()
    }
}
