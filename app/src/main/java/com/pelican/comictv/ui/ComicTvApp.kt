package com.pelican.comictv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
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
            TvShell("library", onNavigate = { nav.navigate(it) { launchSingleTop = true } }) {
                LibraryScreen(comics, container.library, library.focusedId,
                    onFocus = { library.focusedId = it }, onOpen = { nav.navigate("details/$it") },
                    onImport = { nav.navigate("import") }, onRead = { nav.navigate("reader/$it") })
            }
        }
        composable("import") {
            TvShell("import", onNavigate = { nav.navigate(it) { popUpTo("library"); launchSingleTop = true } }) {
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
                val sourceState by sources.state.collectAsStateWithLifecycle()
                LaunchedEffect(sourceEntry.id) { sources.enterMainSection(sourceEntry.id, sourceRoute) }
                val install = rememberExtensionInstallFlow(sources)
                val selected = when (sourceState.managerPage) {
                    SourceManagerPage.EXTENSIONS, SourceManagerPage.REPOSITORIES -> "extensions"
                    else -> if (sourceState.latest) "recent" else "sources"
                }
                TvShell(selected, onNavigate = { destination ->
                    if (destination == sourceRoute) {
                        when (destination) {
                            "recent" -> sources.navigateCatalog(true)
                            "extensions" -> sources.openExtensions()
                            else -> sources.navigateCatalog(false)
                        }
                    } else nav.navigate(destination) { popUpTo("library"); launchSingleTop = true }
                }) {
                    SourcesScreen(sources, onRead = { nav.navigate("reader/$it") }, onInstall = install)
                }
            }
        }
        composable("settings") {
            TvShell("settings", onNavigate = { nav.navigate(it) { popUpTo("library"); launchSingleTop = true } }) {
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

@Composable private fun TvShell(selected: String, onNavigate: (String) -> Unit, content: @Composable () -> Unit) {
    val routes = listOf("library" to "Biblioteca", "recent" to "Novidades", "sources" to "Catálogo", "extensions" to "Extensões", "import" to "Importar", "settings" to "Configurações")
    val icons = listOf(R.drawable.nav_library, R.drawable.nav_recent, R.drawable.nav_sources, R.drawable.nav_extensions, R.drawable.nav_import, R.drawable.nav_settings)
    var focusWithin by remember { mutableStateOf(false) }
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val expanded = focusWithin || hovered
    Box(Modifier.fillMaxSize().background(TvColors.Background).padding(horizontal = 20.dp, vertical = 16.dp)) {
        // The content keeps its full width while the drawer floats above its left edge.
        Box(Modifier.fillMaxSize().padding(start = 80.dp)) { content() }
        Column(Modifier.width(if (expanded) 228.dp else 64.dp).fillMaxHeight()
            .background(TvColors.Panel, RoundedCornerShape(18.dp))
            .border(1.dp, TvColors.Muted.copy(alpha = .2f), RoundedCornerShape(18.dp))
            .onFocusChanged { focusWithin = it.hasFocus }.focusGroup().hoverable(hover).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.CenterStart) {
                Text(if (expanded) "COMIC TV" else "TV", Modifier.padding(start = if (expanded) 8.dp else 9.dp),
                    color = TvColors.Amber, fontSize = if (expanded) 22.sp else 24.sp, maxLines = 1)
            }
            routes.forEachIndexed { index, (route, title) ->
                androidx.tv.material3.Button(onClick = { if (route != selected) onNavigate(route) },
                    modifier = Modifier.fillMaxWidth().height(52.dp), contentPadding = PaddingValues(0.dp),
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
