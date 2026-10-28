package com.pelican.comictv.library

import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pelican.comictv.sources.*
import com.pelican.comictv.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Native coverage; compilation does not imply execution on the user's TV. */
@OptIn(ExperimentalTestApi::class)
class HomeRows127Test {
    @get:Rule val compose = createComposeRule()

    private fun fixture(preset: ColorPreset, textPercent: Int = 130, highContrast: Boolean = false, body: () -> Unit) = runBlocking {
        val real = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(real.cacheDir, "home-rows-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(real) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
        val db = Room.inMemoryDatabaseBuilder(real, LibraryDatabase::class.java).build()
        val repository = LibraryRepository(context, db.comics(), collectionDao = db.collections())
        val books = (0 until 24).map { ComicEntity("home-$it", "Livro $it", 30, currentPage = 3, lastRead = (100 - it).toLong()) }
        val sample = (0 until 24).map { OnlineTitle("source-$it", "Fonte $it") }
        var shown by mutableStateOf(true)
        try {
            books.forEach { db.comics().insert(it) }
            compose.setContent {
                val screens = rememberSaveableStateHolder()
                var details by remember { mutableStateOf(false) }
                var focusedId by remember { mutableStateOf<String?>(null) }
                var initial by remember { mutableStateOf(true) }
                ComicTvTheme(AppearanceOptions(preset = preset, textPercent = textPercent, highContrast = highContrast)) {
                    if (shown) Box(Modifier.fillMaxSize()) {
                        if (details) TvButton("Voltar do teste", { details = false })
                        else screens.SaveableStateProvider("library") {
                            LibraryScreen(books, repository, focusedId, { focusedId = it }, { details = true }, {},
                                requestInitialFocus = initial, onInitialFocus = { initial = false },
                                homeSources = HomeSourcePreviews(catalog = sample, latest = sample),
                                onSourceSection = { details = true }, onSourceTitle = { _, _ -> details = true })
                        }
                    }
                }
            }
            body()
        } finally {
            compose.runOnIdle { shown = false }
            compose.waitForIdle()
            repository.closeBackgroundWork(); db.close(); root.deleteRecursively()
        }
    }

    private fun orderedBoundedRows(preset: ColorPreset) = fixture(preset) {
        compose.onNodeWithText("Continuar leitura").assertIsDisplayed()
        val grid = compose.onNodeWithTag("library-grid")
        grid.performScrollToIndex(2)
        compose.onNodeWithTag("library-recent-home-0").assertIsDisplayed()
        grid.performScrollToIndex(4)
        compose.onNodeWithTag("home-collection-row").assertIsDisplayed().performScrollToIndex(12)
        compose.onNodeWithTag("home-collection-more").assertIsDisplayed()
        compose.onNodeWithTag("home-collection-home-12").assertDoesNotExist()
        grid.performScrollToIndex(5)
        compose.onNodeWithTag("home-source-Catálogo").assertIsDisplayed().performScrollToIndex(12)
        compose.onNodeWithText("Fonte 12").assertDoesNotExist()
        grid.performScrollToIndex(6)
        compose.onNodeWithTag("home-source-Novidades").assertIsDisplayed()
    }

    @Test fun flixHasOrderedBoundedRowsAt130Percent() = orderedBoundedRows(ColorPreset.Flix)
    @Test fun primeHasOrderedBoundedRowsAt130Percent() = orderedBoundedRows(ColorPreset.Prime)
    @Test fun maxHasOrderedBoundedRowsAt130Percent() = orderedBoundedRows(ColorPreset.Max)
    @Test fun cinemaHasOrderedBoundedRowsAt130Percent() = orderedBoundedRows(ColorPreset.Cinema)

    @Test fun localDetailsRestoreSameRowAndCover() = fixture(ColorPreset.Flix) {
        compose.onNodeWithTag("library-grid").performScrollToIndex(4)
        compose.onNodeWithTag("home-collection-row").performScrollToIndex(7)
        val book = compose.onNodeWithTag("home-collection-home-7")
        book.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        val left = book.fetchSemanticsNode().boundsInRoot.left
        book.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Voltar do teste").performClick()
        book.assertIsDisplayed().assertIsFocused()
        assertTrue(kotlin.math.abs(left - book.fetchSemanticsNode().boundsInRoot.left) < 2f)
        compose.onAllNodes(isFocused()).assertCountEquals(1)
    }

    @Test fun collectionMoreKeepsCompleteGridReachable() = fixture(ColorPreset.Max) {
        compose.onNodeWithTag("library-grid").performScrollToIndex(4)
        compose.onNodeWithTag("home-collection-row").performScrollToIndex(12)
        compose.onNodeWithTag("home-collection-more").performClick()
        compose.onNodeWithTag("library-cover-home-0").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("library-cover-home-23"))
        compose.onNodeWithTag("library-cover-home-23").performSemanticsAction(SemanticsActions.RequestFocus) { it() }.assertIsFocused()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("home-collection-more").assertIsDisplayed().assertIsFocused()
        compose.onAllNodes(isFocused()).assertCountEquals(1)
    }

    @Test fun visibleHeroAt100PercentKeepsTopPadding() = fixture(ColorPreset.Cinema, 100) {
        val title = compose.onNodeWithText("Continuar leitura").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(title.top >= 20f)
        compose.onNodeWithText("Continuar leitura").assertIsFocused()
    }
    @Test fun dpadMovesAcrossCoversAndDownToNextRows() = fixture(ColorPreset.Flix) {
        compose.onNodeWithTag("library-grid").performScrollToIndex(4)
        val first = compose.onNodeWithTag("home-collection-home-0")
        first.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        first.performKeyInput { pressKey(Key.DirectionRight) }
        val second = compose.onNodeWithTag("home-collection-home-1").assertIsFocused()
        second.performKeyInput { pressKey(Key.DirectionDown) }
        compose.onAllNodes(isFocused() and hasAnyAncestor(hasTestTag("home-source-Catálogo"))).assertCountEquals(1)
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        compose.onAllNodes(isFocused() and hasAnyAncestor(hasTestTag("home-source-Novidades"))).assertCountEquals(1)
    }

    @Test fun sourceMoreReturnsToItsOriginWithoutTakingLocalCoverFocus() = fixture(ColorPreset.Prime) {
        compose.onNodeWithTag("library-grid").performScrollToIndex(5)
        compose.onNodeWithTag("home-source-Catálogo").performScrollToIndex(12)
        val more = compose.onNode(hasText("Ver mais") and hasAnyAncestor(hasTestTag("home-source-Catálogo")))
        more.performSemanticsAction(SemanticsActions.RequestFocus) { it() }.performClick()
        compose.onNodeWithText("Voltar do teste").performClick()
        more.assertIsDisplayed().assertIsFocused()
        compose.onAllNodes(isFocused()).assertCountEquals(1)
    }

    @Test fun sourcePreviewHighContrastKeepsTextAndSuppressesArtwork() = fixture(ColorPreset.Prime, highContrast = true) {
        compose.onNodeWithTag("library-grid").performScrollToIndex(5)
        compose.onNodeWithTag("home-source-Catálogo").assertIsDisplayed()
        compose.onNodeWithText("Fonte 0").assertIsDisplayed()
        compose.onNodeWithTag("home-source-cover-source-0").assertDoesNotExist()
    }

}
