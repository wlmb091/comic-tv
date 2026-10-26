package com.pelican.comictv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusGroup
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
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch

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

    NavHost(navController = nav, startDestination = "library") {
        composable("library") {
            TvShell("library", onNavigate = { startupFocusPending = false; navigationContentTarget = it; nav.navigate(it) { launchSingleTop = true } },
                contentFocusPending = startupFocusPending, focusContentOnEntry = navigationContentTarget == "library", onSectionFocused = { navigationContentTarget = null }) {
                LibraryScreen(comics, container.library, library.focusedId,
                    onFocus = { library.focusedId = it }, onOpen = { nav.navigate("details/$it") },
                    onImport = { nav.navigate("import") }, onRead = { nav.navigate("reader/$it") },
                    requestInitialFocus = startupFocusPending, onInitialFocus = { startupFocusPending = false },
                    query = libraryQuery, filter = libraryFilter, collections = collections, memberships = memberships,
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

@Composable internal fun TvShell(selected: String, onNavigate: (String) -> Unit,
    focusSectionOnEntry: Boolean = false, onSectionFocused: () -> Unit = {}, contentFocusPending: Boolean = false,
    focusContentOnEntry: Boolean = false, content: @Composable () -> Unit) {
    val sectionFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val preferredEntry = remember { ContentEntryTarget() }
    var atContentEntry by remember { mutableStateOf(false) }
    var internalEntry by remember { mutableStateOf(false) }
    var internalNavigationVersion by remember { mutableIntStateOf(0) }
    fun returnToContent(resetToFirst: Boolean = false) {
        if (!preferredEntry.requestFocus(resetToFirst)) contentFocus.requestFocus()
    }
    fun navigateWithinContent(change: () -> Unit) {
        preferredEntry.cancel()
        contentFocus.requestFocus() // Park before the focused folder/source card is removed.
        internalEntry = true
        change()
        internalNavigationVersion++
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
        if (focusSectionOnEntry) { sectionFocus.requestFocus(); onSectionFocused() }
    }
    val routes = listOf("library" to "Biblioteca", "recent" to "Novidades", "sources" to "Catálogo", "extensions" to "Extensões", "import" to "Importar", "settings" to "Configurações")
    val icons = listOf(R.drawable.nav_library, R.drawable.nav_recent, R.drawable.nav_sources, R.drawable.nav_extensions, R.drawable.nav_import, R.drawable.nav_settings)
    var focusWithin by remember { mutableStateOf(false) }
    val expanded = focusWithin
    val expandedWidth = minOf(320.dp, 228.dp * LocalDensity.current.fontScale.coerceAtLeast(1f))
    Box(Modifier.fillMaxSize().background(TvColors.Background)) {
        if (selected == "library") BrandedBackground(modifier = Modifier.matchParentSize())
    Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        // The content keeps its full width while the drawer floats above its left edge.
        CompositionLocalProvider(LocalPreferredContentEntry provides preferredEntry,
            LocalSectionContentEntry provides (focusContentOnEntry || internalEntry),
            LocalContentNavigation provides ::navigateWithinContent) {
            TvContentEntry(contentFocus, sectionFocus, Modifier.fillMaxSize().padding(start = 80.dp)
                .onFocusChanged { atContentEntry = it.isFocused },
                onEntryLeft = { preferredEntry.cancel(); internalEntry = false }, content = content)
        }
        Column(Modifier.width(if (expanded) expandedWidth else 64.dp).fillMaxHeight().testTag(if (expanded) "sidebar-expanded" else "sidebar-collapsed")
            .background(TvColors.Panel, RoundedCornerShape(18.dp))
            .border(1.dp, TvColors.Muted.copy(alpha = .2f), RoundedCornerShape(18.dp))
            .onFocusChanged { focusWithin = it.hasFocus; if (it.hasFocus) preferredEntry.cancel() }.onPreviewKeyEvent { event ->
                if (event.key == Key.DirectionRight) {
                    if (event.type == KeyEventType.KeyDown) returnToContent()
                    true
                } else false
            }.focusProperties {
                // Spatial search from a top content control otherwise picks Biblioteca by proximity.
                onEnter = { if (requestedFocusDirection == androidx.compose.ui.focus.FocusDirection.Left) sectionFocus.requestFocus() }
            }.focusGroup()
            .verticalScroll(rememberScrollState()).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) {
                if (expanded) ComicWordmark(Modifier.fillMaxWidth().heightIn(min = 44.dp), centered = true)
                else androidx.tv.material3.Icon(painterResource(R.drawable.brand_mark), "Comic TV",
                    Modifier.size(38.dp).align(Alignment.Center), tint = TvColors.Amber)
            }
            routes.forEachIndexed { index, (route, title) ->
                val active = route == selected
                androidx.tv.material3.Button(onClick = {
                    if (route == selected) returnToContent(true)
                    else { contentFocus.requestFocus(); onNavigate(route) }
                },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).focusProperties { canFocus = !contentFocusPending && !focusContentOnEntry }.testTag("nav-" + route)
                        .semantics { this.selected = active }
                        .then(if (route == selected) Modifier.focusRequester(sectionFocus) else Modifier), contentPadding = PaddingValues(0.dp),
                    shape = androidx.tv.material3.ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    scale = androidx.tv.material3.ButtonDefaults.scale(focusedScale = 1f),
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = if (route == selected) TvColors.Amber.copy(alpha = .15f) else Color.Transparent,
                        contentColor = if (route == selected) TvColors.Amber else Color.White,
                        focusedContainerColor = TvColors.Action, focusedContentColor = TvColors.OnAction)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(48.dp).height(36.dp), contentAlignment = Alignment.Center) {
                            androidx.tv.material3.Icon(painterResource(icons[index]), contentDescription = title, modifier = Modifier.size(26.dp))
                        }
                        if (expanded) Text(title, Modifier.weight(1f).padding(start = 8.dp), fontSize = 17.sp)
                    }
                }
            }
        }
    }
    }
}
