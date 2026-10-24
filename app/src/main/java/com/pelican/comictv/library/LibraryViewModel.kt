package com.pelican.comictv.library

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class LibraryViewModel(private val repository: LibraryRepository, private val saved: SavedStateHandle) : ViewModel() {
    val browse = LibraryBrowseState(saved)
    val collections = repository.observeCollections().onEach { browse.reconcile(it.map { row -> row.id }.toSet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val memberships = repository.observeMemberships().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val comics = repository.observeComics().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    var focusedId: String?
        get() = saved["focusedId"]
        set(value) { saved["focusedId"] = value }
    fun message(text: String?) { _message.value = text }
    fun importComic(uri: Uri, onImported: (String) -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            try {
                val comic = repository.importComic(uri)
                focusedId = comic.id
                onImported(comic.id)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _message.value = e.message ?: "Não foi possível importar este arquivo." }
            finally { _busy.value = false }
        }
    }
}
