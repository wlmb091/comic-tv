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
    private var importJob: Job? = null
    fun cancelImport() { importJob?.cancel() }
    fun importProtected(context: android.content.Context, uris: List<Uri>, password: CharArray, onImported: (String)->Unit) {
        if (_busy.value) { password.fill('\u0000');return }
        importJob=viewModelScope.launch {
            _busy.value=true;_message.value="Preparando arquivo protegido ou partes…"
            var output:java.io.File?=null
            try {
                output=com.pelican.comictv.explorer.ProtectedArchiveImport.prepare(context,uris,password)
                val result=repository.importReceived(output,"Quadrinho preparado.cbz"){_message.value=it}
                _message.value=result.summary();result.singleId?.let{focusedId=it;onImported(it)}
            } catch(c:CancellationException){_message.value="Importação interrompida.";throw c}
            catch(e:Exception){_message.value=e.message?:"Não foi possível abrir o arquivo."}
            finally{password.fill('\u0000');output?.parentFile?.deleteRecursively();_busy.value=false}
        }
    }
    fun importComic(uri: Uri, onImported: (String) -> Unit) {
        if (_busy.value) return
        importJob = viewModelScope.launch {
            _busy.value = true
            _message.value = null
            try {
                val result = repository.importReceived(uri) { _message.value = it }
                _message.value = result.summary()
                result.singleId?.let { id -> focusedId = id; onImported(id) }
            } catch (e: CancellationException) {
                _message.value = "Importação interrompida. As HQs já concluídas foram mantidas."
                throw e
            }
            catch (e: Exception) { _message.value = e.message ?: "Não foi possível importar este arquivo." }
            finally { _busy.value = false }
        }
    }
}
