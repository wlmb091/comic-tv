package com.pelican.comictv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
    @Test fun returningToSidebarDuringSourceSwitchCancelsLateTitleFocus() {
        val page = mutableIntStateOf(0)
        compose.setContent { ComicTvTheme {
            TvShell("recent", {}) {
                val navigate = LocalContentNavigation.current
                key(page.intValue) {
                    val first = preferredContentEntryModifier(enabled = page.intValue != 1)
                    if (page.intValue == 0) TvButton("Fonte A", { navigate { page.intValue = 1 } }, first)
                    else if (page.intValue == 2) TvButton("Primeiro título", {}, first)
                    else Text("Carregando fonte")
                }
            }
        } }
        compose.onNodeWithText("Fonte A").performClick()
        compose.onNodeWithTag("content-entry").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("nav-recent").assertIsFocused()
        compose.runOnIdle { page.intValue = 2 }
        compose.onNodeWithTag("nav-recent").assertIsFocused()
        compose.onNodeWithText("Primeiro título").assertIsNotFocused()
    }

    @Test fun folderAndAsynchronousSourceChangeKeepSidebarCollapsed() {
        val page = mutableIntStateOf(0)
        val loading = mutableStateOf(false)
        compose.setContent { ComicTvTheme {
            TvShell("sources", {}) {
                val navigate = LocalContentNavigation.current
                key(page.intValue) {
                    val first = preferredContentEntryModifier(enabled = !loading.value)
                    when (page.intValue) {
                        0 -> TvButton("Trocar fonte", { navigate { page.intValue = 1 } }, first)
                        1 -> TvButton("Fonte A", { navigate { loading.value = true } }, first)
                        else -> TvButton("Primeiro título", {}, first)
                    }
                }
            }
        } }
        compose.onNodeWithText("Trocar fonte").performClick()
        compose.onNodeWithText("Fonte A").assertIsFocused()
        compose.onNodeWithTag("sidebar-expanded").assertDoesNotExist()
        compose.onNodeWithText("Fonte A").performClick()
        compose.onNodeWithTag("content-entry").assertIsFocused()
        compose.runOnIdle { page.intValue = 2; loading.value = false }
        compose.onNodeWithText("Primeiro título").assertIsFocused()
        compose.onNodeWithTag("sidebar-expanded").assertDoesNotExist()
    }

    @Test fun confirmingLibraryRevealsAllWhenCollectionsWereScrolledHorizontally() {
        var filter = "favorites"
        compose.setContent { ComicTvTheme {
            var pending by remember { mutableStateOf(true) }
            TvShell("library", {}, focusContentOnEntry = pending, onSectionFocused = { pending = false }) {
                val filters = rememberLazyListState(initialFirstVisibleItemIndex = 6)
                val first = preferredContentEntryModifier(prepare = { filters.scrollToItem(0) })
                com.pelican.comictv.library.LibraryFilters(
                    (1..20).map { com.pelican.comictv.library.CollectionEntity("$it", "Coleção $it", "colecao $it") },
                    filter, { filter = it }, {}, entryModifier = first, listState = filters)
            }
        } }
        compose.onNodeWithText("Todas").assertIsFocused().assertIsNotSelected()
        compose.runOnIdle { assertEquals("favorites", filter) }
        compose.onNodeWithTag("sidebar-expanded").assertDoesNotExist()
    }

    @Test fun confirmedSectionPreparesFirstItemFromSavedScrollPosition() {
        compose.setContent { ComicTvTheme {
            var pending by remember { mutableStateOf(true) }
            TvShell("sources", {}, focusContentOnEntry = pending, onSectionFocused = { pending = false }) {
                val grid = rememberLazyGridState(initialFirstVisibleItemIndex = 12)
                val first = preferredContentEntryModifier(prepare = { grid.scrollToItem(0) })
                LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), state = grid) {
                    items(30) { index ->
                        TvButton("Título $index", {}, (if (index == 0) first else Modifier)
                            .height(150.dp).testTag("title-$index"))
                    }
                }
            }
        } }
        compose.onNodeWithTag("title-0").assertIsFocused()
        compose.onNodeWithTag("sidebar-expanded").assertDoesNotExist()
    }

    @Test fun firstTitleReceivesFocusAfterLoadAndLaterUpdatesDoNotStealIt() {
        val loaded = mutableStateOf(false)
        val refresh = mutableIntStateOf(0)
        compose.setContent { ComicTvTheme {
            var pending by remember { mutableStateOf(true) }
            TvShell("sources", {}, focusContentOnEntry = pending, onSectionFocused = { pending = false }) {
                val first = preferredContentEntryModifier(enabled = loaded.value)
                Column {
                    TvButton("Trocar fonte", {}, Modifier.testTag("source-switch"))
                    if (loaded.value) {
                        TvButton("Primeiro título ${refresh.intValue}", {}, first.testTag("first-title"))
                        TvButton("Segundo título", {}, Modifier.testTag("second-title"))
                    } else Text("Carregando")
                }
            }
        } }
        compose.onNodeWithTag("content-entry").assertIsFocused()
        compose.runOnIdle { loaded.value = true }
        compose.onNodeWithTag("first-title").assertIsFocused()
        compose.onNodeWithTag("sidebar-expanded").assertDoesNotExist()
        compose.onNodeWithTag("first-title").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("second-title").assertIsFocused()
        compose.runOnIdle { refresh.intValue++ }
        compose.onNodeWithTag("second-title").assertIsFocused()
    }

    @Test fun loadingTitleDoesNotTakeFocusAfterUserReturnsToSidebar() {
        val loaded = mutableStateOf(false)
        compose.setContent { ComicTvTheme {
            var pending by remember { mutableStateOf(true) }
            TvShell("recent", {}, focusContentOnEntry = pending, onSectionFocused = { pending = false }) {
                val first = preferredContentEntryModifier(enabled = loaded.value)
                if (loaded.value) TvButton("Primeiro", {}, first.testTag("first-title")) else Text("Carregando")
            }
        } }
        compose.onNodeWithTag("content-entry").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("nav-recent").assertIsFocused()
        compose.runOnIdle { loaded.value = true }
        compose.onNodeWithTag("nav-recent").assertIsFocused()
        compose.onNodeWithTag("first-title").assertIsNotFocused()
    }

    @Test fun importRailReturnFocusesChooseFileWithoutOpeningPicker() {
        var opened = 0
        compose.setContent { ComicTvTheme {
            TvShell("import", {}) {
                com.pelican.comictv.transfer.ImportScreen(false, null, {}, {}, { opened++ }, {})
            }
        } }
        compose.onNodeWithTag("nav-settings").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("nav-settings").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Escolher arquivo").assertIsFocused()
        compose.runOnIdle { assertEquals(0, opened) }
    }

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
        compose.onNodeWithTag("content-entry").assertIsFocused()
        compose.onNodeWithTag("sidebar-expanded").assertDoesNotExist()
        compose.onNodeWithTag("content-entry").performKeyInput {pressKey(Key.DirectionDown)}
        compose.onNodeWithTag("content-action").assertIsFocused().performKeyInput {pressKey(Key.DirectionLeft)}
        compose.onNodeWithTag("sidebar-expanded").assertExists()
    }
    @Test fun leavingLowerSidebarItemKeepsPageAtTopUntilDownIsPressed() {
        lateinit var list: LazyListState
        val initial = FocusRequester()
        compose.setContent { ComicTvTheme {
            list = rememberLazyListState()
            TvShell("library", {}) {
                LazyColumn(Modifier.fillMaxSize(), state = list) {
                    item { TvButton("Top", {}, Modifier.focusRequester(initial).testTag("top")) }
                    item { Spacer(Modifier.height(600.dp)) }
                    item { TvButton("Below", {}, Modifier.testTag("below")) }
                }
            }
            LaunchedEffect(Unit) { initial.requestFocus() }
        } }
        compose.onNodeWithTag("nav-settings").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("nav-settings").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("content-entry").assertIsFocused()
        compose.onNodeWithTag("sidebar-expanded").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, list.firstVisibleItemIndex); assertEquals(0, list.firstVisibleItemScrollOffset) }
        compose.onNodeWithTag("content-entry").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("top").assertIsFocused()
        compose.onNodeWithTag("top").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("below").assertIsFocused()
        compose.runOnIdle { assertTrue(list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0) }
    }
    @Test fun lateContentReplacementDoesNotMoveFocusBackToSidebar() {
        val loaded = mutableStateOf(false)
        compose.setContent { ComicTvTheme {
            var pending by remember { mutableStateOf(true) }
            TvShell("sources", {}, focusContentOnEntry = pending, onSectionFocused = { pending = false }) {
                if (loaded.value) TvButton("Loaded catalog", {}, Modifier.testTag("loaded")) else Text("Loading…")
            }
        } }
        compose.onNodeWithTag("content-entry").assertIsFocused()
        compose.onNodeWithTag("sidebar-expanded").assertDoesNotExist()
        compose.runOnIdle { loaded.value = true }
        compose.onNodeWithTag("content-entry").assertIsFocused()
        compose.onNodeWithTag("sidebar-expanded").assertDoesNotExist()
        compose.onNodeWithTag("content-entry").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("loaded").assertIsFocused()
    }
    @Test fun firstOkOnlyEntersContentAndSecondOkActivatesChild() {
        var activated = 0
        compose.setContent { ComicTvTheme {
            var pending by remember { mutableStateOf(true) }
            TvShell("library", {}, focusContentOnEntry = pending, onSectionFocused = { pending = false }) {
                TvButton("Activate", { activated++ }, Modifier.testTag("activate"))
            }
        } }
        compose.onNodeWithTag("content-entry").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("activate").assertIsFocused()
        compose.runOnIdle { assertEquals(0, activated) }
        compose.onNodeWithTag("activate").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, activated) }
    }
    @Test fun sidebarReturnFocusesVisibleAllWithoutSelectingItOrExtraDown() {
        var filter by mutableStateOf("favorites")
        compose.setContent { ComicTvTheme {
            TvShell("library", {}) {
                com.pelican.comictv.library.LibraryFilters(emptyList(), filter, { filter = it }, {})
            }
        } }
        compose.onNodeWithTag("nav-settings").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("nav-settings").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Todas").assertIsFocused().assertIsNotSelected()
        compose.runOnIdle { assertEquals("favorites", filter) }
        compose.onNodeWithTag("sidebar-expanded").assertDoesNotExist()
        compose.onNodeWithText("Todas").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("★ Favoritos").assertIsFocused()
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
