package com.pelican.comictv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.pelican.comictv.sources.catalogFirstEntryModifier
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class CatalogFirstTitleFocusTest {
    @get:Rule val compose = createComposeRule()
    private fun content(count: State<Int>, pending: MutableState<Boolean>) {
        compose.setContent { ComicTvTheme {
            TvShell("sources", {}, focusContentOnEntry = pending.value, onSectionFocused = { pending.value = false }) {
                val grid = rememberLazyGridState()
                val first = catalogFirstEntryModifier(count.value, busy = count.value == 0, loaded = count.value > 0, grid)
                LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), state = grid) {
                    item(span = { GridItemSpan(2) }) {
                        Box(Modifier.height(280.dp)) {
                            TvButton("Trocar fonte", {}, if (count.value == 0) first.testTag("folder") else Modifier.testTag("folder"))
                        }
                    }
                    item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(120.dp)) }
                    item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(120.dp)) }
                    items(count.value) { index ->
                        TvButton("Título $index", {}, (if (index == 0) first else Modifier).height(160.dp).testTag("title-$index"))
                    }
                }
            }
        } }
    }
    @Test fun delayedCatalogFocusesFirstTitleInsteadOfFolderDespiteTallHeader() {
        val count = mutableIntStateOf(0); val pending = mutableStateOf(true)
        content(count, pending)
        compose.onNodeWithTag("content-entry").assertIsFocused()
        compose.runOnIdle { count.intValue = 6 }
        compose.onNodeWithTag("title-0").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithTag("sidebar-expanded").assertDoesNotExist()
    }
    @Test fun userReturningToSidebarCancelsLateCatalogFocus() {
        val count = mutableIntStateOf(0); val pending = mutableStateOf(true)
        content(count, pending)
        compose.onNodeWithTag("content-entry").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("nav-sources").assertIsFocused()
        compose.runOnIdle { count.intValue = 6 }
        compose.onNodeWithTag("nav-sources").assertIsFocused()
    }
}
