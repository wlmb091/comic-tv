package com.pelican.comictv.browser

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import org.junit.Assert.assertTrue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.pelican.comictv.ui.ComicTvTheme
import com.pelican.comictv.ui.TvButton
import org.junit.Rule
import org.junit.Test

class BrowserViewportTest {
    @get:Rule val compose = createComposeRule()
    @Test fun enteringSiteHidesToolbarAndExpandsSamePage() {
        var controlsVisible by mutableStateOf(true)
        compose.setContent { ComicTvTheme {
            Box(Modifier.size(640.dp, 360.dp)) {
                BrowserViewport(controlsVisible, controls = { TvButton("Endereço", {}) },
                    page = { Box(Modifier.fillMaxSize().testTag("site")) })
            }
        } }
        val before = compose.onNodeWithTag("site").fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { controlsVisible = false }
        compose.onNodeWithText("Endereço").assertDoesNotExist()
        val after = compose.onNodeWithTag("site").fetchSemanticsNode().boundsInRoot.height
        assertTrue(after > before)
        compose.runOnIdle { controlsVisible = true }
        compose.onNodeWithText("Endereço").assertIsDisplayed()
    }
    @Test fun longControlsScrollWhileSiteKeepsItsViewport() {
        compose.setContent { ComicTvTheme {
            Box(Modifier.size(640.dp, 360.dp)) {
                BrowserViewport(controls = {
                    Column { repeat(15) { TvButton("Controle $it", {}) } }
                }, page = { Box(Modifier.fillMaxSize().testTag("site")) })
            }
        } }
        compose.onNodeWithTag("site").assertIsDisplayed()
        compose.onNodeWithText("Controle 14").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("site").assertIsDisplayed()
        compose.onNodeWithText("Controle 0").performScrollTo().assertIsDisplayed()
    }
}
