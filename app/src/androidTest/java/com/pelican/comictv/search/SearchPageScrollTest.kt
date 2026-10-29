package com.pelican.comictv.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.pelican.comictv.ui.ComicTvTheme
import com.pelican.comictv.ui.TvButton
import org.junit.Rule
import org.junit.Test

class SearchPageScrollTest {
    @get:Rule val compose = createComposeRule()

    @Test fun headerScrollsAwayAndLastResultRemainsReachable() {
        compose.setContent { ComicTvTheme {
            Box(Modifier.size(640.dp, 360.dp)) {
                SearchPageGrid(rememberLazyGridState(), header = {
                    Column(Modifier.testTag("header").height(240.dp)) { TvButton("Pesquisar", {}) }
                }) {
                    items(40, key = { "result:$it" }) { index ->
                        Box(Modifier.height(180.dp).testTag("result:$index")) { TvButton("Obra $index", {}) }
                    }
                }
            }
        } }
        compose.onNodeWithTag("header").assertIsDisplayed()
        compose.onNodeWithTag("result:39").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("header").assertDoesNotExist()
        compose.onNodeWithText("Obra 39").performClick()
    }
}
