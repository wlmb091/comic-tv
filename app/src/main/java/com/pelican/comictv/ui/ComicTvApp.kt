package com.pelican.comictv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.res.painterResource
import com.pelican.comictv.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.*
import androidx.navigation.NavType
import androidx.navigation.navArgument
import java.io.File
import androidx.tv.material3.Text
import com.pelican.comictv.AppContainer
import com.pelican.comictv.BuildConfig
import com.pelican.comictv.sources.*
import com.pelican.comictv.details.DetailsScreen
import com.pelican.comictv.library.*
import com.pelican.comictv.reader.*
import com.pelican.comictv.transfer.ImportScreen
import com.pelican.comictv.transfer.LocalTransferViewModel
import com.pelican.comictv.transfer.LocalTransferScreen
import com.pelican.comictv.transfer.FtpTransferViewModel
import com.pelican.comictv.transfer.FtpTransferScreen
import androidx.compose.ui.platform.LocalContext

@Composable fun ComicTvApp(container: AppContainer) {
    val nav = rememberNavController()
    var startupFocusPending by rememberSaveable { mutableStateOf(true) }
    var sidebarFocusTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var editionPreview by remember { mutableStateOf<File?>(null) }
    val sourcesOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val library: LibraryViewModel = viewModel(factory = viewModelFactory {
        initializer { LibraryViewModel(container.library, createSavedStateHandle()) }
    })
    val comics by library.comics.collectAsStateWithLifecycle()
    val busy by library.busy.collectAsStateWithLifecycle()
    val message by library.message.collectAsStateWithLifecycle()

    NavHost(navController = nav, startDestination = "library") {
        composable("library") {
            TvShell("library", onNavigate = { startupFocusPending = false; sidebarFocusTarget = it; nav.navigate(it) { launchSingleTop = true } },
                contentFocusPending = startupFocusPending, focusSectionOnEntry = sidebarFocusTarget == "library", onSectionFocused = { sidebarFocusTarget = null }) {
                LibraryScreen(comics, container.library, library.focusedId,
                    onFocus = { library.focusedId = it }, onOpen = { nav.navigate("details/$it") },
                    onImport = { nav.navigate("import") }, onRead = { nav.navigate("reader/$it") },
                    requestInitialFocus = startupFocusPending, onInitialFocus = { startupFocusPending = false })
            }
        }
        composable("import") {
            TvShell("import", onNavigate = { startupFocusPending = false; sidebarFocusTarget = it; nav.navigate(it) { popUpTo("library"); launchSingleTop = true } },
                focusSectionOnEntry = sidebarFocusTarget == "import", onSectionFocused = { sidebarFocusTarget = null }) {
                ImportScreen(busy, message, onMessage = library::message,
                    onImport = { uri -> library.importComic(uri) { id -> nav.navigate("details/$id") { popUpTo("library") } } },
                    onLocalTransfer = { nav.navigate("local-transfer") }, onFtpTransfer = { nav.navigate("ftp-transfer") })
            }
        }
        composable("local-transfer") {
            val context = LocalContext.current.applicationContext
            val transfer: LocalTransferViewModel = viewModel(factory = viewModelFactory {
                initializer { LocalTransferViewModel(context, container.library) }
            })
            LocalTransferScreen(transfer) { nav.popBackStack() }
        }
        composable("ftp-transfer") {
            val context = LocalContext.current.applicationContext
            val ftp: FtpTransferViewModel = viewModel(factory = viewModelFactory {
                initializer { FtpTransferViewModel(context, container.library) }
            })
            FtpTransferScreen(ftp) { nav.popBackStack() }
        }
        listOf("sources", "recent", "extensions").forEach { sourceRoute ->
            composable(sourceRoute) { sourceEntry ->
                val sources: SourcesViewModel = viewModel(viewModelStoreOwner = sourcesOwner, key = "online-sources", factory = viewModelFactory {
                    initializer { SourcesViewModel(container.sourcePreferences, container.mihon, container.extensionInstaller, container.online) }
                })
                LaunchedEffect(sourceEntry.id) { sources.enterMainSection(sourceEntry.id, sourceRoute) }
                val install = rememberExtensionInstallFlow(sources)
                TvShell(sourceRoute, onNavigate = { destination ->
                    startupFocusPending = false
                    sidebarFocusTarget = destination
                    if (destination == sourceRoute) {
                        when (destination) {
                            "recent" -> sources.navigateCatalog(true)
                            "extensions" -> sources.openExtensions()
                            else -> sources.navigateCatalog(false)
                        }
                    } else nav.navigate(destination) { popUpTo("library"); launchSingleTop = true }
                }, focusSectionOnEntry = sidebarFocusTarget == sourceRoute, onSectionFocused = { sidebarFocusTarget = null }) {
                    SourcesScreen(sources, onRead = { nav.navigate("reader/$it") }, onInstall = install)
                }
            }
        }
        composable("settings") {
            TvShell("settings", onNavigate = { startupFocusPending = false; sidebarFocusTarget = it; nav.navigate(it) { popUpTo("library"); launchSingleTop = true } },
                focusSectionOnEntry = sidebarFocusTarget == "settings", onSectionFocused = { sidebarFocusTarget = null }) {
                SettingsScreen(container.appearance) { nav.popBackStack("library", false) }
            }
        }
        composable("details/{id}") { entry ->
            val id = entry.arguments?.getString("id")!!
            DetailsScreen(id, container.library, onRead = { nav.navigate("reader/$id") }, onBack = { nav.popBackStack() }, loadOnlineInfo = container.online::editionInfo)
        }
        composable("reader/{id}") { entry ->
            val id = entry.arguments?.getString("id")!!
            val reader: ReaderViewModel = viewModel(factory = viewModelFactory {
                initializer { ReaderViewModel(if (id.startsWith("online-")) container.online else container.library, id, createSavedStateHandle()) }
            })
            ReaderScreen(reader, onExit = { if (id.startsWith("online-")) nav.popBackStack() else nav.popBackStack("library", false) },
                onEditions = if (id.startsWith("online-")) ({
                    editionPreview = reader.image.value?.file
                    nav.navigate("online-editions/$id") { popUpTo(entry.destination.id) { inclusive = true } }
                }) else null,
                onChapterStep = if (id.startsWith("online-")) ({ step ->
                    editionPreview = reader.image.value?.file
                    nav.navigate("online-editions/$id?step=$step") { popUpTo(entry.destination.id) { inclusive = true } }
                }) else null)
        }
        composable("online-editions/{id}?step={step}", arguments = listOf(navArgument("step") { type = NavType.IntType; defaultValue = 0 })) { entry ->
            val id = entry.arguments?.getString("id")!!
            val editions: OnlineEditionsViewModel = viewModel(factory = viewModelFactory {
                initializer { OnlineEditionsViewModel(id, createSavedStateHandle(), container.online::editions,
                    { context, chapter -> container.online.open(context.source, context.title, chapter, context.pin) },
                    readOrder = { context -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { container.sourcePreferences.reversedSequence(context) } },
                    writeOrder = { context, reversed -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { container.sourcePreferences.saveSequenceOrder(context, reversed) } }) }
            })
            OnlineEditionsScreen(editions, onRead = { next ->
                nav.navigate("reader/$next") { popUpTo(entry.destination.id) { inclusive = true } }
            }, onBack = { nav.navigate("reader/$id") { popUpTo(entry.destination.id) { inclusive = true } } },
                previewFile = editionPreview, initialStep = entry.arguments?.getInt("step") ?: 0, fallbackCover = container.library.cover(id))
        }
    }
}

