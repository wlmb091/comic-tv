package com.pelican.comictv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import com.pelican.comictv.library.ConfirmHistoryRemoval

@OptIn(ExperimentalTestApi::class)
class NavigationAndSummaryTest {
    @Test fun confirmedSectionCollapsesSidebarAndMovesFocusToContent() {
        val route=mutableStateOf("library")
        val pending=mutableStateOf<String?>(null)
        compose.setContent {ComicTvTheme {
            key(route.value) {
                TvShell(route.value,{pending.value=it;route.value=it},
                    focusContentOnEntry=pending.value==route.value,onSectionFocused={pending.value=null}) {
                    TvButton("Conteúdo",{},Modifier.testTag("content-action"))
                }
            }
        }}
        compose.onNodeWithTag("nav-sources").performClick()
        compose.onNodeWithTag("content-action").assertIsFocused()
        compose.onNodeWithTag("sidebar-expanded").assertDoesNotExist()
        compose.onNodeWithTag("content-action").performKeyInput {pressKey(Key.DirectionLeft)}
        compose.onNodeWithTag("sidebar-expanded").assertExists()
    }
    @get:Rule val compose = createComposeRule()

    @Test fun sidebarFocusFollowsRequestedSectionAndIgnoresCatalogUpdates() {
        val route = mutableStateOf("library")
        val requested = mutableStateOf<String?>("library")
        val loaded = mutableIntStateOf(0)
        compose.setContent {
            ComicTvTheme {
                key(route.value) {
                    TvShell(route.value, onNavigate = { requested.value = it; route.value = it },
                        focusSectionOnEntry = requested.value == route.value,
                        onSectionFocused = { requested.value = null }) {
                        Text("Resultado " + loaded.intValue)
                    }
                }
            }
        }
        compose.onNodeWithTag("nav-library").assertIsFocused()
        compose.onNodeWithTag("nav-recent").performClick()
        compose.onNodeWithTag("nav-recent").assertIsSelected().assertIsFocused()
        compose.onNodeWithTag("nav-library").assertIsNotSelected().assertIsNotFocused()
        compose.runOnIdle { loaded.intValue++ }
        compose.onNodeWithTag("nav-recent").assertIsFocused()
        compose.onNodeWithTag("nav-sources").performClick()
        compose.onNodeWithTag("nav-sources").assertIsSelected().assertIsFocused()
        compose.onNodeWithTag("nav-recent").assertIsNotSelected().assertIsNotFocused()
    }

    @Test fun expandedSummaryScrollsDownAndUpWhileItsButtonOwnsFocus() {
        lateinit var list: LazyListState
        compose.setContent {
            val focus = remember { FocusRequester() }
            list = rememberLazyListState()
            var expanded by remember { mutableStateOf(false) }
            ComicTvTheme {
                LazyColumn(Modifier.width(300.dp).height(220.dp).textPanelScroll(list), state = list) {
                    item {
                        Column {
                            TvButton(if (expanded) "Recolher resumo" else "Mostrar resumo",
                                { expanded = !expanded }, Modifier.focusRequester(focus).testTag("summary-toggle"))
                            Text(if (expanded) "Texto longo do resumo. ".repeat(200) else "Resumo curto.")
                        }
                    }
                }
                LaunchedEffect(Unit) { focus.requestFocus() }
            }
        }
        compose.onNodeWithTag("summary-toggle").performClick()
        compose.onNodeWithTag("summary-toggle").assertIsFocused()
        compose.onNodeWithTag("summary-toggle").performKeyInput { pressKey(Key.DirectionDown) }
        compose.runOnIdle { assertTrue(list.firstVisibleItemScrollOffset > 0) }
        compose.onNodeWithTag("summary-toggle").performKeyInput { pressKey(Key.DirectionUp) }
        compose.runOnIdle { assertEquals(0, list.firstVisibleItemScrollOffset) }
    }

    @Test fun removalRequiresConfirmationAndCancelDoesNotWrite() {
        var confirmed = 0
        val visible = mutableStateOf(true)
        compose.setContent {
            ComicTvTheme {
                if (visible.value) ConfirmHistoryRemoval("Edição de teste",
                    onDismiss = { visible.value = false }, onConfirm = { confirmed++ })
            }
        }
        compose.onNodeWithText("Cancelar").assertIsFocused().performClick()
        compose.runOnIdle { assertEquals(0, confirmed); visible.value = true }
        compose.onNodeWithText("Remover do histórico", substring = false).performClick()
        compose.runOnIdle { assertEquals(1, confirmed); assertFalse(visible.value) }
    }
}
