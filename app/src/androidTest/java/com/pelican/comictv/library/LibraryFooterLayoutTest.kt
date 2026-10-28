package com.pelican.comictv.library

import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pelican.comictv.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Native lazy-grid geometry and focus; execution on a device is separate from compilation. */
@OptIn(ExperimentalTestApi::class)
class LibraryFooterLayoutTest {
    @get:Rule val compose = createComposeRule()

    private val books = (0 until 24).map { index ->
        ComicEntity("footer-$index", "Edição $index", 30, currentPage = 3,
            lastRead = if (index == 0) 100L else 0L)
    }

    private fun fixture(preset: ColorPreset, focusedId: String? = null,
        searchEvents: MutableIntState = mutableIntStateOf(0), screenKey: MutableIntState = mutableIntStateOf(0),
        body: () -> Unit) = runBlocking {
        val real = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(real.cacheDir, "footer-layout-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(real) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
        val db = Room.inMemoryDatabaseBuilder(real, LibraryDatabase::class.java).build()
        val repository = LibraryRepository(context, db.comics(), collectionDao = db.collections())
        var shown by mutableStateOf(true)
        try {
            books.forEach { db.comics().insert(it) }
            compose.setContent {
                var initial by remember { mutableStateOf(focusedId == null) }
                var query by remember { mutableStateOf("") }
                var filter by remember { mutableStateOf("all") }
                ComicTvTheme(AppearanceOptions(preset = preset, textPercent = 130, highContrast = true)) {
                    if (shown) Box(Modifier.fillMaxSize()) {
                        key(screenKey.intValue) {
                            LibraryScreen(books, repository, focusedId, {}, {}, {},
                                requestInitialFocus = initial, onInitialFocus = { initial = false },
                                query = query, onQuery = { query = it }, filter = filter, onFilter = { filter = it },
                                searchRequest = searchEvents.intValue)
                        }
                    }
                }
            }
            body()
        } finally {
            compose.runOnIdle { shown = false }
            compose.waitForIdle()
            repository.closeBackgroundWork()
            db.close()
            root.deleteRecursively()
        }
    }

    private fun footerFollowsEveryCover(preset: ColorPreset) = fixture(preset) {
        if (preset == ColorPreset.Flix || preset == ColorPreset.Prime) {
            compose.onNodeWithText("Buscar").assertIsDisplayed().assertIsFocused()
        } else {
            compose.onNodeWithText("Continuar leitura").assertIsDisplayed().assertIsFocused()
            compose.onNodeWithText("Buscar").assertDoesNotExist()
        }
        compose.onNodeWithText("Todas").assertDoesNotExist()
        compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("library-footer"))
        compose.onNodeWithTag("library-footer").assertIsDisplayed()
        compose.onNodeWithText("Biblioteca · 24").assertIsDisplayed()
        compose.onNodeWithText("Histórico · 1").assertIsDisplayed()
        compose.onNodeWithText("Todas").assertIsDisplayed()
        val lastCover = compose.onNodeWithTag("library-cover-footer-23").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val footer = compose.onNodeWithTag("library-footer").fetchSemanticsNode().boundsInRoot
        assertTrue("O rodapé deve seguir a última capa da coleção", lastCover.bottom <= footer.top)
        if (preset == ColorPreset.Cinema) compose.onNodeWithText("Buscar").assertDoesNotExist()
        if (preset == ColorPreset.Max) compose.onNodeWithText("Buscar").assertIsDisplayed()
    }

    @Test fun flixKeepsOnlySearchAboveAndPlacesControlsAfterCollection() = footerFollowsEveryCover(ColorPreset.Flix)
    @Test fun cinemaKeepsControlsAtEndAndSearchInShell() = footerFollowsEveryCover(ColorPreset.Cinema)
    @Test fun maxPlacesSearchAndAllBrowsingControlsAfterCollection() = footerFollowsEveryCover(ColorPreset.Max)
    @Test fun primeKeepsStandaloneSearchAndSecondaryControlsAtEnd() = footerFollowsEveryCover(ColorPreset.Prime)

    @Test fun maxSearchReturnsToFooterWhenFilteringShortensTheCollection() = fixture(ColorPreset.Max) {
        compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("library-footer"))
        compose.onNodeWithText("Buscar").performClick()
        compose.onNodeWithContentDescription("Título do quadrinho").performTextInput("Sem correspondência")
        compose.onNodeWithText("Ver resultados").performClick()
        compose.onNodeWithText("Buscar").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithTag("library-footer").assertIsDisplayed()
        compose.onNodeWithText("Todas").assertIsDisplayed()
    }

    @Test fun collectionManagerReturnsToItsFooterButton() = fixture(ColorPreset.Flix) {
        compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("library-footer"))
        compose.onNodeWithTag("library-filters").performScrollToNode(hasText("Gerenciar coleções"))
        compose.onNodeWithText("Gerenciar coleções").performClick()
        compose.onNodeWithText("Criar coleção").assertIsFocused()
        compose.onNodeWithText("Fechar").performClick()
        compose.onNodeWithText("Gerenciar coleções").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithTag("library-footer").assertIsDisplayed()
    }

    @Test fun footerFiltersRequireOkAndKeepFocusAfterTheCollectionShrinks() = fixture(ColorPreset.Max) {
        compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("library-footer"))
        compose.onNodeWithText("Todas").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("★ Favoritos").assertIsFocused().assertIsNotSelected()
        compose.onNodeWithText("Todas").assertIsSelected()
        compose.onNodeWithText("★ Favoritos").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("★ Favoritos").assertIsDisplayed().assertIsFocused().assertIsSelected()
        compose.onNodeWithTag("library-footer").assertIsDisplayed()
        compose.onNodeWithText("Continuar leitura").assertIsNotDisplayed()
    }

    @Test fun returningFromDetailsRestoresTheCoverAfterRecentRows() = fixture(ColorPreset.Max, "footer-23") {
        compose.onNodeWithTag("library-cover-footer-23").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithText("Continuar leitura").assertIsNotDisplayed()
    }

    @Test fun cinemaShellSearchRestoresScrolledCoverAndDoesNotReplayAfterReentry() {
        val searchEvents = mutableIntStateOf(0)
        val screenKey = mutableIntStateOf(0)
        fixture(ColorPreset.Cinema, "footer-23", searchEvents, screenKey) {
            compose.onNodeWithTag("library-cover-footer-23").assertIsFocused()
            compose.runOnIdle { searchEvents.intValue++ }
            compose.onNodeWithContentDescription("Título do quadrinho").assertIsDisplayed()
            compose.onNodeWithText("Ver resultados").performClick()
            compose.onNodeWithTag("library-cover-footer-23").assertIsDisplayed().assertIsFocused()
            compose.onNodeWithText("Continuar leitura").assertIsNotDisplayed()
            compose.runOnIdle { screenKey.intValue++ }
            compose.onNodeWithContentDescription("Título do quadrinho").assertDoesNotExist()
            compose.onNodeWithTag("library-cover-footer-23").assertIsDisplayed().assertIsFocused()
        }
    }
}
