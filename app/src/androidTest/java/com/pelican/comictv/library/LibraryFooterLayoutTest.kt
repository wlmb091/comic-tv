package com.pelican.comictv.library

import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
        collectionData: List<CollectionEntity> = emptyList(), navigateDetails: Boolean = false,
        membershipData: List<CollectionMembership> = emptyList(), shellSearch: Boolean = false,
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
                var currentFocusedId by remember { mutableStateOf(focusedId) }
                var details by remember { mutableStateOf(false) }
                val savedScreens = rememberSaveableStateHolder()
                ComicTvTheme(AppearanceOptions(preset = preset, textPercent = 130, highContrast = true)) {
                    if (shown) Box(Modifier.fillMaxSize()) {
                        if (details) TvButton("Voltar dos detalhes", { details = false })
                        else savedScreens.SaveableStateProvider("library") {
                            key(screenKey.intValue) {
                                CompositionLocalProvider(LocalShellLibrarySearch provides
                                    (if (shellSearch) ({ searchEvents.intValue++ }) else null)) {
                                LibraryScreen(books, repository, currentFocusedId, { currentFocusedId = it },
                                    { if (navigateDetails) details = true }, {},
                                    requestInitialFocus = initial, onInitialFocus = { initial = false },
                                    query = query, onQuery = { query = it }, filter = filter, onFilter = { filter = it },
                                    collections = collectionData.toList(), memberships = membershipData, searchRequest = searchEvents.intValue)
                                }
                            }
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
        compose.onNodeWithText("Continuar leitura").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithText("Buscar").assertDoesNotExist()
        compose.onNodeWithText("Todas").assertDoesNotExist()
        compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("library-footer"))
        compose.onNodeWithTag("library-footer").assertIsDisplayed()
        compose.onNodeWithText("Biblioteca · 24").assertIsDisplayed()
        compose.onNodeWithText("Histórico · 1").assertIsDisplayed()
        compose.onNodeWithText("Todas").assertIsDisplayed()
        if (preset == ColorPreset.Max) {
            compose.onNodeWithTag("library-cover-footer-23").assertDoesNotExist()
            compose.onNodeWithText("Sua coleção · 24").assertDoesNotExist()
        } else {
            val lastCover = compose.onNodeWithTag("library-cover-footer-23").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val footer = compose.onNodeWithTag("library-footer").fetchSemanticsNode().boundsInRoot
            assertTrue("O rodapé deve seguir a última capa da coleção", lastCover.bottom <= footer.top)
        }
        if (preset == ColorPreset.Cinema) compose.onNodeWithText("Buscar").assertDoesNotExist()
        if (preset == ColorPreset.Max) compose.onNodeWithText("Buscar").assertIsDisplayed()
    }

    @Test fun flixKeepsSearchOutOfHomeAndPlacesControlsAfterCollection() = footerFollowsEveryCover(ColorPreset.Flix)
    @Test fun cinemaKeepsControlsAtEndAndSearchInShell() = footerFollowsEveryCover(ColorPreset.Cinema)
    @Test fun maxPlacesSearchAndAllBrowsingControlsAfterCollection() = footerFollowsEveryCover(ColorPreset.Max)
    @Test fun primeStartsAtReadingAndKeepsSearchInShellAndSecondaryControlsAtEnd() = footerFollowsEveryCover(ColorPreset.Prime)

    @Test fun maxSearchShowsEmptyResultsWhenFilteringRemovesEveryMatch() = fixture(ColorPreset.Max) {
        compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("library-footer"))
        compose.onNodeWithText("Buscar").performClick()
        compose.onNodeWithContentDescription("Título do quadrinho").performTextInput("Sem correspondência")
        compose.onNodeWithText("Ver resultados").performClick()
        compose.onNodeWithText("Nenhum quadrinho encontrado").assertIsDisplayed()
        compose.onNodeWithText("Mostrar todas").assertIsDisplayed().assertIsFocused()
    }

    @Test fun collectionManagerReturnsToItsFooterButton() = fixture(ColorPreset.Flix) {
        compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("library-footer"))
        compose.onNodeWithTag("library-footer").performScrollToNode(hasText("Gerenciar coleções"))
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

    @Test fun maxAllLibraryRemainsAvailableFromHomeFooter() = fixture(ColorPreset.Max) {
        compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("library-footer"))
        compose.onNodeWithText("Biblioteca · 24").performClick()
        compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("library-cover-footer-23"))
        compose.onNodeWithTag("library-cover-footer-23").assertIsDisplayed()
    }

    @Test fun maxHistoryUsesLargePortraitCoversAndKeepsFocusAt130Percent() = fixture(ColorPreset.Max) {
        compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("library-footer"))
        compose.onNodeWithText("Histórico · 1").performClick()
        compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("library-cover-footer-0"))
        val card = compose.onNodeWithTag("library-cover-footer-0")
        card.performSemanticsAction(SemanticsActions.RequestFocus) { it() }.assertIsFocused()
        val bounds = card.fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithTag("library-title-footer-0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Histórico Max mantém capa de coleção vertical grande", bounds.height > bounds.width * 1.4f)
        assertTrue("Título fica dentro da capa", title.right <= bounds.right && title.bottom <= bounds.bottom)
        compose.onAllNodes(isFocused()).assertCountEquals(1)
    }

    @Test fun maxHeroKeepsReadingActionsAtItsBottomAt130Percent() = fixture(ColorPreset.Max) {
        val hero = compose.onNodeWithTag("library-hero").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithTag("library-hero-title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val read = compose.onNodeWithText("Continuar leitura").assertIsDisplayed().assertIsFocused()
            .fetchSemanticsNode().boundsInRoot
        assertTrue("AçõesMax ficam na base do destaque, próximas da próxima faixa", read.center.y > hero.center.y && read.bottom <= hero.bottom)
        assertTrue("Título precede leitura sem sobrepor a ação", title.bottom <= read.top)
    }

    private fun collectionCoverKeepsPortraitGeometry(preset: ColorPreset) = fixture(preset, "footer-23") {
        if (preset == ColorPreset.Max) {
            compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("library-footer"))
            compose.onNodeWithText("Biblioteca · 24").performClick()
            compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("library-cover-footer-23"))
            compose.onNodeWithTag("library-cover-footer-23").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        }
        val bounds = compose.onNodeWithTag("library-cover-footer-23").assertIsDisplayed().assertIsFocused()
            .fetchSemanticsNode().boundsInRoot
        assertTrue("A coleção mantém capas verticais", bounds.height > bounds.width)
    }

    @Test fun comicCollectionUsesSquareFramesAndRestoresFocusAt130Percent() = fixture(ColorPreset.Amber, "footer-23") {
        val card = compose.onNodeWithTag("library-cover-footer-23").assertIsDisplayed().assertIsFocused()
        val bounds = card.fetchSemanticsNode().boundsInRoot
        assertTrue("Comic usa quadro quadrado sem deformar a capa", kotlin.math.abs(bounds.width - bounds.height) < 2f)
        compose.onAllNodes(isFocused()).assertCountEquals(1)
    }
    @Test fun flixCollectionKeepsPortraitCards() = collectionCoverKeepsPortraitGeometry(ColorPreset.Flix)
    @Test fun maxCollectionKeepsLargerPortraitCards() = collectionCoverKeepsPortraitGeometry(ColorPreset.Max)

    @Test fun primeCollectionUsesLandscapeFramesAndRestoresItsEditionAt130Percent() = fixture(ColorPreset.Prime, "footer-23") {
        val card = compose.onNodeWithTag("library-cover-footer-23").assertIsDisplayed().assertIsFocused()
        val bounds = card.fetchSemanticsNode().boundsInRoot
        assertTrue("Coleção Prime usa miniaturas em quadro amplo", bounds.width > bounds.height * 1.6f)
        compose.onAllNodes(isFocused()).assertCountEquals(1)
    }

    @Test fun primeRecentUsesBroadArtworkWithoutTakingReadingFocusAt130Percent() = fixture(ColorPreset.Prime) {
        compose.onNodeWithText("Continuar leitura").assertIsFocused()
        compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("library-recent-footer-0"))
        val card = compose.onNodeWithTag("library-recent-footer-0")
        val bounds = card.fetchSemanticsNode().boundsInRoot
        assertTrue("Recentes Prime usa quadro amplo", bounds.width > bounds.height)
        card.performSemanticsAction(SemanticsActions.RequestFocus) { it() }.assertIsFocused()
        compose.onAllNodes(isFocused()).assertCountEquals(1)
    }
    @Test fun flixRecentShowsBroadArtworkFrameAt130Percent() = fixture(ColorPreset.Flix) {
        compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("library-recent-footer-0"))
        val card = compose.onNodeWithTag("library-recent-footer-0")
        val bounds = card.fetchSemanticsNode().boundsInRoot
        assertTrue("Recentes Flix mantém quadro amplo", bounds.width > bounds.height)
        card.performSemanticsAction(SemanticsActions.RequestFocus) { it() }.assertIsFocused()
        compose.onAllNodes(isFocused()).assertCountEquals(1)
    }

    @Test fun cinemaGlassRecentKeepsItsTitleInsideAndOneFocusAt130Percent() = fixture(ColorPreset.Cinema) {
        compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("library-recent-footer-0"))
        val card = compose.onNodeWithTag("library-recent-footer-0")
        card.performSemanticsAction(SemanticsActions.RequestFocus) { it() }.assertIsFocused()
        val bounds = card.fetchSemanticsNode().boundsInRoot
        assertTrue("Vidro Cinema mantém faixa horizontal legível", bounds.width > bounds.height)
        compose.onAllNodes(isFocused()).assertCountEquals(1)
    }

    @Test fun cinemaCollectionKeepsPortraitCards() = collectionCoverKeepsPortraitGeometry(ColorPreset.Cinema)

    @Test fun primeShellSearchReturnsToReadingWhenThereIsNoVisibleEditionOrigin() {
        val searchEvents = mutableIntStateOf(0)
        fixture(ColorPreset.Prime, searchEvents = searchEvents) {
            compose.onNodeWithTag("library-top-search").assertDoesNotExist()
            compose.onNodeWithText("Continuar leitura").assertIsDisplayed().assertIsFocused()
            compose.runOnIdle { searchEvents.intValue++ }
            compose.onNodeWithContentDescription("Título do quadrinho").assertIsDisplayed()
            compose.onNodeWithContentDescription("Título do quadrinho").performTextInput("Sem correspondência")
            compose.onNodeWithText("Ver resultados").performClick()
            compose.onNodeWithText("Continuar leitura").assertIsDisplayed().assertIsFocused()
            compose.onNodeWithTag("library-top-search").assertDoesNotExist()
            compose.onAllNodes(isFocused()).assertCountEquals(1)
        }
    }

    @Test fun maxExplorerCollectionSearchRestoresFocusedCircleAndHorizontalOffset() {
        val searchEvents = mutableIntStateOf(0)
        val collections = (0 until 16).map { CollectionEntity("explore-$it", "Coleção $it", "colecao $it") }
        fixture(ColorPreset.Max, searchEvents = searchEvents, collectionData = collections) {
            compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("max-explore"))
            compose.onNodeWithTag("max-explore-row").performScrollToNode(hasTestTag("max-explore-collection-explore-12"))
            val circle = compose.onNodeWithTag("max-explore-collection-explore-12")
            circle.performSemanticsAction(SemanticsActions.RequestFocus) { it() }.assertIsFocused()
            val left = circle.fetchSemanticsNode().boundsInRoot.left
            circle.performKeyInput { pressKey(Key.DirectionCenter) }
            compose.onNodeWithTag("max-collection-page").assertExists()
            compose.onNodeWithTag("max-collection-empty").assertIsDisplayed()
            compose.runOnIdle { searchEvents.intValue++ }
            compose.onNodeWithContentDescription("Título do quadrinho").assertIsDisplayed()
            compose.onNodeWithText("Ver resultados").performClick()
            compose.onNodeWithTag("max-collection-page").assertExists()
            compose.onNodeWithText("Voltar").assertIsFocused().performClick()
            circle.assertIsDisplayed().assertIsFocused()
            assertTrue("Busca restaura o deslocamento da faixa circular", kotlin.math.abs(left - circle.fetchSemanticsNode().boundsInRoot.left) < 2f)
            compose.onAllNodes(isFocused()).assertCountEquals(1)
        }
    }

    @Test fun maxCollectionDetailsReturnsToPageThenItsHomeCircle() =
        fixture(ColorPreset.Max, collectionData = listOf(CollectionEntity("owned", "Minhas edições", "minhas edicoes")),
            navigateDetails = true, membershipData = books.map { CollectionMembership(it.id, "owned") }) {
            compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("max-explore"))
            val circle = compose.onNodeWithTag("max-explore-collection-owned")
            circle.performSemanticsAction(SemanticsActions.RequestFocus) { it() }.performClick()
            compose.onNodeWithTag("max-collection-page").performScrollToNode(hasTestTag("max-collection-cover-footer-20"))
            val book = compose.onNodeWithTag("max-collection-cover-footer-20")
            book.performSemanticsAction(SemanticsActions.RequestFocus) { it() }.performClick()
            compose.onNodeWithText("Voltar dos detalhes").performClick()
            book.assertIsDisplayed().assertIsFocused()
            compose.onNodeWithTag("max-collection-page").performScrollToNode(hasText("Voltar"))
            compose.onNodeWithText("Voltar").performClick()
            circle.assertIsDisplayed().assertIsFocused()
            compose.onNodeWithTag("library-cover-footer-20").assertDoesNotExist()
        }

    @Test fun deletingAnOpenCollectionReturnsHomeWithoutRemovingBooks() {
        val collections = mutableStateListOf(CollectionEntity("removed", "Removida", "removida"))
        fixture(ColorPreset.Max, collectionData = collections) {
            compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("max-explore"))
            compose.onNodeWithTag("max-explore-collection-removed").performClick()
            compose.onNodeWithTag("max-collection-empty").assertIsDisplayed()
            compose.runOnIdle { collections.clear() }
            compose.onNodeWithTag("max-collection-page").assertDoesNotExist()
            compose.onNodeWithTag("library-grid").assertExists()
            compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("library-footer"))
            compose.onNodeWithText("Biblioteca · 24").performClick()
            compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("library-cover-footer-23"))
            compose.onNodeWithTag("library-cover-footer-23").assertIsDisplayed()
        }
    }

    @Test fun maxHomeSearchResultsRevealMatchingLibraryCover() {
        val searchEvents = mutableIntStateOf(0)
        fixture(ColorPreset.Max, searchEvents = searchEvents) {
            compose.onNodeWithTag("library-cover-footer-23").assertDoesNotExist()
            compose.runOnIdle { searchEvents.intValue++ }
            compose.onNodeWithContentDescription("Título do quadrinho").performTextInput("Edição 23")
            compose.onNodeWithText("Ver resultados").performClick()
            compose.onNodeWithTag("library-cover-footer-23").assertIsDisplayed().assertIsFocused()
            compose.onNodeWithTag("library-cover-footer-22").assertDoesNotExist()
        }
    }

    @Test fun maxShellSearchWithoutMatchesFocusesVisibleClearAction() {
        val searchEvents = mutableIntStateOf(0)
        fixture(ColorPreset.Max, searchEvents = searchEvents, shellSearch = true) {
            compose.runOnIdle { searchEvents.intValue++ }
            compose.onNodeWithContentDescription("Título do quadrinho").performTextInput("Nenhum título existe")
            compose.onNodeWithText("Ver resultados").performClick()
            compose.onNodeWithText("Nenhum quadrinho encontrado").assertIsDisplayed()
            compose.onNodeWithText("Mostrar todas").assertIsDisplayed().assertIsFocused()
            compose.onNodeWithText("Mostrar todas").performClick()
            compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("library-cover-footer-23"))
            compose.onNodeWithTag("library-cover-footer-23").assertIsDisplayed()
        }
    }

    @Test fun maxShellCollectionSearchWithoutMatchesStaysOnPageAndReturnsHome() {
        val searchEvents = mutableIntStateOf(0)
        fixture(ColorPreset.Max, searchEvents = searchEvents, shellSearch = true,
            collectionData = listOf(CollectionEntity("shell-empty", "Coleção vazia", "colecao vazia"))) {
            compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("max-explore"))
            val circle = compose.onNodeWithTag("max-explore-collection-shell-empty")
            circle.performSemanticsAction(SemanticsActions.RequestFocus) { it() }.performClick()
            compose.runOnIdle { searchEvents.intValue++ }
            compose.onNodeWithContentDescription("Título do quadrinho").performTextInput("Nenhum título existe")
            compose.onNodeWithText("Ver resultados").performClick()
            compose.onNodeWithTag("max-collection-empty").assertIsDisplayed()
            compose.onNodeWithText("Voltar").assertIsFocused().performClick()
            circle.assertIsDisplayed().assertIsFocused()
            compose.onNodeWithTag("library-cover-footer-0").assertDoesNotExist()
        }
    }

    @Test fun maxEmptySearchDoesNotRevealHomeGrid() {
        val searchEvents = mutableIntStateOf(0)
        fixture(ColorPreset.Max, searchEvents = searchEvents) {
            compose.runOnIdle { searchEvents.intValue++ }
            compose.onNodeWithText("Ver resultados").performClick()
            compose.onNodeWithTag("library-cover-footer-0").assertDoesNotExist()
        }
    }

    @Test fun maxCollectionHoldKeepsOptionsAndRestoresItsCard() =
        fixture(ColorPreset.Max, collectionData = listOf(CollectionEntity("hold", "Menu de edição", "menu de edicao")),
            navigateDetails = true, membershipData = books.map { CollectionMembership(it.id, "hold") }) {
            compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("max-explore"))
            compose.onNodeWithTag("max-explore-collection-hold")
                .performSemanticsAction(SemanticsActions.RequestFocus) { it() }.performClick()
            val book = compose.onNodeWithTag("max-collection-cover-footer-0")
            book.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            book.performKeyInput { keyDown(Key.DirectionCenter) }
            // The shared hold controller intentionally uses Android monotonic real time.
            android.os.SystemClock.sleep(1100)
            compose.mainClock.advanceTimeBy(1100)
            compose.onNodeWithText("Abrir detalhes").assertIsDisplayed()
            compose.onNodeWithText("Voltar dos detalhes").assertDoesNotExist()
            compose.onNodeWithText("Fechar").performClick()
            book.assertIsDisplayed().assertIsFocused()
            book.performKeyInput { keyUp(Key.DirectionCenter); pressKey(Key.DirectionCenter) }
            compose.onNodeWithText("Voltar dos detalhes").assertIsDisplayed().performClick()
            book.assertIsDisplayed().assertIsFocused()
        }

    @Test fun maxHeroSecondaryFocusDoesNotKeepAnOldExplorerSearchOrigin() {
        val searchEvents = mutableIntStateOf(0)
        fixture(ColorPreset.Max, searchEvents = searchEvents) {
            compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("max-explore"))
            compose.onNodeWithTag("max-explore-comic-footer-0")
                .performSemanticsAction(SemanticsActions.RequestFocus) { it() }.assertIsFocused()
            compose.onNodeWithTag("library-grid").performScrollToNode(hasText("Ver detalhes"))
            compose.onNodeWithText("Ver detalhes")
                .performSemanticsAction(SemanticsActions.RequestFocus) { it() }.assertIsFocused()
            compose.runOnIdle { searchEvents.intValue++ }
            compose.onNodeWithText("Ver resultados").performClick()
            compose.onNodeWithText("Continuar leitura").assertIsDisplayed().assertIsFocused()
            compose.onAllNodes(isFocused()).assertCountEquals(1)
        }
    }

    @Test fun maxExplorerBookReturnsFromDetailsToItsSavedCircleInsteadOfTheDuplicateGridCard() =
        fixture(ColorPreset.Max, navigateDetails = true) {
            compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("max-explore"))
            compose.onNodeWithTag("max-explore-row").performScrollToNode(hasTestTag("max-explore-comic-footer-10"))
            val circle = compose.onNodeWithTag("max-explore-comic-footer-10")
            circle.performSemanticsAction(SemanticsActions.RequestFocus) { it() }.assertIsFocused()
            val left = circle.fetchSemanticsNode().boundsInRoot.left
            circle.performKeyInput { pressKey(Key.DirectionCenter) }
            compose.onNodeWithText("Voltar dos detalhes").assertIsDisplayed().performClick()
            circle.assertIsDisplayed().assertIsFocused()
            assertTrue("Detalhes restaura o deslocamento da faixa circular", kotlin.math.abs(left - circle.fetchSemanticsNode().boundsInRoot.left) < 2f)
            compose.onNodeWithTag("library-cover-footer-10").assertDoesNotExist()
            compose.onAllNodes(isFocused()).assertCountEquals(1)
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
