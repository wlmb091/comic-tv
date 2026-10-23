package com.pelican.comictv.ui

import androidx.compose.foundation.background
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
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.*
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
                    onImport = { nav.navigate("import") })
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
        composable("sources") {
            val sources: SourcesViewModel = viewModel(factory = viewModelFactory {
                initializer { SourcesViewModel(container.sourcePreferences, container.mihon, container.extensionInstaller, container.online) }
            })
            val install = rememberExtensionInstallFlow(sources)
            TvShell("sources", onNavigate = { nav.navigate(it) { popUpTo("library"); launchSingleTop = true } }) {
                SourcesScreen(sources, onRead = { nav.navigate("reader/$it") }, onInstall = install)
            }
        }
        composable("settings") {
            TvShell("settings", onNavigate = { nav.navigate(it) { popUpTo("library"); launchSingleTop = true } }) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    ScreenTitle("Seu controle, sua leitura", "Comic TV · versão ${BuildConfig.VERSION_NAME}")
                    Text("← →  Página anterior / próxima\n↑ ↓  Aumentar / reduzir zoom\nOK rápido  Alternar zoom/mover\nSegurar OK 2s  Abrir ferramentas\nMover página  Deslocar com as quatro setas\nVoltar  Sair do modo ativo e retornar", fontSize = 20.sp, lineHeight = 34.sp)
                    TvButton("Voltar à biblioteca", { nav.popBackStack("library", false) })
                }
            }
        }
        composable("details/{id}") { entry ->
            val id = entry.arguments?.getString("id")!!
            DetailsScreen(id, container.library, onRead = { nav.navigate("reader/$id") }, onBack = { nav.popBackStack() })
        }
        composable("reader/{id}") { entry ->
            val id = entry.arguments?.getString("id")!!
            val reader: ReaderViewModel = viewModel(factory = viewModelFactory {
                initializer { ReaderViewModel(if (id.startsWith("online-")) container.online else container.library, id, createSavedStateHandle()) }
            })
            ReaderScreen(reader, onExit = { if (id.startsWith("online-")) nav.popBackStack() else nav.popBackStack("library", false) },
                onEditions = if (id.startsWith("online-")) ({
                    nav.navigate("online-editions/$id") { popUpTo(entry.destination.id) { inclusive = true } }
                }) else null)
        }
        composable("online-editions/{id}") { entry ->
            val id = entry.arguments?.getString("id")!!
            val editions: OnlineEditionsViewModel = viewModel(factory = viewModelFactory {
                initializer { OnlineEditionsViewModel(id, createSavedStateHandle(), container.online::editions,
                    { context, chapter -> container.online.open(context.source, context.title, chapter, context.pin) },
                    readOrder = { context -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { container.sourcePreferences.reversedSequence(context) } },
                    writeOrder = { context, reversed -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { container.sourcePreferences.saveSequenceOrder(context, reversed) } }) }
            })
            OnlineEditionsScreen(editions, onRead = { next ->
                nav.navigate("reader/$next") { popUpTo(entry.destination.id) { inclusive = true } }
            }, onBack = { nav.popBackStack() })
        }
    }
}

@Composable private fun TvShell(selected: String, onNavigate: (String) -> Unit, content: @Composable () -> Unit) {
    val routes = listOf("library" to "Biblioteca", "sources" to "Fontes", "import" to "Importar", "settings" to "Configurações")
    val icons = listOf(R.drawable.nav_library, R.drawable.nav_sources, R.drawable.nav_import, R.drawable.nav_settings)
    var focusedRoute by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(TvColors.Background).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.width(60.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("TV", Modifier.height(44.dp), color = TvColors.Amber, fontSize = 24.sp)
                routes.forEachIndexed { index, (route, title) ->
                    androidx.tv.material3.Button(onClick = { if (route != selected) onNavigate(route) },
                        modifier = Modifier.size(56.dp).onFocusChanged {
                            if (it.isFocused) focusedRoute = route else if (focusedRoute == route) focusedRoute = null
                        }, contentPadding = PaddingValues(0.dp),
                        colors = androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = if (route == selected) TvColors.Amber else TvColors.Panel,
                            contentColor = if (route == selected) TvColors.Ink else Color.White,
                            focusedContainerColor = TvColors.Amber, focusedContentColor = TvColors.Ink)) {
                        androidx.tv.material3.Icon(painterResource(icons[index]), contentDescription = title, modifier = Modifier.size(26.dp))
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) { content() }
        }
        // An overlay label explains the focused icon without reserving catalog width.
        val focusedIndex = routes.indexOfFirst { it.first == focusedRoute }
        if (focusedIndex >= 0) Text(routes[focusedIndex].second,
            Modifier.padding(start = 68.dp, top = (62 + focusedIndex * 68).dp)
                .background(TvColors.Panel).padding(horizontal = 14.dp, vertical = 10.dp),
            fontSize = 16.sp, color = TvColors.Amber)
    }
}
