package com.pelican.comictv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.res.painterResource
import com.pelican.comictv.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch

/** Explicit shell action so horizontal navigation can search from any main section. */
internal val LocalShellLibrarySearch = compositionLocalOf<(() -> Unit)?> { null }

@Composable fun ComicTvApp(container: AppContainer) {
    var recovered by remember { mutableStateOf(false) }
    var recoveryError by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        recoveryError = false
        try {
            container.backup.recover(); recovered = true
            launch {
                try { container.downloads.refresh() }
                catch (e: kotlinx.coroutines.CancellationException) {throw e}
                catch (_: Exception) { /* Reader still verifies local packages independently. */ }
            }
        }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { recoveryError = true }
    }
    if (!recovered) {
        Column(Modifier.fillMaxSize().background(TvColors.Background).padding(30.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(if (recoveryError) "Não foi possível concluir uma restauração pendente. Seus arquivos foram mantidos." else "Preparando biblioteca…")
            if (recoveryError) TvButton("Tentar novamente", { attempt++ })
        }
        return
    }
    val nav = rememberNavController()
    val updater: com.pelican.comictv.updates.AppUpdateViewModel = viewModel(key = "app-updates", factory = viewModelFactory {
        initializer { com.pelican.comictv.updates.AppUpdateViewModel(container.appUpdates) }
    })
    val installUpdate = com.pelican.comictv.updates.rememberAppUpdateInstallFlow(updater)
    val updateState by updater.state.collectAsStateWithLifecycle()
    val updateRoute by nav.currentBackStackEntryAsState()
    val updateContext = LocalContext.current
    val updateLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var notifiedVersion by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(updater) { updater.automaticCheck() }
    DisposableEffect(updateLifecycle, updater) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) updater.backgrounded()
        }
        updateLifecycle.addObserver(observer)
        onDispose { updateLifecycle.removeObserver(observer) }
    }
    LaunchedEffect(updateState.update?.versionCode, updateRoute?.destination?.route) {
        val candidate = updateState.update
        if (candidate != null && notifiedVersion != candidate.versionCode && updateRoute?.destination?.route in
            setOf("library", "sources", "recent", "extensions", "import", "settings")) {
            notifiedVersion = candidate.versionCode
            android.widget.Toast.makeText(updateContext, "Comic TV ${candidate.versionName} disponível. Abra Configurações → Atualizações.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    var startupFocusPending by rememberSaveable { mutableStateOf(true) }
    var navigationContentTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var relatedTitle by remember { mutableStateOf<RelatedComic?>(null) }
    var editionPreview by remember { mutableStateOf<File?>(null) }
    val sourcesOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val sourcesFactory = remember(container) { viewModelFactory {
        initializer {SourcesViewModel(container.sourcePreferences,container.mihon,container.extensionInstaller,container.online)}
    } }
    val sourcesProvider = remember(sourcesOwner,sourcesFactory) {androidx.lifecycle.ViewModelProvider(sourcesOwner,sourcesFactory)}
    // One activity-scoped launcher survives section changes; source VM is still created lazily.
    val installExtension = rememberExtensionInstallFlow {sourcesProvider.get("online-sources",SourcesViewModel::class.java)}
    val library: LibraryViewModel = viewModel(factory = viewModelFactory {
        initializer { LibraryViewModel(container.library, createSavedStateHandle()) }
    })
    val comics by library.comics.collectAsStateWithLifecycle()
    val busy by library.busy.collectAsStateWithLifecycle()
    val message by library.message.collectAsStateWithLifecycle()
    val collections by library.collections.collectAsStateWithLifecycle()
    val memberships by library.memberships.collectAsStateWithLifecycle()
    val libraryQuery by library.browse.query.collectAsStateWithLifecycle()
    val libraryFilter by library.browse.filter.collectAsStateWithLifecycle()
    val downloadsState by container.downloads.state.collectAsStateWithLifecycle()
    fun openDownload(id: String) { nav.navigate(if (id in downloadsState.complete) "downloads" else "downloads?edition=$id") }
    // A pending action is consumed by LibraryScreen, including when the destination is newly composed.
    // It is deliberately not saved: returning from details/recreation must not replay a search.
    var librarySearchPending by remember { mutableStateOf(false) }
    val openLibrarySearch: () -> Unit = {
        startupFocusPending = false
        navigationContentTarget = null
        librarySearchPending = true
        if (nav.currentDestination?.route != "library") {
            nav.navigate("library") { popUpTo("library"); launchSingleTop = true }
        }
    }

    CompositionLocalProvider(LocalShellLibrarySearch provides openLibrarySearch) {
    NavHost(navController = nav, startDestination = "library") {
        composable("library") {
            var backdropId by remember { mutableStateOf(library.focusedId) }
            TvShell("library", onNavigate = { startupFocusPending = false; navigationContentTarget = it; nav.navigate(it) { launchSingleTop = true } },
                contentFocusPending = startupFocusPending, focusContentOnEntry = navigationContentTarget == "library", onSectionFocused = { navigationContentTarget = null },
                onSearch = openLibrarySearch,
                backdrop = {
                    if (LocalThemeSpec.current.navigation.shellBackdrop) {
                        CinemaBackdrop(featuredComic(comics, backdropId)?.id, container.library, Modifier.fillMaxSize())
                    }
                }) {
                LibraryScreen(comics, container.library, library.focusedId,
                    onFocus = { library.focusedId = it; backdropId = it }, onOpen = { nav.navigate("details/$it") },
                    onImport = { nav.navigate("import") }, onRead = { nav.navigate("reader/$it") },
                    requestInitialFocus = startupFocusPending, onInitialFocus = { startupFocusPending = false },
                    query = libraryQuery, filter = libraryFilter, collections = collections, memberships = memberships,
                    searchRequested = librarySearchPending, onSearchHandled = { librarySearchPending = false },
                    onQuery = library.browse::setQuery, onFilter = library.browse::setFilter,
                    offlineIds = downloadsState.complete,onDownload = ::openDownload, onDeleteOnline = container::deleteOnlineEdition)
            }
        }
        composable("import") {
            TvShell("import", onNavigate = { startupFocusPending = false; navigationContentTarget = it; nav.navigate(it) { popUpTo("library"); launchSingleTop = true } },
                focusContentOnEntry = navigationContentTarget == "import", onSectionFocused = { navigationContentTarget = null }) {
                ImportScreen(busy, message, onMessage = library::message,
                    onImport = { uri -> library.importComic(uri) { id -> nav.navigate("details/$id") { popUpTo("library") } } },
                    onLocalTransfer = { nav.navigate("local-transfer") }, onFtpTransfer = { nav.navigate("ftp-transfer") },
                    onCancelImport = library::cancelImport)
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
                val sources: SourcesViewModel = viewModel(viewModelStoreOwner = sourcesOwner, key = "online-sources", factory = sourcesFactory)
                LaunchedEffect(sourceEntry.id) { sources.enterMainSection(sourceEntry.id, sourceRoute) }
                TvShell(sourceRoute, onNavigate = { destination ->
                    startupFocusPending = false
                    navigationContentTarget = destination
                    if (destination == sourceRoute) {
                        when (destination) {
                            "recent" -> sources.navigateCatalog(true)
                            "extensions" -> sources.openExtensions()
                            else -> sources.navigateCatalog(false)
                        }
                    } else nav.navigate(destination) { popUpTo("library"); launchSingleTop = true }
                }, focusContentOnEntry = navigationContentTarget == sourceRoute, onSectionFocused = { navigationContentTarget = null }) {
                    SourcesScreen(sources, onRead = { nav.navigate("reader/$it") }, onInstall = installExtension)
                }
            }
        }
        composable("settings") {
            val app = LocalContext.current.applicationContext
            val backup: com.pelican.comictv.backup.BackupViewModel = viewModel(factory = viewModelFactory {
                initializer { com.pelican.comictv.backup.BackupViewModel(app, container.backup) }
            })
            val storage: com.pelican.comictv.storage.StorageViewModel = viewModel(factory = viewModelFactory {
                initializer { com.pelican.comictv.storage.StorageViewModel(container.library::storageReport, container.library::clearReaderCaches) }
            })
            TvShell("settings", onNavigate = { startupFocusPending = false; navigationContentTarget = it; nav.navigate(it) { popUpTo("library"); launchSingleTop = true } },
                focusContentOnEntry = navigationContentTarget == "settings", onSectionFocused = { navigationContentTarget = null }) {
                SettingsScreen(container.appearance, storage, backup, onDownloads = {nav.navigate("downloads")}, updater = updater, installUpdate = installUpdate) { nav.popBackStack("library", false) }
            }
        }
        composable("details/{id}") { entry ->
            val id = entry.arguments?.getString("id")!!
            DetailsScreen(id, container.library, onRead = { nav.navigate("reader/$id") }, onBack = { nav.popBackStack() }, loadOnlineInfo = container.online::editionInfo, loadRelated = container.online::related,
                onDeleteOnline = container::deleteOnlineEdition,
                onEditions = if (id.startsWith("online-")) ({ nav.navigate("online-editions/$id?from=details") }) else null,
                offlineAvailable = id in downloadsState.complete, onDownload = {openDownload(id)}, onRelated = { selected ->
                    if (selected.libraryId != null) nav.navigate("details/" + selected.libraryId)
                    else { relatedTitle = selected; nav.navigate("related-title") }
                })
        }
        composable("downloads?edition={edition}",arguments = listOf(navArgument("edition") {type = NavType.StringType;nullable = true;defaultValue = null})) { entry ->
            val downloads: com.pelican.comictv.offline.DownloadsViewModel = viewModel(factory = viewModelFactory {
                initializer {com.pelican.comictv.offline.DownloadsViewModel(container.downloads)}
            })
            com.pelican.comictv.offline.DownloadsScreen(downloads,comics,entry.arguments?.getString("edition"),
                onBack = {nav.popBackStack()},onRead = {nav.navigate("reader/$it")})
        }
        composable("related-title") {
            com.pelican.comictv.details.RelatedTitleScreen(relatedTitle, container.online,
                onRead = { nav.navigate("reader/$it") }, onBack = { nav.popBackStack() })
        }
        composable("reader/{id}") { entry ->
            val id = entry.arguments?.getString("id")!!
            val reader: ReaderViewModel = viewModel(factory = viewModelFactory {
                initializer { ReaderViewModel(if (id.startsWith("online-")) container.onlineReader else container.library, id, createSavedStateHandle(), thumbnailWarm = container::warmReaderThumbnail, imagePreferences = container.readerImages) }
            })
            ReaderScreen(reader, onExit = { if (id.startsWith("online-")) nav.popBackStack() else nav.popBackStack("library", false) },
                onEditions = if (id.startsWith("online-")) ({
                    editionPreview = reader.image.value?.file
                    nav.navigate("online-editions/$id") { popUpTo(entry.destination.id) { inclusive = true } }
                }) else null,
                onChapterStep = if (id.startsWith("online-")) ({ step ->
                    editionPreview = reader.image.value?.file
                    nav.navigate("online-editions/$id?step=$step") { popUpTo(entry.destination.id) { inclusive = true } }
                }) else null,
                narrationOriginal = if (id.startsWith("online-")) null else container.library.originalFile(id))
        }
        composable("online-editions/{id}?step={step}&from={from}", arguments = listOf(
            navArgument("step") { type = NavType.IntType; defaultValue = 0 },
            navArgument("from") { type = NavType.StringType; defaultValue = "reader" },
        )) { entry ->
            val id = entry.arguments?.getString("id")!!
            val fromDetails = entry.arguments?.getString("from") == "details"
            val editions: OnlineEditionsViewModel = viewModel(factory = viewModelFactory {
                initializer { OnlineEditionsViewModel(id, createSavedStateHandle(), container.online::editions,
                    { context, chapter -> container.online.open(context.source, context.title, chapter, context.pin) },
                    readOrder = { context -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { container.sourcePreferences.chapterSequenceOrder(context) } },
                    writeOrder = { context, order -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { container.sourcePreferences.saveSequenceOrder(context, order) } }) }
            })
            OnlineEditionsScreen(editions, onRead = { next ->
                nav.navigate("reader/$next") { popUpTo(entry.destination.id) { inclusive = true } }
            }, onBack = {
                if (fromDetails) nav.popBackStack()
                else nav.navigate("reader/$id") { popUpTo(entry.destination.id) { inclusive = true } }
            }, previewFile = if (fromDetails) null else editionPreview,
                initialStep = entry.arguments?.getInt("step") ?: 0, fallbackCover = container.library.cover(id),
                backLabel = if (fromDetails) "Voltar aos detalhes" else "Voltar à leitura",
                previewDescription = if (fromDetails) "Capa da edição" else "Página atual da leitura")
        }
    }
    }
}

@Composable internal fun TvShell(selected: String, onNavigate: (String) -> Unit,
    focusSectionOnEntry: Boolean = false, onSectionFocused: () -> Unit = {}, contentFocusPending: Boolean = false,
    focusContentOnEntry: Boolean = false, onSearch: (() -> Unit)? = null,
    backdrop: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    ThemedScaffold(selected, onNavigate, focusSectionOnEntry, onSectionFocused, contentFocusPending,
        focusContentOnEntry, onSearch, backdrop, content)
}

@Composable internal fun ThemedScaffold(selected: String, onNavigate: (String) -> Unit,
    focusSectionOnEntry: Boolean = false, onSectionFocused: () -> Unit = {}, contentFocusPending: Boolean = false,
    focusContentOnEntry: Boolean = false, onSearch: (() -> Unit)? = null,
    backdrop: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    val theme = LocalThemeSpec.current
    val policy = theme.navigation.layout
    val sectionFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val preferredEntry = remember { ContentEntryTarget() }
    var atContentEntry by remember { mutableStateOf(false) }
    var internalEntry by remember { mutableStateOf(false) }
    var internalNavigationVersion by remember { mutableIntStateOf(0) }
    var focusWithin by remember { mutableStateOf(false) }
    var cinemaMenu by remember(policy) { mutableStateOf(false) }
    fun returnToContent(resetToFirst: Boolean = false) {
        if (!preferredEntry.requestFocus(resetToFirst)) contentFocus.requestFocus()
        cinemaMenu = false
    }
    fun openNavigation() {
        preferredEntry.cancel()
        if (policy == ThemeNavigationPolicy.ImmersiveMenu && !cinemaMenu) cinemaMenu = true
        else sectionFocus.requestFocus()
    }
    fun navigateWithinContent(change: () -> Unit) {
        preferredEntry.cancel()
        contentFocus.requestFocus() // Park before the focused folder/source card is removed.
        internalEntry = true
        change()
        internalNavigationVersion++
    }
    LaunchedEffect(cinemaMenu) {
        if (cinemaMenu) { withFrameNanos { }; sectionFocus.requestFocus() }
    }
    LaunchedEffect(internalNavigationVersion) {
        if (internalNavigationVersion > 0) {
            withFrameNanos { }
            if (atContentEntry) returnToContent(true) else internalEntry = false
        }
    }
    LaunchedEffect(focusContentOnEntry) {
        if (focusContentOnEntry) { withFrameNanos { }; returnToContent(true); onSectionFocused() }
    }
    LaunchedEffect(focusSectionOnEntry) {
        if (focusSectionOnEntry) { openNavigation(); onSectionFocused() }
    }
    val searchAction = onSearch ?: LocalShellLibrarySearch.current
    val showLibrarySearch = searchAction != null && theme.navigation.searchInNavigation &&
        (policy != ThemeNavigationPolicy.ImmersiveMenu || selected == "library")
    val routes = (if (showLibrarySearch) listOf("search" to "Buscar") else emptyList()) +
        listOf("library" to "Biblioteca", "recent" to "Novidades", "sources" to "Catálogo", "extensions" to "Extensões", "import" to "Importar", "settings" to "Configurações")
    val icons = mapOf("library" to R.drawable.nav_library, "recent" to R.drawable.nav_recent,
        "sources" to R.drawable.nav_sources, "extensions" to R.drawable.nav_extensions,
        "import" to R.drawable.nav_import, "settings" to R.drawable.nav_settings)
    val density = LocalDensity.current
    val fontScale = density.fontScale.coerceAtLeast(1f)
    var measuredDockHeight by remember { mutableStateOf(74.dp) }
    val immersiveLibrary = policy == ThemeNavigationPolicy.ImmersiveMenu && selected == "library"
    val expanded = focusWithin || policy == ThemeNavigationPolicy.ImmersiveMenu
    val flix = theme.navigation.framelessSidebar
    val framelessSidebar = flix && !TvColors.options.highContrast
    val iconOnlySidebar = flix && !expanded && !TvColors.options.highContrast
    val expandedWidth = minOf(320.dp, 228.dp * fontScale)
    val horizontal = policy == ThemeNavigationPolicy.TopBar || policy == ThemeNavigationPolicy.BottomDock
    val navigationToContentKey = when (policy) {
        ThemeNavigationPolicy.TopBar -> Key.DirectionDown
        ThemeNavigationPolicy.BottomDock -> Key.DirectionUp
        ThemeNavigationPolicy.ImmersiveMenu -> Key.DirectionLeft
        else -> Key.DirectionRight
    }
    val entryDirection = when (policy) {
        ThemeNavigationPolicy.TopBar -> androidx.compose.ui.focus.FocusDirection.Up
        ThemeNavigationPolicy.BottomDock -> androidx.compose.ui.focus.FocusDirection.Down
        ThemeNavigationPolicy.ImmersiveMenu -> androidx.compose.ui.focus.FocusDirection.Right
        else -> androidx.compose.ui.focus.FocusDirection.Left
    }
    @Composable fun NavigationIcon(route: String, title: String, modifier: Modifier) {
        val drawable = icons[route]
        if (drawable != null) androidx.tv.material3.Icon(painterResource(drawable),
            if (expanded || horizontal) null else title, modifier)
        else {
            val tint = androidx.tv.material3.LocalContentColor.current
            androidx.compose.foundation.Canvas(modifier) {
                val stroke = 1.7.dp.toPx()
                drawCircle(tint, radius = size.minDimension * .27f,
                    center = androidx.compose.ui.geometry.Offset(size.width * .42f, size.height * .42f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                drawLine(tint, androidx.compose.ui.geometry.Offset(size.width * .62f, size.height * .62f),
                    androidx.compose.ui.geometry.Offset(size.width * .86f, size.height * .86f),
                    strokeWidth = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
    }
    @Composable fun NavigationItem(index: Int, modifier: Modifier = Modifier) {
        val (route, title) = routes[index]
        val active = route == selected
        var iconFocused by remember(route) { mutableStateOf(false) }
        val iconScale by animateFloatAsState(if (iconFocused && !TvColors.options.highContrast) theme.navigation.iconFocusScale else 1f,
            tween(theme.motion.navigationIconMs, easing = CubicBezierEasing(.23f, 1f, .32f, 1f)), label = "nav-icon-scale")
        androidx.tv.material3.Button(onClick = {
            if (route == "search") { returnToContent(); searchAction?.invoke() }
            else if (active) returnToContent(true)
            else { contentFocus.requestFocus(); cinemaMenu = false; onNavigate(route) }
        }, modifier = modifier.heightIn(min = if (horizontal) 52.dp * fontScale else 52.dp)
            .focusProperties { canFocus = !contentFocusPending && !focusContentOnEntry }
            .testTag("nav-" + route).onFocusChanged { iconFocused = it.isFocused }
            .semantics { this.selected = active }
            .then(if (active) Modifier.focusRequester(sectionFocus) else Modifier),
            contentPadding = PaddingValues(0.dp),
            shape = androidx.tv.material3.ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
            scale = androidx.tv.material3.ButtonDefaults.scale(focusedScale = if (policy == ThemeNavigationPolicy.BottomDock) theme.navigation.dockFocusScale else 1f),
            border = androidx.tv.material3.ButtonDefaults.border(focusedBorder = androidx.tv.material3.Border(
                androidx.compose.foundation.BorderStroke(if (TvColors.options.highContrast) 3.dp else 2.dp, TvColors.FocusAccent))),
            colors = androidx.tv.material3.ButtonDefaults.colors(
                containerColor = if (active && !iconOnlySidebar && policy != ThemeNavigationPolicy.TopBar)
                    TvColors.Amber.copy(alpha = if (policy == ThemeNavigationPolicy.BottomDock) theme.navigation.dockActiveAlpha else .15f) else Color.Transparent,
                contentColor = if (active) TvColors.Amber else Color.White,
                focusedContainerColor = TvColors.Action, focusedContentColor = TvColors.OnAction)) {
            if (policy == ThemeNavigationPolicy.TopBar) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (route == "search") NavigationIcon(route, title, Modifier.size(18.dp))
                    Text(title, fontSize = 13.sp, maxLines = 1, softWrap = false)
                }
            } else if (policy == ThemeNavigationPolicy.BottomDock) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    NavigationIcon(route, title,
                        Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale })
                    Text(title, fontSize = theme.navigation.dockLabelSp.sp, maxLines = 2, softWrap = true,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(if (horizontal) 32.dp else 48.dp).height(36.dp), contentAlignment = Alignment.Center) {
                        NavigationIcon(route, title,
                            Modifier.size(if (horizontal) 22.dp else 26.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale })
                    }
                    if (expanded || horizontal) Text(title, Modifier.weight(1f).padding(start = if (horizontal) 0.dp else 8.dp),
                        fontSize = if (horizontal) 13.sp else 17.sp)
                }
            }
        }
    }
    val navigationModifier = Modifier.onFocusChanged {
        val wasFocused = focusWithin
        focusWithin = it.hasFocus
        if (it.hasFocus) preferredEntry.cancel()
        else if (wasFocused && policy == ThemeNavigationPolicy.ImmersiveMenu) cinemaMenu = false
    }.onPreviewKeyEvent { event ->
        if (event.key == navigationToContentKey) {
            if (event.type == KeyEventType.KeyDown) returnToContent()
            true
        } else false
    }.focusProperties {
        // Always return to the current section, regardless of the nearest icon's geometry.
        onEnter = { if (requestedFocusDirection == entryDirection) sectionFocus.requestFocus() }
    }.focusGroup()
    val flixScrimProgress by animateFloatAsState(if (flix && expanded) 1f else 0f,
        tween(160, easing = CubicBezierEasing(.23f, 1f, .32f, 1f)), label = "flix-menu-gradient")
    Box(Modifier.fillMaxSize().background(TvColors.Background).testTag("theme-shell")) {
        if (selected == "library") BrandedBackground(modifier = Modifier.matchParentSize())
        backdrop()
        val shellInsets = if (immersiveLibrary) Modifier else Modifier.padding(horizontal = theme.navigation.safeHorizontalDp.dp, vertical = theme.navigation.safeVerticalDp.dp)
        Box(Modifier.fillMaxSize()) {
            val contentPadding = when (policy) {
                ThemeNavigationPolicy.Sidebar -> PaddingValues(start = 80.dp)
                ThemeNavigationPolicy.TopBar -> PaddingValues(top = 72.dp * fontScale)
                ThemeNavigationPolicy.BottomDock -> PaddingValues(bottom = measuredDockHeight + 12.dp)
                ThemeNavigationPolicy.ImmersiveMenu -> if (immersiveLibrary) PaddingValues(0.dp) else PaddingValues(top = 64.dp * fontScale)
            }
            CompositionLocalProvider(LocalPreferredContentEntry provides preferredEntry,
                LocalSectionContentEntry provides (focusContentOnEntry || internalEntry),
                LocalContentNavigation provides ::navigateWithinContent) {
                TvContentEntry(contentFocus, sectionFocus, Modifier.fillMaxSize().then(shellInsets).padding(contentPadding)
                    .onFocusChanged { atContentEntry = it.isFocused },
                    onEntryLeft = { preferredEntry.cancel(); internalEntry = false },
                    navigationPolicy = policy, onNavigateToMenu = ::openNavigation, content = content)
            }
            if (flix && (expanded || flixScrimProgress > 0f)) {
                Box(Modifier.matchParentSize().testTag("flix-navigation-scrim")
                    .background(if (TvColors.options.highContrast) Brush.horizontalGradient(listOf(Color.Black, Color.Black))
                        else Brush.horizontalGradient(0f to Color.Black.copy(alpha = flixScrimProgress), .28f to Color.Black.copy(alpha = .94f * flixScrimProgress),
                            .65f to Color.Black.copy(alpha = .5f * flixScrimProgress), 1f to Color.Transparent)))
            }
            Box(Modifier.fillMaxSize().then(shellInsets)) {
                if (policy == ThemeNavigationPolicy.ImmersiveMenu && !cinemaMenu) {
                    Box(Modifier.align(Alignment.TopEnd).then(if (immersiveLibrary) Modifier.padding(horizontal = 20.dp, vertical = 16.dp) else Modifier)) {
                        TvButton("Menu", ::openNavigation, Modifier.testTag("cinema-menu")
                            .onPreviewKeyEvent { event ->
                                if (event.key == Key.DirectionDown) {
                                    if (event.type == KeyEventType.KeyDown) returnToContent()
                                    true
                                } else false
                            }, transparentBackground = true)
                    }
                }
                if (policy == ThemeNavigationPolicy.TopBar) {
                    val brandWidth = 150.dp * fontScale
                    // Equal gutters keep the menu centred on the screen, independently of the right-hand brand.
                    // Scrolling preserves every label and target at large Android/app font scales.
                    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().testTag("navigation-top")) {
                        if (TvColors.options.highContrast) Box(Modifier.matchParentSize()
                            .testTag("prime-navigation-panel").glassPanel(surface = TvColors.FrostedSidebar))
                        Row(Modifier.align(Alignment.Center).padding(horizontal = brandWidth + 12.dp)
                            .fillMaxWidth().testTag("prime-navigation-viewport")
                            .horizontalScroll(rememberScrollState()).then(navigationModifier),
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically) {
                            routes.indices.forEach { NavigationItem(it) }
                        }
                        ComicWordmark(Modifier.align(Alignment.CenterEnd).width(brandWidth)
                            .testTag("prime-wordmark"))
                    }
                } else if (policy == ThemeNavigationPolicy.BottomDock) {
                    Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().testTag("navigation-bottom")
                        .onSizeChanged { measuredDockHeight = with(density) { it.height.toDp() } }
                        .background(if (TvColors.options.highContrast) Color.Black else Color(theme.navigation.dockSurface ?: 0xD11E1440), RoundedCornerShape(10.dp))
                        .border(1.dp, if (TvColors.options.highContrast) Color.White else Color(theme.navigation.dockBorder ?: 0x739B6DFF), RoundedCornerShape(10.dp))
                        .padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(navigationModifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            routes.indices.forEach { NavigationItem(it, Modifier.weight(1f)) }
                        }
                    }
                } else if (policy == ThemeNavigationPolicy.Sidebar || cinemaMenu) {
                    val sidebarReflection by animateFloatAsState(if (expanded) 1f else .6f,
                        tween(160, easing = CubicBezierEasing(.23f, 1f, .32f, 1f)), label = "sidebar-reflection")
                    Box(Modifier.align(if (policy == ThemeNavigationPolicy.ImmersiveMenu) Alignment.TopEnd else Alignment.TopStart)
                        .width(if (expanded) expandedWidth else 64.dp).fillMaxHeight()) {
                        if (!framelessSidebar) {
                            val panel = if (policy == ThemeNavigationPolicy.ImmersiveMenu) {
                                val shape = RoundedCornerShape(8.dp)
                                Modifier.background(transparentButtonSurface(TvColors.options) ?: TvColors.ButtonSurface, shape)
                                    .border(1.dp, if (TvColors.options.highContrast) Color.White else Color.White.copy(alpha = .28f), shape)
                            } else Modifier.glassPanel(surface = TvColors.FrostedSidebar, reflection = { sidebarReflection })
                            Box(Modifier.matchParentSize().testTag("sidebar-panel").then(panel))
                        }
                        Column(Modifier.fillMaxSize()
                            .testTag(if (expanded) "sidebar-expanded" else "sidebar-collapsed")
                            .then(navigationModifier).verticalScroll(rememberScrollState()).padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.fillMaxWidth().heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) {
                                if (expanded) ComicWordmark(Modifier.fillMaxWidth().heightIn(min = 44.dp), centered = true)
                                else androidx.tv.material3.Icon(painterResource(R.drawable.brand_mark), "Comic TV",
                                    Modifier.size(38.dp).align(Alignment.Center), tint = TvColors.Amber)
                            }
                            routes.indices.forEach { NavigationItem(it, Modifier.fillMaxWidth()) }
                        }
                    }
                }
            }
        }
    }
    // Registered after route handlers: Back closes the overlay before navigating its underlying page.
    androidx.activity.compose.BackHandler(policy == ThemeNavigationPolicy.ImmersiveMenu && cinemaMenu) {
        returnToContent()
    }
}
