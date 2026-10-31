package com.pelican.comictv.browser

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pelican.comictv.ui.ComicTvTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class BrowserHomeFocusTest {
    @get:Rule val compose = createComposeRule()
    private fun verifyEntry(fromFooter: Boolean) {
        var opened = ""
        val tab = BrowserTab(id = "home")
        val state = BrowserSessionState(tabs = listOf(tab), selectedId = tab.id)
        compose.setContent { ComicTvTheme {
            var chrome by remember { mutableStateOf(true) }
            val homeFocus = remember { FocusRequester() }
            val addressFocus = remember { FocusRequester() }
            val owner = browserFocusOwner(true, "", chrome, false)
            LaunchedEffect(owner) { if (owner == BrowserFocusOwner.Home) { withFrameNanos { }; homeFocus.requestFocus() } }
            BrowserViewport(controlsVisible = chrome,
                controls = { BrowserChrome(state, "", {}, addressFocus, false, false, 0, 100, "", {}, homeFocus) },
                page = { BrowserHome({ opened = it }, {}, homeFocus) { chrome = false } })
        } }
        val start = compose.onNodeWithContentDescription(if (fromFooter) "Fechar aba atual" else "Aba 1: Nova aba")
        start.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        start.assertIsFocused().performKeyInput { pressKey(if (fromFooter) Key.DirectionUp else Key.DirectionDown) }
        compose.onNodeWithText("Só Quadrinhos").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals("https://site.soquadrinhos.com", opened) }
        compose.onNodeWithContentDescription("Fechar aba atual").assertDoesNotExist()
    }
    @Test fun upFromTheFirstSiteRowAsksForTheControls() {
        var requests = 0
        compose.setContent { ComicTvTheme { BrowserHome({}, {}, onTopUp = { requests++ }) } }
        val first = compose.onNodeWithText("Só Quadrinhos")
        first.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        first.assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.runOnIdle { assertEquals(1, requests) }
    }
    @Test fun downFromTheLastHomeRowAsksForTheFooter() {
        var requests = 0
        compose.setContent { ComicTvTheme { BrowserHome({}, {}, onBottomDown = { requests++ }) } }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Google"))
        val last = compose.onNodeWithText("Google")
        last.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        last.assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.runOnIdle { assertEquals(1, requests) }
    }
    @Test fun tabsDownEntersHomeInsteadOfJumpingToFooter() = verifyEntry(false)
    @Test fun footerUpEntersHomeInsteadOfJumpingToTabs() = verifyEntry(true)
}