@Composable internal fun TvShell(selected: String, onNavigate: (String) -> Unit,
    focusSectionOnEntry: Boolean = false, onSectionFocused: () -> Unit = {}, contentFocusPending: Boolean = false, content: @Composable () -> Unit) {
    val sectionFocus = remember { FocusRequester() }
    LaunchedEffect(focusSectionOnEntry) {
        if (focusSectionOnEntry) { sectionFocus.requestFocus(); onSectionFocused() }
    }
    val routes = listOf("library" to "Biblioteca", "recent" to "Novidades", "sources" to "Catálogo", "extensions" to "Extensões", "import" to "Importar", "settings" to "Configurações")
    val icons = listOf(R.drawable.nav_library, R.drawable.nav_recent, R.drawable.nav_sources, R.drawable.nav_extensions, R.drawable.nav_import, R.drawable.nav_settings)
    var focusWithin by remember { mutableStateOf(false) }
    val expanded = focusWithin
    Box(Modifier.fillMaxSize().background(TvColors.Background)) {
        if (selected == "library") BrandedBackground(modifier = Modifier.matchParentSize())
    Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        // The content keeps its full width while the drawer floats above its left edge.
        Box(Modifier.fillMaxSize().padding(start = 80.dp)) { content() }
        Column(Modifier.width(if (expanded) 228.dp else 64.dp).fillMaxHeight().testTag(if (expanded) "sidebar-expanded" else "sidebar-collapsed")
            .background(TvColors.Panel, RoundedCornerShape(18.dp))
            .border(1.dp, TvColors.Muted.copy(alpha = .2f), RoundedCornerShape(18.dp))
            .onFocusChanged { focusWithin = it.hasFocus }.focusGroup().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.CenterStart) {
                if (expanded) ComicSignature(Modifier.fillMaxWidth().height(44.dp))
                else androidx.tv.material3.Icon(painterResource(R.drawable.brand_mark), "Comic TV",
                    Modifier.size(38.dp).align(Alignment.Center), tint = TvColors.Amber)
            }
            routes.forEachIndexed { index, (route, title) ->
                val active = route == selected
                androidx.tv.material3.Button(onClick = { if (route != selected) onNavigate(route) },
                    modifier = Modifier.fillMaxWidth().height(52.dp).focusProperties { canFocus = !contentFocusPending }.testTag("nav-" + route)
                        .semantics { this.selected = active }
                        .then(if (route == selected) Modifier.focusRequester(sectionFocus) else Modifier), contentPadding = PaddingValues(0.dp),
                    shape = androidx.tv.material3.ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    scale = androidx.tv.material3.ButtonDefaults.scale(focusedScale = 1f),
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = if (route == selected) TvColors.Amber.copy(alpha = .15f) else Color.Transparent,
                        contentColor = if (route == selected) TvColors.Amber else Color.White,
                        focusedContainerColor = TvColors.Amber, focusedContentColor = TvColors.Ink)) {
                    Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(48.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            androidx.tv.material3.Icon(painterResource(icons[index]), contentDescription = title, modifier = Modifier.size(26.dp))
                        }
                        if (expanded) Text(title, Modifier.padding(start = 8.dp), fontSize = 17.sp, maxLines = 1)
                    }
                }
            }
        }
    }
    }
}
