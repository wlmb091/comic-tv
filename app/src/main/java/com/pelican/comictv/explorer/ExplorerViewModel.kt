package com.pelican.comictv.explorer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class ExplorerState(val roots: List<ExplorerFolder> = emptyList(),
    val folder: ExplorerFolder? = null, val rows: List<ExplorerEntry> = emptyList(),
    val loading: Boolean = false, val message: String? = null, val focus: String? = null, val focusSerial: Int = 0)

internal class ExplorerViewModel(context: Context) : ViewModel() {
    val storage = ExplorerStorage(context.applicationContext)
    val locations = ExplorerLocations()
    private val mutable = MutableStateFlow(ExplorerState(roots = storage.roots()))
    val state = mutable.asStateFlow()
    private val parents = ArrayList<Pair<ExplorerFolder?, String>>()
    private var loading: Job? = null
    fun focus(key: String) { mutable.value = mutable.value.copy(focus = key) }
    fun restoreFocus() { mutable.value = mutable.value.copy(focusSerial = mutable.value.focusSerial + 1) }
    fun message(value: String?) { mutable.value = mutable.value.copy(message = value) }
    fun authorize(uri: Uri) {
        runCatching { storage.authorize(uri) }.onSuccess {
            mutable.value = mutable.value.copy(roots = storage.roots(), message = "Pasta autorizada.")
            if (mutable.value.folder == null) restoreFocus()
        }.onFailure { message(it.message ?: "Não foi possível autorizar a pasta.") }
    }
    fun open(folder: ExplorerFolder, origin: String = folder.key) {
        parents += mutable.value.folder to origin
        load(folder, null)
    }
    fun open(entry: ExplorerEntry) { open(storage.child(requireNotNull(mutable.value.folder), entry), entry.key) }
    fun back(): Boolean {
        if (parents.isEmpty()) return false
        val (parent, focus) = parents.removeAt(parents.lastIndex)
        load(parent, focus)
        return true
    }
    private fun load(folder: ExplorerFolder?, focus: String?) {
        loading?.cancel()
        mutable.value = mutable.value.copy(folder = folder, rows = emptyList(), loading = folder != null,
            message = null, focus = focus)
        if (folder == null) {
            mutable.value = mutable.value.copy(roots = storage.roots(), focusSerial = mutable.value.focusSerial + 1)
            return
        }
        loading = viewModelScope.launch {
            try {
                val rows = storage.list(folder)
                ensureActive()
                mutable.value = mutable.value.copy(rows = rows, loading = false,
                    message = if (rows.size >= com.pelican.comictv.archive.ExplorerFiles.MAX_ENTRIES) "Mostrando até 2.000 itens desta pasta. Divida pastas maiores para acessar o restante." else null,
                    focusSerial = mutable.value.focusSerial + 1)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutable.value = mutable.value.copy(loading = false,
                message = error.message ?: "Pasta indisponível.", focusSerial = mutable.value.focusSerial + 1) }
        }
    }
}
