package com.pelican.comictv.reader

import android.graphics.BitmapFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelican.comictv.library.ReaderRepository
import com.pelican.comictv.model.PageRef
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class PageImage(val file: File, val width: Int, val height: Int)

class ReaderViewModel(
    private val repository: ReaderRepository,
    private val comicId: String,
    private val saved: SavedStateHandle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pageDimensions: (File) -> Pair<Int, Int> = { file ->
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        options.outWidth to options.outHeight
    },
) : ViewModel() {
    private val _state = MutableStateFlow(ReaderState())
    val state = _state.asStateFlow()
    private val _image = MutableStateFlow<PageImage?>(null)
    val image = _image.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private var pages = emptyList<PageRef>()
    private var loadJob: Job? = null
    private var initializationJob: Job? = null
    private var initialized = false
    private var closing = false
    private var exited = false
    private val writes = Channel<Int>(Channel.CONFLATED)
    private val persistence = Mutex()

    init {
        viewModelScope.launch {
            for (page in writes) {
                try { persistence.withLock { if (!closing && !exited) repository.saveProgress(comicId, page) } }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { _error.value = "Não foi possível salvar o progresso. Tente novamente antes de sair." }
            }
        }
        initialize()
    }

    private fun initialize() {
        initializationJob?.cancel()
        _loading.value = true
        _error.value = null
        initializationJob = viewModelScope.launch {
            try {
                val comic = repository.getComic(comicId)
                pages = repository.pages(comicId)
                _state.value = ReaderState.restore(saved.get<Int>("page") ?: comic.currentPage, pages.size).copy(
                    fit = FitMode.entries.getOrElse(comic.fit) { FitMode.Page }, rtl = comic.rtl)
                initialized = true
                loadPage()
                writes.trySend(_state.value.page)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _error.value = e.message ?: "Não foi possível abrir esta edição."; _loading.value = false }
        }
    }

    fun action(action: ReaderAction) {
        if (closing || exited) return
        val old = _state.value
        val next = old.reduce(action)
        _state.value = next
        if (next.page != old.page) {
            saved["page"] = next.page
            writes.trySend(next.page)
            loadPage()
        }
        if (next.fit != old.fit || next.rtl != old.rtl) viewModelScope.launch {
            try { repository.savePreferences(comicId, next.fit.ordinal, next.rtl) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _error.value = "Não foi possível salvar as opções desta edição." }
        }
    }

    fun retry() { if (!initialized) initialize() else loadPage() }

    private fun loadPage() {
        loadJob?.cancel()
        val expected = _state.value.page
        val page = pages.getOrNull(expected) ?: return
        _image.value = null
        _loading.value = true
        _error.value = null
        loadJob = viewModelScope.launch {
            try {
                val file = repository.materialize(comicId, page)
                val dimensions = withContext(ioDispatcher) { pageDimensions(file) }
                ensureActive()
                if (_state.value.page == expected) _image.value = PageImage(file, dimensions.first, dimensions.second)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (_state.value.page == expected) _error.value = e.message ?: "Não foi possível carregar esta página." }
            finally { if (isActive && _state.value.page == expected) _loading.value = false }
        }
    }

    fun close(onSaved: () -> Unit) {
        if (closing || exited) return
        closing = true
        viewModelScope.launch {
            try {
                if (initialized) {
                    persistence.withLock { repository.saveProgress(comicId, _state.value.page) }
                    repository.savePreferences(comicId, _state.value.fit.ordinal, _state.value.rtl)
                } else {
                    initializationJob?.cancel()
                    loadJob?.cancel()
                }
                exited = true
                onSaved()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { closing = false; _error.value = "Não foi possível salvar. Use Voltar para tentar novamente." }
        }
    }
}
