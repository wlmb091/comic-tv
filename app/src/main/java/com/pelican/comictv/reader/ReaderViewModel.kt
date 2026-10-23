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

data class PageImage(val file: File, val width: Int, val height: Int, val text: String? = null, val displayed: Boolean = false)
data class TimelinePreview(val page: Int, val file: File? = null, val loading: Boolean = true, val failed: Boolean = false)
data class AutomaticReading(val enabled: Boolean = false, val secondsPerScreen: Int = 10)
private data class AutomaticFrame(val options: AutomaticReading, val page: Int, val mode: ReaderMode,
    val loading: Boolean, val failed: Boolean, val text: Boolean, val ready: Boolean)

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
    private val _previews = MutableStateFlow<List<TimelinePreview>>(emptyList())
    val previews = _previews.asStateFlow()
    private var previewJob: Job? = null
    private var previewGeneration = 0
    private val _automatic = MutableStateFlow(AutomaticReading())
    val automatic = _automatic.asStateFlow()
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
            combine(_automatic, _state, _loading, _error, _image) { options, state, loading, error, image ->
                AutomaticFrame(options, state.page, state.mode, loading, error != null, image?.text != null,
                    image != null && (image.text != null || image.displayed))
            }.distinctUntilChanged().collectLatest { frame ->
                if (!frame.options.enabled) return@collectLatest
                if (frame.failed) { pauseAutomaticReading(); return@collectLatest }
                if (frame.mode != ReaderMode.Reading || frame.loading || !frame.ready || frame.text) return@collectLatest
                delay(frame.options.secondsPerScreen * 1_000L)
                advanceAutomatically(frame.page)
            }
        }
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
        pauseAutomaticReading()
        applyAction(action)
    }

    private fun applyAction(action: ReaderAction) {
        if (closing || exited) return
        val old = _state.value
        val next = old.reduce(action)
        _state.value = next
        if (next.mode != ReaderMode.Timeline || next.timelinePage != old.timelinePage || next.mode != old.mode) updatePreviews()
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

    fun retry() {
        if (closing || exited) return
        if (!initialized) initialize() else loadPage()
    }

    fun startAutomaticReading() {
        if (closing || exited || !initialized || _state.value.pageCount == 0 || _error.value != null) return
        _state.value = _state.value.copy(mode = ReaderMode.Reading, timelinePage = null)
        updatePreviews()
        _automatic.value = _automatic.value.copy(enabled = true)
    }

    fun pauseAutomaticReading() { _automatic.value = _automatic.value.copy(enabled = false) }

    fun imageDisplayed(file: File) {
        val image = _image.value ?: return
        if (image.file == file && image.text == null && !image.displayed) _image.value = image.copy(displayed = true)
    }

    fun imageDisplayFailed(file: File) {
        val image = _image.value ?: return
        if (image.file != file || image.text != null) return
        pauseAutomaticReading()
        _error.value = "Não foi possível decodificar esta página. Use as ferramentas para tentar novamente."
    }

    fun cycleAutomaticInterval() {
        pauseAutomaticReading()
        val choices = listOf(5, 10, 20, 30, 60)
        _automatic.value = AutomaticReading(secondsPerScreen = choices[(choices.indexOf(_automatic.value.secondsPerScreen) + 1) % choices.size])
    }

    fun automaticSectionFinished(expectedPage: Int) {
        if (_image.value?.text == null) return
        advanceAutomatically(expectedPage)
    }

    private fun advanceAutomatically(expectedPage: Int) {
        val state = _state.value
        if (!_automatic.value.enabled || closing || exited || state.mode != ReaderMode.Reading || state.page != expectedPage ||
            _loading.value || _error.value != null || _image.value == null) return
        if (state.page >= state.pageCount - 1) { pauseAutomaticReading(); return }
        applyAction(if (state.rtl) ReaderAction.Left else ReaderAction.Right)
        val next = _state.value.page
        if (next == state.pageCount - 1 && pages.getOrNull(next)?.key?.startsWith("epub:text:") != true) pauseAutomaticReading()
    }

    private fun updatePreviews() {
        previewJob?.cancel()
        val generation = ++previewGeneration
        val cursor = _state.value.timelinePage
        if (_state.value.mode != ReaderMode.Timeline || cursor == null) { _previews.value = emptyList(); return }
        val indices = (cursor - 2..cursor + 2).filter { it in pages.indices }
        _previews.value = indices.map { TimelinePreview(it) }
        previewJob = viewModelScope.launch {
            delay(180)
            for (index in indices.sortedBy { kotlin.math.abs(it - cursor) }) {
                val result = try { TimelinePreview(index, repository.thumbnail(comicId, pages[index]), loading = false) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { TimelinePreview(index, loading = false, failed = true) }
                ensureActive()
                if (generation != previewGeneration || _state.value.mode != ReaderMode.Timeline || _state.value.timelinePage != cursor) return@launch
                _previews.value = _previews.value.map { if (it.page == index) result else it }
            }
        }
    }

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
                val text = if (page.key.startsWith("epub:text:")) withContext(ioDispatcher) { file.readText(Charsets.UTF_8) } else null
                val dimensions = if (text != null) 1 to 1 else withContext(ioDispatcher) { pageDimensions(file) }
                ensureActive()
                if (_state.value.page == expected) _image.value = PageImage(file, dimensions.first, dimensions.second, text)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (_state.value.page == expected) _error.value = e.message ?: "Não foi possível carregar esta página." }
            finally { if (isActive && _state.value.page == expected) _loading.value = false }
        }
    }

    fun close(onSaved: () -> Unit) {
        if (closing || exited) return
        closing = true
        pauseAutomaticReading()
        previewJob?.cancel()
        previewGeneration++
        _previews.value = emptyList()
        viewModelScope.launch {
            try {
                // Navigation may retain the outgoing composable during its animation. Finish
                // every source operation before another screen reloads the extension context.
                initializationJob?.cancelAndJoin()
                loadJob?.cancelAndJoin()
                previewJob?.cancelAndJoin()
                if (initialized) {
                    persistence.withLock { repository.saveProgress(comicId, _state.value.page) }
                    repository.savePreferences(comicId, _state.value.fit.ordinal, _state.value.rtl)
                }
                exited = true
                onSaved()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { closing = false; _error.value = "Não foi possível salvar. Use Voltar para tentar novamente." }
        }
    }
}
