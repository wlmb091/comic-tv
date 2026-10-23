package com.pelican.comictv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
                    onLocalTransfer = { nav.navigate("local-transfer") })
            }
        }
        composable("local-transfer") {
            val context = LocalContext.current.applicationContext
            val transfer: LocalTransferViewModel = viewModel(factory = viewModelFactory {
                initializer { LocalTransferViewModel(context, container.library) }
            })
            LocalTransferScreen(transfer) { nav.popBackStack() }
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
                    Text("← →  Página anterior / próxima\n↑ ↓  Aumentar / reduzir zoom\nOK  Abrir ferramentas\nMover página  Deslocar com as quatro setas\nVoltar  Sair do modo ativo e retornar", fontSize = 20.sp, lineHeight = 34.sp)
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
            ReaderScreen(reader, onExit = { if (id.startsWith("online-")) nav.popBackStack() else nav.popBackStack("library", false) })
        }
    }
}

@Composable private fun TvShell(selected: String, onNavigate: (String) -> Unit, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxSize().background(TvColors.Background).padding(horizontal = 36.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(30.dp)) {
        Column(Modifier.width(166.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.padding(bottom = 28.dp)) {
                Text("COMIC ", fontSize = 24.sp)
                Text("TV", color = TvColors.Amber, fontSize = 24.sp)
            }
            listOf("library" to "Biblioteca", "sources" to "Fontes", "import" to "Importar", "settings" to "Configurações").forEach { (route, title) ->
                TvButton(title, { if (route != selected) onNavigate(route) }, Modifier.fillMaxWidth(), primary = route == selected)
            }
            Spacer(Modifier.weight(1f))
            Text("SUA PRÓXIMA\nHISTÓRIA ESTÁ AQUI.", fontSize = 12.sp, lineHeight = 19.sp, color = TvColors.Muted)
        }
        Box(Modifier.weight(1f).fillMaxHeight()) { content() }
    }
}
