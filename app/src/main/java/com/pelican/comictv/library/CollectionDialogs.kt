package com.pelican.comictv.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.pelican.comictv.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable internal fun LibraryDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().heightIn(max = 520.dp)
            .background(TvColors.Background).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, fontSize = 24.sp)
            content()
        }
    }
}

@Composable internal fun LibraryTextInput(value: String, onChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, onDone: () -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(value, onChange, modifier.fillMaxWidth().heightIn(min = 48.dp)
        .onFocusChanged { focused = it.hasFocus }.semantics { contentDescription = label }
        .background(TvColors.Panel).border(if (focused) 2.dp else 1.dp, if (focused) TvColors.Amber else TvColors.Muted)
        .padding(12.dp), textStyle = TextStyle(color = Color.White, fontSize = 18.sp), singleLine = true,
        cursorBrush = SolidColor(TvColors.Amber), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }))
}

@Composable internal fun CollectionNameDialog(repository: LibraryRepository, editing: CollectionEntity? = null,
    onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    var value by remember { mutableStateOf(editing?.name.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    fun save() {
        if (busy) return
        scope.launch {
            busy = true; error = null
            try {
                val id = if (editing == null) repository.createCollection(value)
                    else { repository.renameCollection(editing.id, value); editing.id }
                keyboard?.hide(); onSaved(id)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Não foi possível salvar a coleção." }
            finally { busy = false }
        }
    }
    LibraryDialog(if (editing == null) "Criar coleção" else "Renomear coleção", { if (!busy) onDismiss() }) {
        Text("Nome · até 40 caracteres", color = TvColors.Muted)
        LibraryTextInput(value, { value = it.take(100) }, "Nome da coleção", Modifier.focusRequester(focus), ::save)
        error?.let { Text(it, color = TvColors.Amber) }
        TvButton(if (busy) "Salvando…" else "Salvar", ::save, enabled = !busy)
        TvButton("Cancelar", { keyboard?.hide(); onDismiss() }, enabled = !busy)
    }
    LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
}

@Composable internal fun CollectionPicker(comic: ComicEntity, repository: LibraryRepository, onDismiss: () -> Unit) {
    val collections by remember(repository) { repository.observeCollections() }.collectAsState(initial = emptyList())
    var selected by remember(comic.id) { mutableStateOf(emptySet<String>()) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(comic.id) {
        try { selected = repository.collectionsFor(comic.id); loaded = true }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Não foi possível carregar as coleções. Feche e tente novamente." }
    }
    LibraryDialog("Coleções · " + comic.title, { if (!busy) onDismiss() }) {
        Text("Escolha uma ou várias. Os vínculos mudam somente ao salvar.", color = TvColors.Muted)
        TvButton("Criar coleção", { creating = true }, enabled = !busy && loaded)
        if (!loaded && error == null) Text("Carregando…")
        for (collection in collections) {
            val checked = collection.id in selected
            TvButton((if (checked) "✓ " else "○ ") + collection.name,
                { selected = toggleCollection(selected, collection.id) },
                Modifier.semantics { this.selected = checked }, enabled = loaded && !busy)
        }
        if (loaded && collections.isEmpty()) Text("Crie sua primeira coleção para organizar esta edição.", color = TvColors.Muted)
        error?.let { Text(it, color = TvColors.Amber) }
        TvButton(if (busy) "Salvando…" else "Salvar", {
            if (!busy && loaded) scope.launch {
                busy = true; error = null
                try { repository.setCollections(comic.id, selected.intersect(collections.map { it.id }.toSet())); onDismiss() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { error = "Não foi possível salvar as coleções." }
                finally { busy = false }
            }
        }, enabled = loaded && !busy)
        TvButton("Cancelar", onDismiss, Modifier.focusRequester(first), enabled = !busy)
    }
    LaunchedEffect(Unit) { first.requestFocus() }
    if (creating) CollectionNameDialog(repository, onDismiss = { creating = false }, onSaved = { id -> selected = selected + id; creating = false })
}

@Composable internal fun ManageCollections(repository: LibraryRepository, onDismiss: () -> Unit) {
    val rows by remember(repository) { repository.observeCollections() }.collectAsState(initial = emptyList())
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CollectionEntity?>(null) }
    var deleting by remember { mutableStateOf<CollectionEntity?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var restore by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LibraryDialog("Gerenciar coleções", onDismiss) {
        TvButton("Criar coleção", { creating = true }, Modifier.focusRequester(first))
        if (rows.isEmpty()) Text("Você ainda não criou coleções.", color = TvColors.Muted)
        for (row in rows) {
            Text(row.name, fontSize = 20.sp)
            TvButton("Renomear · " + row.name, { editing = row })
            TvButton("Excluir · " + row.name, { deleting = row })
        }
        error?.let { Text(it, color = TvColors.Amber) }
        TvButton("Fechar", onDismiss)
    }
    LaunchedEffect(restore) { first.requestFocus() }
    if (creating || editing != null) CollectionNameDialog(repository, editing, { creating = false; editing = null; restore++ },
        { creating = false; editing = null; restore++ })
    deleting?.let { row ->
        val cancel = remember { FocusRequester() }
        LibraryDialog("Excluir coleção?", { if (!busy) { deleting = null; restore++ } }) {
            Text("Excluir “${row.name}”? Os quadrinhos, favoritos e o progresso serão mantidos.")
            error?.let { Text(it, color = TvColors.Amber) }
            TvButton("Cancelar", { deleting = null; restore++ }, Modifier.focusRequester(cancel), enabled = !busy)
            TvButton(if (busy) "Excluindo…" else "Excluir coleção", {
                if (!busy) scope.launch {
                    busy = true; error = null
                    try { repository.deleteCollection(row.id); deleting = null; restore++ }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = "Não foi possível excluir a coleção." }
                    finally { busy = false }
                }
            }, enabled = !busy)
        }
        LaunchedEffect(row.id) { cancel.requestFocus() }
    }
}

@Composable internal fun OrganizationActions(comic: ComicEntity, repository: LibraryRepository) {
    var favorite by remember(comic.id, comic.favorite) { mutableStateOf(comic.favorite) }
    var busy by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val collectionsFocus = remember { FocusRequester() }
    var restore by remember { mutableIntStateOf(0) }
    TvButton(if (favorite) "★ Remover dos favoritos" else "☆ Favoritar", {
        if (!busy) scope.launch {
            busy = true; error = null
            try { repository.setFavorite(comic.id, !favorite); favorite = !favorite }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Não foi possível alterar o favorito." }
            finally { busy = false }
        }
    }, enabled = !busy)
    TvButton("Organizar em coleções", { picker = true }, Modifier.focusRequester(collectionsFocus), enabled = !busy)
    error?.let { Text(it, color = TvColors.Amber) }
    if (picker) CollectionPicker(comic, repository) { picker = false; restore++ }
    LaunchedEffect(restore) { if (restore > 0) collectionsFocus.requestFocus() }
}
