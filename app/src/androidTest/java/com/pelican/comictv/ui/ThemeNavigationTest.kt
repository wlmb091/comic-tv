package com.pelican.comictv.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

@OptIn(ExperimentalTestApi::class)
class ThemeNavigationTest {
    @get:Rule val compose = createComposeRule()

    private fun assertBrowserAndPhoneDestinations(preset:ColorPreset,horizontal:Boolean=false) {
        var destination=""
        compose.setContent { ComicTvTheme(AppearanceOptions(preset=preset,textPercent=130,highContrast=true)) {
            TvShell("companion",{destination=it},focusSectionOnEntry=true,onSearch={}) {
                TvButton("Conteúdo",{},preferredContentEntryModifier())
            }
        } }
        compose.onNodeWithTag("nav-companion").assertIsFocused().assertIsDisplayed()
            .performKeyInput { pressKey(if(horizontal)Key.DirectionLeft else Key.DirectionUp) }
        compose.onNodeWithTag("nav-browser").assertIsFocused().assertIsDisplayed().performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals("browser",destination) }
    }
    @Test fun comicHasSeparateBrowserAndPhoneAt130Percent()=assertBrowserAndPhoneDestinations(ColorPreset.Amber)
    @Test fun flixHasSeparateBrowserAndPhoneAt130Percent()=assertBrowserAndPhoneDestinations(ColorPreset.Flix)
    @Test fun primeHasSeparateBrowserAndPhoneAt130Percent()=assertBrowserAndPhoneDestinations(ColorPreset.Prime,true)
    @Test fun maxHasSeparateBrowserAndPhoneAt130Percent()=assertBrowserAndPhoneDestinations(ColorPreset.Max,true)
    @Test fun cinemaHasSeparateBrowserAndPhoneAt130Percent()=assertBrowserAndPhoneDestinations(ColorPreset.Cinema)

    private fun setShell(preset: ColorPreset, selected: String = "library", highContrast: Boolean = true) {
        compose.setContent { ComicTvTheme(AppearanceOptions(preset = preset, textPercent = 130, highContrast = highContrast)) {
            var pending by remember { mutableStateOf(true) }
            val startupFocus = remember { FocusRequester() }
            // Library startup owns hero focus; focusContentOnEntry means an explicit section change.
            LaunchedEffect(Unit) {
                if (preset == ColorPreset.Flix) { withFrameNanos { }; startupFocus.requestFocus(); pending = false }
            }
            TvShell(selected, {}, focusContentOnEntry = pending && preset != ColorPreset.Flix,
                onSectionFocused = { pending = false },
                backdrop = { Box(Modifier.fillMaxSize().testTag("test-shell-backdrop")) }) {
                TvButton("Primeiro controle", {}, Modifier.focusRequester(startupFocus).then(preferredContentEntryModifier()))
            }
        } }
    }

    @Test fun cinemaReservesMenuSpaceOutsideTheImmersiveLibrary() {
        setShell(ColorPreset.Cinema, "settings")
        val menu = compose.onNodeWithTag("cinema-menu").fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithText("Primeiro controle").fetchSemanticsNode().boundsInRoot
        assertTrue("Menu não deve cobrir controles/título das outras seções", content.top >= menu.bottom)
        val shell = compose.onNodeWithTag("theme-shell").fetchSemanticsNode().boundsInRoot
        assertTrue("Menu do Cinema fica no canto superior direito", menu.center.x > shell.center.x)
        compose.onNodeWithText("COMIC TV").assertDoesNotExist()
    }

    @Test fun cinemaMenuBackPreservesUnderlyingNestedPage() {
        var nestedBack = false
        compose.setContent { ComicTvTheme(AppearanceOptions(preset = ColorPreset.Cinema)) {
            var pending by remember { mutableStateOf(true) }
            TvShell("sources", {}, focusContentOnEntry = pending, onSectionFocused = { pending = false }) {
                androidx.activity.compose.BackHandler { nestedBack = true }
                TvButton("Página interna", {}, preferredContentEntryModifier())
            }
        } }
        compose.onNodeWithText("Página interna").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("sidebar-expanded").assertExists()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithTag("sidebar-expanded").assertDoesNotExist()
        assertTrue("Voltar do menu preserva a página interna", !nestedBack)
    }

    private fun assertWordmarkFits() {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("COMIC TV").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Marca inteira com texto em130%", layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
    }

    @Test fun primeMovesUpToCurrentSectionAndDownToContent() {
        setShell(ColorPreset.Prime)
        assertWordmarkFits()
        val primeBrand = compose.onNodeWithTag("prime-wordmark").fetchSemanticsNode().boundsInRoot
        val primeMenu = compose.onNodeWithTag("navigation-top").fetchSemanticsNode().boundsInRoot
        assertTrue("Marca do Prime fica à direita da navegação", primeBrand.center.x > primeMenu.center.x)
        compose.onNodeWithTag("navigation-top").assertExists()
        compose.onNodeWithTag("sidebar-collapsed").assertDoesNotExist()
        compose.onNodeWithText("Primeiro controle").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("nav-library").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Primeiro controle").assertIsFocused()
    }

    @Test fun maxMovesDownToDockAndUpToContent() {
        setShell(ColorPreset.Max)
        compose.onNodeWithTag("navigation-bottom").assertExists()
        compose.onNodeWithTag("sidebar-collapsed").assertDoesNotExist()
        compose.onNodeWithText("Primeiro controle").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("nav-library").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Primeiro controle").assertIsFocused()
    }

    @Test fun maxSearchCategoryIsAvailableFromEveryMainSection() {
        var route by mutableStateOf("library")
        var searches = 0
        compose.setContent { ComicTvTheme(AppearanceOptions(preset = ColorPreset.Max, textPercent = 130)) {
            CompositionLocalProvider(LocalShellLibrarySearch provides { searches++ }) {
                key(route) {
                    TvShell(route, {}) { TvButton("Conteúdo", {}, preferredContentEntryModifier()) }
                }
            }
        } }
        listOf("library", "recent", "sources", "extensions", "import", "settings").forEachIndexed { index, section ->
            compose.runOnIdle { route = section }
            compose.onNodeWithText("Conteúdo").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                .performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithTag("nav-search").assertIsDisplayed().assertIsNotSelected().performClick()
            compose.onNodeWithTag("nav-$section").assertIsSelected()
            compose.runOnIdle { assertTrue(searches == index + 1) }
        }
    }

    @Test fun primeBackdropCoversViewportAndNavigationKeepsBrandApartAt130Percent() {
        setShell(ColorPreset.Prime, highContrast = false)
        val shell = compose.onNodeWithTag("theme-shell").fetchSemanticsNode().boundsInRoot
        val backdrop = compose.onNodeWithTag("test-shell-backdrop").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("prime-navigation-viewport").fetchSemanticsNode().boundsInRoot
        val brand = compose.onNodeWithTag("prime-wordmark").fetchSemanticsNode().boundsInRoot
        assertTrue("Arte Prime alcança as quatro bordas da tela", backdrop == shell)
        compose.onNodeWithTag("prime-navigation-panel").assertDoesNotExist()
        assertTrue("Categorias não invadem a marca", viewport.right < brand.left)
        assertTrue("Marca permanece dentro da tela", brand.right <= shell.right)
        assertWordmarkFits()
    }

    @Test fun primeSearchCategoryUsesGlobalCallbackFromEveryMainSectionAt130Percent() {
        var route by mutableStateOf("library")
        var searches = 0
        var navigations = 0
        compose.setContent { ComicTvTheme(AppearanceOptions(preset = ColorPreset.Prime, textPercent = 130)) {
            CompositionLocalProvider(LocalShellLibrarySearch provides { searches++ }) {
                key(route) {
                    TvShell(route, { navigations++ }) { TvButton("Conteúdo", {}, preferredContentEntryModifier()) }
                }
            }
        } }
        listOf("library", "recent", "sources", "extensions", "import", "settings").forEachIndexed { index, section ->
            compose.runOnIdle { route = section }
            compose.onNodeWithTag("nav-search").performScrollTo().assertIsDisplayed().assertIsNotSelected().performClick()
            compose.onNodeWithTag("nav-$section").assertIsSelected()
            compose.runOnIdle { assertTrue(searches == index + 1 && navigations == 0) }
        }
    }

    @Test fun primeLabelsScrollWithoutClippingAtLargeAndroidAndAppFontScales() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.4f)) {
                ComicTvTheme(AppearanceOptions(preset = ColorPreset.Prime, textPercent = 130)) {
                    TvShell("library", {}, onSearch = {}) { TvButton("Conteúdo", {}, preferredContentEntryModifier()) }
                }
            }
        }
        listOf("Buscar", "Biblioteca", "Novidades", "Catálogo", "Extensões", "Importar", "Configurações").forEach { label ->
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Rótulo inteiro: $label", layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
        }
        assertWordmarkFits()
    }

    @Test fun primeHighContrastRetainsOpaqueNavigationSurface() {
        setShell(ColorPreset.Prime)
        compose.onNodeWithTag("prime-navigation-panel").assertExists()
        compose.onNodeWithTag("navigation-top").assertExists()
    }

    @Test fun maxPermanentDockReservesContentHeightAt130Percent() {
        var density = 0f
        var scale = 0f
        compose.setContent { ComicTvTheme(AppearanceOptions(preset = ColorPreset.Max, textPercent = 130)) {
            density = LocalDensity.current.density
            scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            TvShell("library", {}, onSearch = {}) { TvButton("Conteúdo", {}, preferredContentEntryModifier()) }
        } }
        compose.onNodeWithTag("navigation-bottom").assertExists()
        compose.onNodeWithText("Conteúdo").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performKeyInput { pressKey(Key.DirectionDown) }
        val dock = compose.onNodeWithTag("navigation-bottom").fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag("content-entry").fetchSemanticsNode().boundsInRoot
        val shell = compose.onNodeWithTag("theme-shell").fetchSemanticsNode().boundsInRoot
        assertTrue("Dock respects horizontal safe area", dock.left >= shell.left + 47f * density && dock.right <= shell.right - 47f * density)
        assertTrue("Dock respects bottom safe area", dock.bottom <= shell.bottom - 26f * density)
        assertTrue("Dock não cobre o conteúdo", content.bottom <= dock.top)
        assertTrue("Dock usa altura compacta mesmo a130%", dock.height <= 74f * scale * density + 1f)
        listOf("search", "library", "recent", "sources", "extensions", "import", "settings").forEach { section ->
            val item = compose.onNodeWithTag("nav-$section").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("Alvo do dock conserva48dp: $section", item.height >= 48f * density && item.width >= 48f * density)
        }
        listOf("Buscar", "Biblioteca", "Novidades", "Catálogo", "Extensões", "Importar", "Configurações").forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Rótulo do dock inteiro a130%: $label", layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
            assertTrue("Dock labels keep minimum14sp: $label", layouts.all { it.layoutInput.style.fontSize.value >= 14f })
        }
    }

    @Test fun cinemaRightOpensMenuOnRightAndLeftDismissesIt() {
        setShell(ColorPreset.Cinema)
        compose.onNodeWithText("COMIC TV").assertDoesNotExist()
        compose.onNodeWithTag("sidebar-collapsed").assertDoesNotExist()
        compose.onNodeWithTag("sidebar-expanded").assertDoesNotExist()
        compose.onNodeWithText("Primeiro controle").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("sidebar-expanded").assertExists()
        val menu = compose.onNodeWithTag("sidebar-expanded").fetchSemanticsNode().boundsInRoot
        val shell = compose.onNodeWithTag("theme-shell").fetchSemanticsNode().boundsInRoot
        assertTrue("Menu expandido segue o botão à direita", menu.center.x > shell.center.x)
        compose.onNodeWithText("COMIC TV").assertIsDisplayed()
        assertWordmarkFits()
        val brand = compose.onNodeWithText("COMIC TV").fetchSemanticsNode().boundsInRoot
        val library = compose.onNodeWithTag("nav-library").fetchSemanticsNode().boundsInRoot
        assertTrue("Marca reaparece apenas no topo do menu expandido", brand.bottom <= library.top)
        compose.onNodeWithTag("nav-library").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithText("Primeiro controle").assertIsFocused()
        compose.onNodeWithTag("sidebar-expanded").assertDoesNotExist()
        compose.onNodeWithText("COMIC TV").assertDoesNotExist()
        compose.onAllNodes(isFocused()).assertCountEquals(1)
    }

    @Test fun flixIconsOverlayFullShellBackdropAndExpandedMenuDimsContent() {
        setShell(ColorPreset.Flix, highContrast = false)
        val shell = compose.onNodeWithTag("theme-shell").fetchSemanticsNode().boundsInRoot
        val backdrop = compose.onNodeWithTag("test-shell-backdrop").fetchSemanticsNode().boundsInRoot
        assertTrue("Arte deve alcançar a área dos ícones e as bordas da tela", backdrop == shell)
        compose.onNodeWithTag("sidebar-expanded").assertExists()
        compose.onNodeWithTag("sidebar-panel").assertDoesNotExist()
        compose.onNodeWithTag("nav-library").assertIsSelected()
        compose.onNodeWithTag("flix-navigation-scrim").assertExists()
        compose.onNodeWithText("Primeiro controle").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("sidebar-expanded").assertExists()
        compose.onNodeWithTag("sidebar-panel").assertDoesNotExist()
        val scrim = compose.onNodeWithTag("flix-navigation-scrim").fetchSemanticsNode().boundsInRoot
        assertTrue("Escurecimento cobre o conteúdo completo", scrim == shell)
        compose.onNodeWithTag("nav-library").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Primeiro controle").assertIsFocused()
        compose.onNodeWithTag("sidebar-panel").assertDoesNotExist()
        compose.onNodeWithTag("flix-navigation-scrim").assertDoesNotExist()
    }

    @Test fun cinemaMenuDownReturnsDirectlyToContentWithoutOpeningMenu() {
        setShell(ColorPreset.Cinema)
        compose.onNodeWithTag("cinema-menu").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("cinema-menu").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Primeiro controle").assertIsFocused()
        compose.onNodeWithTag("sidebar-expanded").assertDoesNotExist()
        compose.onAllNodes(isFocused()).assertCountEquals(1)
    }

    @Test fun cinemaSearchInvokesExistingLibraryCallbackWithoutChangingRoute() {
        var searches = 0
        var navigations = 0
        compose.setContent { ComicTvTheme(AppearanceOptions(preset = ColorPreset.Cinema)) {
            var pending by remember { mutableStateOf(true) }
            TvShell("library", { navigations++ }, focusContentOnEntry = pending,
                onSectionFocused = { pending = false }, onSearch = { searches++ }) {
                TvButton("Primeiro controle", {}, preferredContentEntryModifier())
            }
        } }
        compose.onNodeWithText("Primeiro controle").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("nav-library").assertIsSelected().assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("nav-library").assertIsSelected().assertIsNotFocused()
        compose.onNodeWithTag("nav-search").assertIsNotSelected().assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertTrue(searches == 1 && navigations == 0) }
        compose.onNodeWithTag("sidebar-expanded").assertDoesNotExist()
        compose.onNodeWithText("Primeiro controle").assertIsFocused()
        compose.onAllNodes(isFocused()).assertCountEquals(1)
    }

    @Test fun maxBackdropExtendsBehindBottomDock() {
        setShell(ColorPreset.Max, highContrast = false)
        val shell = compose.onNodeWithTag("theme-shell").fetchSemanticsNode().boundsInRoot
        val backdrop = compose.onNodeWithTag("test-shell-backdrop").fetchSemanticsNode().boundsInRoot
        assertTrue("Arte Max alcança toda a tela atrás do dock", backdrop == shell)
        compose.onNodeWithTag("navigation-bottom").assertExists()
    }

    @Test fun maxDownMovesThroughContentThenReachesDockAndUpRestoresLastControl() {
        compose.setContent { ComicTvTheme(AppearanceOptions(preset = ColorPreset.Max)) {
            var pending by remember { mutableStateOf(true) }
            TvShell("library", {}, focusContentOnEntry = pending, onSectionFocused = { pending = false }) {
                Column {
                    TvButton("Primeira fileira", {}, preferredContentEntryModifier())
                    TvButton("Última fileira", {})
                }
            }
        } }
        compose.onNodeWithText("Primeira fileira").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Última fileira").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("nav-library").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Última fileira").assertIsFocused()
        compose.onNodeWithTag("navigation-bottom").assertExists()
        compose.onAllNodes(isFocused()).assertCountEquals(1)
    }

    @Test fun flixHighContrastRetainsOpaqueSidebar() {
        setShell(ColorPreset.Flix)
        compose.onNodeWithTag("sidebar-expanded").assertExists()
        compose.onNodeWithTag("sidebar-panel").assertExists()
    }

    @Test fun flixDefaultOpenBackClosesBeforeUnderlyingPageAndPreservesHeroFocus() {
        var pageBack = false
        compose.setContent { ComicTvTheme(AppearanceOptions(preset = ColorPreset.Flix)) {
            val startupFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { withFrameNanos { }; startupFocus.requestFocus() }
            TvShell("library", {}) {
                androidx.activity.compose.BackHandler { pageBack = true }
                TvButton("Destaque inicial", {}, Modifier.focusRequester(startupFocus).then(preferredContentEntryModifier()))
            }
        } }
        compose.onNodeWithTag("sidebar-expanded").assertExists()
        compose.onNodeWithText("Destaque inicial").assertIsFocused()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithTag("sidebar-collapsed").assertExists()
        compose.onNodeWithText("Destaque inicial").assertIsFocused()
        compose.runOnIdle { assertTrue(!pageBack) }
        compose.onAllNodes(isFocused()).assertCountEquals(1)
    }

    @Test fun sidebarExpansionKeepsContentBoundsAndDismissalRestoresLowerCardAndScroll() {
        var scroll: androidx.compose.foundation.ScrollState? = null
        compose.setContent { ComicTvTheme(AppearanceOptions(preset = ColorPreset.Amber, textPercent = 130)) {
            var pending by remember { mutableStateOf(true) }
            TvShell("library", {}, focusContentOnEntry = pending, onSectionFocused = { pending = false }) {
                val state = rememberScrollState()
                scroll = state
                Column(Modifier.fillMaxSize().verticalScroll(state)) {
                    TvButton("Topo", {}, preferredContentEntryModifier())
                    Spacer(Modifier.height(600.dp))
                    TvButton("Cartão inferior", {})
                    Spacer(Modifier.height(150.dp))
                }
            }
        } }
        compose.onNodeWithTag("sidebar-collapsed").assertExists()
        compose.onNodeWithText("Cartão inferior").performScrollTo()
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("content-entry").fetchSemanticsNode().boundsInRoot
        var offset = 0
        compose.runOnIdle { offset = scroll!!.value; assertTrue(offset > 0) }
        compose.onNodeWithText("Cartão inferior").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("sidebar-expanded").assertExists()
        assertTrue(bounds == compose.onNodeWithTag("content-entry").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("nav-library").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Cartão inferior").assertIsFocused()
        compose.runOnIdle { assertTrue(scroll!!.value == offset) }
        compose.onAllNodes(isFocused()).assertCountEquals(1)
    }

    @Test fun confirmingAnotherSidebarSectionRetractsAndFocusesDestinationEntry() {
        compose.setContent { ComicTvTheme(AppearanceOptions(preset = ColorPreset.Flix)) {
            var route by remember { mutableStateOf("library") }
            var pendingDestination by remember { mutableStateOf<String?>(null) }
            // NavHost destinations have separate shell instances, rather than one shell changing selected.
            key(route) {
                val startupFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    if (route == "library") { withFrameNanos { }; startupFocus.requestFocus() }
                }
                TvShell(route, { pendingDestination = it; route = it },
                    focusContentOnEntry = pendingDestination == route,
                    onSectionFocused = { pendingDestination = null }) {
                    TvButton("Conteúdo $route", {}, Modifier.focusRequester(startupFocus).then(preferredContentEntryModifier()))
                }
            }
        } }
        compose.onNodeWithTag("sidebar-expanded").assertExists()
        compose.onNodeWithText("Conteúdo library").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("nav-recent").performClick()
        compose.onNodeWithTag("sidebar-collapsed").assertExists()
        compose.onNodeWithText("Conteúdo recent").assertIsFocused()
        compose.onAllNodes(isFocused()).assertCountEquals(1)
    }
}
