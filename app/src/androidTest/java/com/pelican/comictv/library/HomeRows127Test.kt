package com.pelican.comictv.library

import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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

    private fun fixture(preset: ColorPreset, textPercent: Int = 130, highContrast: Boolean = false,
        withShell: Boolean = false, longTitle: Boolean = false, body: () -> Unit) = runBlocking {
        val real = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(real.cacheDir, "home-rows-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(real) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
        val db = Room.inMemoryDatabaseBuilder(real, LibraryDatabase::class.java).build()
        val repository = LibraryRepository(context, db.comics(), collectionDao = db.collections())
        val books = (0 until 24).map { ComicEntity("home-$it",
            if (longTitle && it == 0) "A fantástica jornada dos guardiões da cidade perdida" else "Livro $it",
            30, currentPage = 3, lastRead = (100 - it).toLong()) }
        val sample = (0 until 24).map { OnlineTitle("source-$it", "Fonte $it") }
        var shown by mutableStateOf(true)
        try {
            books.forEach { db.comics().insert(it) }
            compose.setContent {
                val screens = rememberSaveableStateHolder()
                var details by remember { mutableStateOf(false) }
                var focusedId by remember { mutableStateOf<String?>(null) }
                var initial by remember { mutableStateOf(true) }
                val platformDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(platformDensity.density, 1f)) {
                ComicTvTheme(AppearanceOptions(preset = preset, textPercent = textPercent, highContrast = highContrast)) {
                    if (shown) Box(if (withShell) Modifier.requiredSize(960.dp, 540.dp) else Modifier.fillMaxSize()) {
                        if (details) TvButton("Voltar do teste", { details = false })
                        else screens.SaveableStateProvider("library") {
                            val library: @Composable () -> Unit = { LibraryScreen(books, repository, focusedId, { focusedId = it }, { details = true }, {},
                                requestInitialFocus = initial, onInitialFocus = { initial = false },
                                homeSources = HomeSourcePreviews(catalog = sample, latest = sample),
                                onSourceSection = { details = true }, onSourceTitle = { _, _ -> details = true }) }
                            if (withShell) TvShell("library", {}) { library() } else library()
                        }
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
        val extra = if (preset == ColorPreset.Max) 1 else 0
        grid.performScrollToIndex(2)
        compose.onNodeWithTag("library-recent-home-0").assertIsDisplayed()
        grid.performScrollToIndex(4 + extra)
        compose.onNodeWithTag("home-collection-row").assertIsDisplayed().performScrollToIndex(12)
        compose.onNodeWithTag("home-collection-more").assertIsDisplayed()
        compose.onNodeWithTag("home-collection-home-12").assertDoesNotExist()
        grid.performScrollToIndex(5 + extra)
        compose.onNodeWithTag("home-source-Catálogo").assertIsDisplayed().performScrollToIndex(12)
        compose.onNodeWithText("Fonte 12").assertDoesNotExist()
        grid.performScrollToIndex(6 + extra)
        compose.onNodeWithTag("home-source-Novidades").assertIsDisplayed()
    }

    private fun rowsReachRealViewportEdges(preset: ColorPreset) = fixture(preset, 100, withShell = true) {
        val shell = compose.onNodeWithTag("theme-shell").getUnclippedBoundsInRoot()
        val grid = compose.onNodeWithTag("library-grid")
        val viewport = grid.getUnclippedBoundsInRoot()
        val sidebar = preset == ColorPreset.Amber || preset == ColorPreset.Flix
        val rail = if (sidebar) ThemeSpec.forPreset(preset).navigation.sidebarCollapsedWidthDp.dp else 0.dp
        assertTrue("Content reserves only the actual sidebar rail", kotlin.math.abs((viewport.left - shell.left - rail).value) < 1f)
        assertTrue("Grid reaches the real right screen edge", kotlin.math.abs((viewport.right - shell.right).value) < 1f)
        if (sidebar) {
            val navigation = compose.onNode(hasTestTag("sidebar-collapsed") or hasTestTag("sidebar-expanded"))
                .fetchSemanticsNode().boundsInRoot
            val shellPixels = compose.onNodeWithTag("theme-shell").fetchSemanticsNode().boundsInRoot
            assertEquals("Sidebar has no outer left gap", shellPixels.left, navigation.left, 1f)
        }
        grid.performScrollToIndex(2)
        val recent = compose.onNodeWithTag("recent-row-viewport").getUnclippedBoundsInRoot()
        assertTrue("Recent row keeps the full viewport width", kotlin.math.abs((recent.left - viewport.left).value) < 1f && kotlin.math.abs((recent.right - viewport.right).value) < 1f)
        if (preset != ColorPreset.Amber) {
            grid.performScrollToIndex(if (preset == ColorPreset.Max) 5 else 4)
            val collection = compose.onNodeWithTag("home-collection-row").getUnclippedBoundsInRoot()
            assertTrue("Collection reaches the right screen edge", kotlin.math.abs((collection.right - shell.right).value) < 1f)
            grid.performScrollToIndex(if (preset == ColorPreset.Max) 6 else 5)
            val catalog = compose.onNodeWithTag("home-source-Catálogo").getUnclippedBoundsInRoot()
            assertTrue("Catalog reaches the right screen edge", kotlin.math.abs((catalog.right - shell.right).value) < 1f)
        }
        if (preset == ColorPreset.Max) {
            grid.performScrollToIndex(3)
            val explorer = compose.onNodeWithTag("max-explore-row").getUnclippedBoundsInRoot()
            assertTrue("Explorer reaches the right screen edge", kotlin.math.abs((explorer.right - shell.right).value) < 1f)
        }
    }

    @Test fun comicRowsReachViewportEdges() = rowsReachRealViewportEdges(ColorPreset.Amber)
    @Test fun flixRowsReachViewportEdges() = rowsReachRealViewportEdges(ColorPreset.Flix)
    @Test fun primeRowsReachViewportEdges() = rowsReachRealViewportEdges(ColorPreset.Prime)
    @Test fun maxRowsReachViewportEdges() = rowsReachRealViewportEdges(ColorPreset.Max)
    @Test fun cinemaRowsReachViewportEdges() = rowsReachRealViewportEdges(ColorPreset.Cinema)

    @Test fun flixHasOrderedBoundedRowsAt130Percent() = orderedBoundedRows(ColorPreset.Flix)
    @Test fun primeHasOrderedBoundedRowsAt130Percent() = orderedBoundedRows(ColorPreset.Prime)
    @Test fun maxHasOrderedBoundedRowsAt130Percent() = orderedBoundedRows(ColorPreset.Max)
    @Test fun cinemaHasOrderedBoundedRowsAt130Percent() = orderedBoundedRows(ColorPreset.Cinema)

    private fun firstRowInsideInitialViewport(preset: ColorPreset, percentage: Int, longTitle: Boolean = false) =
        fixture(preset, percentage, withShell = true, longTitle = longTitle) {
            val viewport = compose.onNodeWithTag("library-grid").getUnclippedBoundsInRoot()
            val hero = compose.onNodeWithTag("library-hero").getUnclippedBoundsInRoot()
            val firstRow = compose.onNodeWithTag("recent-row-viewport")
                .assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("Cabeçalho permanece integralmente abaixo do topo", hero.top >= viewport.top)
            assertTrue("Primeira fileira inteira sem seta↓ inicial", firstRow.bottom <= viewport.bottom + 1.dp)
            compose.onNodeWithText("Continuar leitura").assertIsDisplayed().assertIsFocused()
            compose.onAllNodes(isFocused()).assertCountEquals(1)
            if (longTitle) {
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                compose.onNodeWithTag("library-hero-title").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue("Fixture exercises both title lines", layouts.single().lineCount == 2)
                assertTrue("Responsive title stays readable", layouts.single().layoutInput.style.fontSize.value >= 24f)
            }
        }

    @Test fun comicInitialRowFitsAt100Percent() = firstRowInsideInitialViewport(ColorPreset.Amber, 100)
    @Test fun comicInitialRowFitsAt130Percent() = firstRowInsideInitialViewport(ColorPreset.Amber, 130)
    @Test fun flixInitialRowFitsAt100Percent() = firstRowInsideInitialViewport(ColorPreset.Flix, 100)
    @Test fun flixInitialRowFitsAt130Percent() = firstRowInsideInitialViewport(ColorPreset.Flix, 130)
    @Test fun primeInitialRowFitsAt100Percent() = firstRowInsideInitialViewport(ColorPreset.Prime, 100)
    @Test fun primeInitialRowFitsAt130Percent() = firstRowInsideInitialViewport(ColorPreset.Prime, 130)
    @Test fun maxInitialRecentFitsAt100Percent() = firstRowInsideInitialViewport(ColorPreset.Max, 100)
    @Test fun maxInitialRecentFitsAt130Percent() = firstRowInsideInitialViewport(ColorPreset.Max, 130)
    @Test fun flixTwoLineTitleAndFirstRowFitAt100Percent() = firstRowInsideInitialViewport(ColorPreset.Flix, 100, true)
    @Test fun flixTwoLineTitleAndFirstRowFitAt130Percent() = firstRowInsideInitialViewport(ColorPreset.Flix, 130, true)
    @Test fun primeTwoLineTitleAndFirstRowFitAt100Percent() = firstRowInsideInitialViewport(ColorPreset.Prime, 100, true)
    @Test fun primeTwoLineTitleAndFirstRowFitAt130Percent() = firstRowInsideInitialViewport(ColorPreset.Prime, 130, true)
    @Test fun maxTwoLineTitleAndRecentFitAt100Percent() = firstRowInsideInitialViewport(ColorPreset.Max, 100, true)
    @Test fun maxTwoLineTitleAndRecentFitAt130Percent() = firstRowInsideInitialViewport(ColorPreset.Max, 130, true)

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
        compose.onNodeWithTag("library-grid").performScrollToIndex(5)
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
    private fun headerReturnsToTop(preset: ColorPreset, percent: Int = 100) = fixture(preset, percent) {
        val action = compose.onNodeWithText("Continuar leitura").assertIsFocused()
        val title = compose.onNodeWithTag("library-hero-title").assertIsDisplayed()
        val initialTop = title.fetchSemanticsNode().boundsInRoot.top
        val viewportTop = compose.onNodeWithTag("library-grid").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Startup must preserve the title above focused actions", initialTop >= viewportTop + 19f)
        if (preset == ColorPreset.Amber) {
            val poster = compose.onNodeWithTag("library-hero-poster").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val viewport = compose.onNodeWithTag("library-grid").fetchSemanticsNode().boundsInRoot
            assertTrue("Comic cover must start below the top margin", poster.top >= viewport.top + 19f)
            assertTrue("Comic cover must remain fully within the viewport", poster.bottom <= viewport.bottom)
        }
        // Keep focus on the action, reproducing the no-higher-focus-target case from the videos.
        compose.onNodeWithTag("library-grid").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 60f) }
        action.performKeyInput { pressKey(Key.DirectionUp) }
        action.assertIsFocused()
        val returnedTop = title.fetchSemanticsNode().boundsInRoot.top
        assertEquals(initialTop, returnedTop, 2f)
        compose.onAllNodes(isFocused()).assertCountEquals(1)
        action.performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNode(isFocused()).assert(hasAnyAncestor(hasTestTag("library-header")).not())
    }

    @Test fun flixStartupAndUpRevealWholeHeader() = headerReturnsToTop(ColorPreset.Flix)
    @Test fun maxStartupAndUpRevealWholeHeader() = headerReturnsToTop(ColorPreset.Max)
    @Test fun cinemaStartupAndUpRevealWholeHeader() = headerReturnsToTop(ColorPreset.Cinema)
    @Test fun comicStartupAndUpRevealWholePoster() = headerReturnsToTop(ColorPreset.Amber)
    @Test fun cinemaEnlargedTextStillAllowsUpThenDown() = headerReturnsToTop(ColorPreset.Cinema, 130)

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

    private fun sourceMoreReturnsToOrigin(preset: ColorPreset) = fixture(preset) {
        compose.onNodeWithTag("library-grid").performScrollToIndex(when (preset) { ColorPreset.Max -> 6; ColorPreset.Amber -> 28; else -> 5 })
        compose.onNodeWithTag("home-source-Catálogo").performScrollToIndex(12)
        val more = compose.onNode(hasText("Ver mais") and hasAnyAncestor(hasTestTag("home-source-Catálogo")))
        more.performSemanticsAction(SemanticsActions.RequestFocus) { it() }.performClick()
        compose.onNodeWithText("Voltar do teste").performClick()
        more.assertIsDisplayed().assertIsFocused()
        compose.onAllNodes(isFocused()).assertCountEquals(1)
    }

    @Test fun comicKeepsCollectionGridAndAddsSourcePreviewsBeforeFooterWithSidebarSearch() =
        fixture(ColorPreset.Amber, 100, withShell = true) {
            compose.onNodeWithText("Continuar leitura").assertIsFocused()
            compose.onNodeWithTag("nav-search").assertExists()
            compose.onNodeWithTag("home-collection-row").assertDoesNotExist()
            val grid = compose.onNodeWithTag("library-grid")
            grid.performScrollToNode(hasTestTag("library-cover-home-23"))
            compose.onNodeWithTag("library-cover-home-23").assertIsDisplayed()
            grid.performScrollToIndex(28)
            compose.onNodeWithTag("home-source-Catálogo").assertIsDisplayed()
            grid.performScrollToIndex(29)
            compose.onNodeWithTag("home-source-Novidades").assertIsDisplayed()
            grid.performScrollToIndex(30)
            compose.onNodeWithTag("library-footer").assertIsDisplayed()
            compose.onAllNodes(hasText("Buscar") and hasAnyAncestor(hasTestTag("library-footer"))).assertCountEquals(0)
        }

    @Test fun comicSourceMoreRestoresOriginAfterReturningFromSourceSection() = sourceMoreReturnsToOrigin(ColorPreset.Amber)

    @Test fun sourceMoreReturnsToItsOriginWithoutTakingLocalCoverFocus() = sourceMoreReturnsToOrigin(ColorPreset.Prime)
    @Test fun maxSourceMoreRestoresAfterExplorerMovedBelowRecent() = sourceMoreReturnsToOrigin(ColorPreset.Max)

    @Test fun sourcePreviewHighContrastKeepsTextAndSuppressesArtwork() = fixture(ColorPreset.Prime, highContrast = true) {
        compose.onNodeWithTag("library-grid").performScrollToIndex(5)
        compose.onNodeWithTag("home-source-Catálogo").assertIsDisplayed()
        compose.onNodeWithText("Fonte 0").assertIsDisplayed()
        compose.onNodeWithTag("home-source-cover-source-0").assertDoesNotExist()
    }

    @Test fun comicPreviewHasTenCoversInTwoRowsAndMoreReturnsHome() = fixture(ColorPreset.Amber, 100) {
        compose.onNodeWithTag("library-grid").performScrollToIndex(4)
        val covers = (0 until 10).map { compose.onNodeWithTag("home-collection-home-$it").fetchSemanticsNode().boundsInRoot }
        assertEquals(5, covers.take(5).map { it.left }.distinct().size)
        assertTrue(covers.take(5).all { kotlin.math.abs(it.top - covers.first().top) < 2f })
        assertTrue(covers.drop(5).all { it.top > covers.first().top })
        compose.onNodeWithTag("home-collection-home-10").assertDoesNotExist()
        compose.onNodeWithTag("home-collection-more").performClick()
        compose.onNodeWithTag("library-cover-home-0").assertExists()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithTag("home-collection-more").assertIsFocused()
    }
}
