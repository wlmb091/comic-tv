package com.pelican.comictv.browser

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
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
