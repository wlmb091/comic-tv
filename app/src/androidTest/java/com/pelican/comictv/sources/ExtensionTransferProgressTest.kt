package com.pelican.comictv.sources

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.*
import com.pelican.comictv.ui.ComicTvTheme
import org.junit.Rule
import org.junit.Test

class ExtensionTransferProgressTest {
    @get:Rule val compose=createComposeRule()
    @Test fun percentageIsVisibleAndExposedToAccessibility() {
        compose.setContent {ComicTvTheme {ExtensionTransferProgress(.45f)}}
        compose.onNodeWithText("45%").assertExists()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,ProgressBarRangeInfo(.45f,0f..1f))).assertExists()
    }
}
