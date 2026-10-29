package com.pelican.comictv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Native geometry coverage; compiling this test does not constitute a TV/device run. */
class HomeFocusGeometryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun firstAndLastFocusedCardsShareHeaderInsetAndKeepOverflowInsideRow() {
        var selected by mutableStateOf(ColorPreset.Amber)
        var focusCard: (Int) -> Unit = {}
        var rootLeft = 0f
        var rootRight = 0f
        var rowTop = 0f
        var rowBottom = 0f
        var cardLeft = 0f
        var cardRight = 0f
        var cardTop = 0f
        var cardBottom = 0f
        var density = 1f
        val safety = HomeFocusSafety()
        compose.setContent {
            ComicTvTheme(AppearanceOptions(preset = selected)) {
              Box(Modifier.size(640.dp, 360.dp)) {
                density = androidx.compose.ui.platform.LocalDensity.current.density
                val state = rememberLazyListState()
                val scope = rememberCoroutineScope()
                val first = remember { FocusRequester() }
                val last = remember { FocusRequester() }
                val theme = LocalThemeSpec.current
                focusCard = { index -> scope.launch {
                    state.scrollToItem(index)
                    withFrameNanos { }
                    (if (index == 0) first else last).requestFocus()
                } }
                CompositionLocalProvider(LocalHomeFocusSafety provides safety) {
                    LazyVerticalGrid(columns = GridCells.Fixed(1), modifier = Modifier.fillMaxSize()
                        .onGloballyPositioned { rootLeft = it.boundsInRoot().left; rootRight = it.boundsInRoot().right },
                        contentPadding = PaddingValues(horizontal = safety.horizontalDp.dp,
                            vertical = HomeSafeArea.verticalDp.dp)) {
                        item(span = { GridItemSpan(maxLineSpan) }) { Text("Heading", Modifier.testTag("heading")) }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LazyRow(state = state, modifier = Modifier.homeRowViewport().fillMaxWidth()
                                .onGloballyPositioned { rowTop = it.boundsInRoot().top; rowBottom = it.boundsInRoot().bottom },
                                contentPadding = safety.rowPadding, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(12) { index ->
                                    Box(Modifier.width(80.dp).height(114.dp).onGloballyPositioned {
                                        if (index == 0 || index == 11) {
                                            cardLeft = it.boundsInRoot().left; cardRight = it.boundsInRoot().right
                                            cardTop = it.boundsInRoot().top; cardBottom = it.boundsInRoot().bottom
                                        }
                                    }) {
                                        Card(onClick = {}, modifier = Modifier.fillMaxSize().homeFocusCard(
                                            theme.focus.collectionScale, theme.focus.cardBorderDp.toFloat())
                                            .then(if (index == 0) Modifier.focusRequester(first) else if (index == 11) Modifier.focusRequester(last) else Modifier)
                                            .testTag("card-$index"),
                                            scale = CardDefaults.scale(focusedScale = theme.focus.collectionScale),
                                            border = CardDefaults.border(focusedBorder = Border(BorderStroke(theme.focus.cardBorderDp.dp, TvColors.FocusAccent)))) {
                                            Text("$index")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
              }
            }
        }
        listOf(ColorPreset.Amber, ColorPreset.Flix, ColorPreset.Prime, ColorPreset.Max, ColorPreset.Cinema).forEach { preset ->
            compose.runOnIdle { selected = preset; focusCard(0) }
            compose.waitForIdle()
            compose.onNodeWithTag("card-0").assertIsFocused()
            compose.runOnIdle {
                assertEquals(48f * density, cardLeft - rootLeft, 1.1f)
                assertTrue(cardTop - rowTop >= safety.overflowDp * density - 1f)
                assertTrue(rowBottom - cardBottom >= safety.overflowDp * density - 1f)
            }
            compose.onNodeWithTag("heading").assertLeftPositionInRootIsEqualTo(48.dp)
            compose.runOnIdle { focusCard(11) }
            compose.waitForIdle()
            compose.onNodeWithTag("card-11").assertIsFocused()
            compose.runOnIdle { assertEquals(48f * density, rootRight - cardRight, 1.1f) }
        }
    }
}
