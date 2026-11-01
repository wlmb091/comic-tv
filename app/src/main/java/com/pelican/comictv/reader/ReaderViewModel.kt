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
import com.pelican.comictv.performance.AppTimings
import com.pelican.comictv.performance.LoadStage

data class PageImage(val file: File, val width: Int, val height: Int, val text: String? = null, val displayed: Boolean = false)
data class TimelinePreview(val page: Int, val file: File? = null, val loading: Boolean = true, val failed: Boolean = false)
data class AutomaticReading(val enabled: Boolean = false, val secondsPerScreen: Int = 10)
private data class ReadingImages(val current: PageImage?, val partner: PageImage?, val layout: ReaderLayout)
private data class AutomaticFrame(val options: AutomaticReading, val page: Int, val mode: ReaderMode,
    val loading: Boolean, val failed: Boolean, val text: Boolean, val ready: Boolean)

class ReaderViewModel(
    private val repository: ReaderRepository,
    private val comicId: String,
    private val saved: SavedStateHandle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val thumbnailWarm: suspend (File) -> Unit = {},
    private val previewFileValid: (File) -> Boolean = { it.isFile },
    private val imagePreferences: ReaderImageStore = object : ReaderImageStore {},
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
    private val _bookError = MutableStateFlow<String?>(null)
    val error = combine(_error, _bookError) { main, partner -> main ?: partner }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _layout = MutableStateFlow(ReaderLayout.restore(saved.get<String>("layout"), saved.get<Boolean>("gallery") ?: false))
    val layout = _layout.asStateFlow()
    private val _bookPartner = MutableStateFlow<PageImage?>(null)
    val bookPartner = _bookPartner.asStateFlow()
    private val _previews = MutableStateFlow<List<TimelinePreview>>(emptyList())
    val previews = _previews.asStateFlow()
    private val _imageOptions = MutableStateFlow(imagePreferences.read(comicId))
    val imageOptions = _imageOptions.asStateFlow()
    private val _compareOriginal = MutableStateFlow(false)
    val compareOriginal = _compareOriginal.asStateFlow()
    private val imageWrites = Mutex()
    private val _anticipated = MutableStateFlow<List<PageImage>>(emptyList())
    val anticipated = _anticipated.asStateFlow()
    private var prefetchJob: Job? = null
    private var activePrefetch: Pair<Job, Int>? = null
    private val thumbnailFiles = LinkedHashMap<Int, File>(32, .75f, true)
    private var previousPreviewCursor: Int? = null
    private var previewJob: Job? = null
    private var previewGeneration = 0
    private val _automatic = MutableStateFlow(AutomaticReading())
    val automatic = _automatic.asStateFlow()
    private var pages = emptyList<PageRef>()
    private val sourceLoads = linkedSetOf<Job>()
    private fun launchLoad(block: suspend CoroutineScope.() -> Unit): Job {
        val job = viewModelScope.launch(block = block)
        synchronized(sourceLoads) { sourceLoads += job }
        job.invokeOnCompletion { synchronized(sourceLoads) { sourceLoads -= job } }
        return job
    }
    private var loadJob: Job? = null
    private var imageReadyNanos: Long? = null
    private var initializationJob: Job? = null
    private var initialized = false
    private var closing = false
    private var exited = false
    private var narrationActive = false
    internal val narrationBookId get() = comicId
    internal fun canNarrateBook() = pages.any { it.key.startsWith("epub:text:") || it.key.startsWith("pdf:") }
    internal fun narrationIsPdf() = pages.any { it.key.startsWith("pdf:") }
    internal suspend fun narrationText(index: Int): String? {
        val page = pages.getOrNull(index)?.takeIf { it.key.startsWith("epub:text:") } ?: return null
        val file = repository.prefetch(comicId, page)
        return withContext(ioDispatcher) {
            runInterruptible { require(file.length() <= 480_000) { "Texto da seção excede o limite." }; file.readText(Charsets.UTF_8) }
        }
    }
    internal fun beginNarration() {
        narrationActive = true
        pauseAutomaticReading(); prefetchJob?.cancel(); previewJob?.cancel(); loadJob?.cancel()
        previewGeneration++
        _anticipated.value = emptyList(); _previews.value = emptyList(); _bookPartner.value = null
        _state.value = _state.value.copy(mode = ReaderMode.Reading, timelinePage = null)
    }
    internal fun narrationSelectPage(page: Int) {
        if (!narrationActive || closing || exited || page !in pages.indices) return
        _state.value = _state.value.copy(page = page, mode = ReaderMode.Reading, timelinePage = null, zoom = 1f, panX = 0f, panY = 0f)
        saved["page"] = page
        _image.value = null
        writes.trySend(page)
    }
    internal fun finishNarration() {
        narrationActive = false
        if (!closing && !exited) { loadPage(); updatePreviews() }
    }
    private val writes = Channel<Int>(Channel.CONFLATED)
    private val persistence = Mutex()

    init {
        viewModelScope.launch {
            val images = combine(_image, _bookPartner, _layout) { current, partner, layout -> ReadingImages(current, partner, layout) }
            combine(_automatic, _state, _loading, error, images) { options, state, loading, error, images ->
                val image = images.current
                AutomaticFrame(options, state.page, state.mode, loading, error != null, image?.text != null,
                    image != null && (image.text != null || image.displayed) &&
                        (images.layout != ReaderLayout.Book || !hasBookPartner(state.page) || images.partner?.displayed == true))
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
        initializationJob = launchLoad {
            try {
                val comic = repository.getComic(comicId)
                pages = repository.pages(comicId)
                _state.value = ReaderState.restore(saved.get<Int>("page") ?: comic.currentPage, pages.size).copy(
                    fit = FitMode.entries.getOrElse(comic.fit) { FitMode.Page }, rtl = comic.rtl)
                initialized = true
                loadPage()
                updatePreviews()
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
        val next = if (_layout.value == ReaderLayout.Book && old.mode == ReaderMode.Reading &&
            pages.getOrNull(old.page)?.key?.startsWith("epub:text:") == false && (action == ReaderAction.Left || action == ReaderAction.Right)) {
            val forward = (action == ReaderAction.Right) != old.rtl
            val target = if (forward) old.page + if (hasBookPartner(old.page)) 2 else 1
                else (old.page - if (old.page >= 2 && pages[old.page - 1].key.startsWith("epub:text:").not() && pages[old.page - 2].key.startsWith("epub:text:").not()) 2 else 1).coerceAtLeast(0)
            if (target in pages.indices && target != old.page) old.turnPage(target) else old
        } else old.reduce(action)
        applyState(old, next)
    }

    /** Explicit phone controls never reinterpret arrows through the physical remote's mode. */
    internal val companionComicId get() = comicId
    internal fun companionAvailable() = initialized && !closing && !exited && !narrationActive && pages.isNotEmpty()
    internal fun companionPan(x:Float,y:Float) {
        check(companionAvailable()){ "Leitor indisponível para controle." }
        require(x.isFinite()&&y.isFinite())
        pauseAutomaticReading()
        val old=_state.value
        _state.value=if(old.zoom<=1f)old.copy(panX=0f,panY=0f) else old.copy(
            panX=(old.panX+x).coerceIn(-1f,1f),panY=(old.panY+y).coerceIn(-1f,1f))
    }
    internal fun companionZoom(zoom:Float) {
        check(companionAvailable()){ "Leitor indisponível para controle." };require(zoom.isFinite())
        pauseAutomaticReading()
        val old=_state.value;val value=zoom.coerceIn(1f,3f)
        _state.value=old.copy(zoom=value,panX=if(value==1f)0f else old.panX,panY=if(value==1f)0f else old.panY)
    }
    /** How much of the view the shown pages fill at zoom 1; published by the renderer that measures them. */
    @Volatile private var pageFill=1f to 1f
    internal fun reportPageFill(width:Float,height:Float){if(width>0f&&height>0f)pageFill=width to height}
    /** Phone pinch: the page point under the fingers stays there while they zoom and move. */
    internal fun companionPinch(zoom:Float,x:Float,y:Float,dx:Float,dy:Float) {
        check(companionAvailable()){ "Leitor indisponível para controle." };require(zoom.isFinite()&&x.isFinite()&&y.isFinite()&&dx.isFinite()&&dy.isFinite())
        pauseAutomaticReading()
        val old=_state.value;val value=zoom.coerceIn(1f,3f);val (fillX,fillY)=pageFill
        _state.value=old.copy(zoom=value,panX=pinchPan(fillX,old.zoom,value,old.panX,x-dx,x),panY=pinchPan(fillY,old.zoom,value,old.panY,y-dy,y))
    }
    internal fun companionPage(page:Int) {
        check(companionAvailable()){ "Leitor indisponível para controle." };require(page in pages.indices){ "Página não encontrada." }
        pauseAutomaticReading();val old=_state.value
        applyState(old,old.turnPage(page))
    }
    internal fun companionTurn(delta:Int) {
        check(companionAvailable()){ "Leitor indisponível para controle." };require(delta==-1||delta==1)
        val old=_state.value
        val book=_layout.value==ReaderLayout.Book
        val step=if(delta>0){if(book&&hasBookPartner(old.page))2 else 1}
            else if(book&&old.page>=2&&pages[old.page-1].key.startsWith("epub:text:").not()&&pages[old.page-2].key.startsWith("epub:text:").not())2 else 1
        companionPage((old.page+delta*step).coerceIn(0,pages.lastIndex))
    }
    private fun applyState(old:ReaderState,next:ReaderState) {
        _state.value = next
        if (next.page != old.page) {
            saved["page"] = next.page
            writes.trySend(next.page)
            val keep = activePrefetch?.let { (owner, index) -> owner === prefetchJob && owner.isActive && index == next.page } == true
            if (!keep) prefetchJob?.cancel()
            _anticipated.value = emptyList()
            // Clear the previous displayed image before previews can start another warm window.
            loadPage()
        }
        if (next.page != old.page || next.timelinePage != old.timelinePage || next.mode != old.mode) updatePreviews()
        if (next.fit != old.fit || next.rtl != old.rtl) viewModelScope.launch {
            try { repository.savePreferences(comicId, next.fit.ordinal, next.rtl) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _error.value = "Não foi possível salvar as opções desta edição." }
        }
    }

    fun cycleLayout() {
        if (closing || exited) return
        pauseAutomaticReading()
        _layout.value = _layout.value.next()
        saved["layout"] = _layout.value.name
        _state.value = _state.value.copy(mode = if (_state.value.mode == ReaderMode.Tools) ReaderMode.Tools else ReaderMode.Reading, fit = FitMode.Page, zoom = 1f, panX = 0f, panY = 0f)
        updatePreviews()
    }

    internal fun hasBookPartner(page: Int) = pages.getOrNull(page)?.key?.startsWith("epub:text:") == false &&
        pages.getOrNull(page + 1)?.key?.startsWith("epub:text:") == false

    fun bookPageDisplayed(file: File) {
        val partner = _bookPartner.value ?: return
        if (partner.file == file && !partner.displayed) _bookPartner.value = partner.copy(displayed = true)
    }

    fun bookPageFailed(file: File) {
        if (_bookPartner.value?.file == file) {
            pauseAutomaticReading()
            _bookError.value = "Não foi possível exibir a segunda página. Tente novamente nas ferramentas."
        }
    }

    fun retry() {
        if (closing || exited) return
        if (!initialized) initialize() else { loadPage(); updatePreviews() }
    }

    fun startAutomaticReading() {
        if (narrationActive || closing || exited || !initialized || _state.value.pageCount == 0 || _error.value != null || _bookError.value != null) return
        _state.value = _state.value.copy(mode = ReaderMode.Reading, timelinePage = null)
        updatePreviews()
        _automatic.value = _automatic.value.copy(enabled = true)
    }

    fun pauseAutomaticReading() { _automatic.value = _automatic.value.copy(enabled = false) }

    fun imageDisplayed(file: File) {
        val image = _image.value ?: return
        if (image.file == file && image.text == null && !image.displayed) {
            imageReadyNanos?.let { AppTimings.finish(LoadStage.PageDisplay, it) }
            imageReadyNanos = null
            _image.value = image.copy(displayed = true)
            warmReadingWindow()
        }
    }

    fun cycleImagePreset() {
        if (closing || exited) return
        _compareOriginal.value = false
        val old = _imageOptions.value
        _imageOptions.value = old.copy(preset = ImagePreset.entries[(old.preset.ordinal + 1) % ImagePreset.entries.size])
        saveImageOptions()
    }
    fun cycleImageIntensity() {
        if (closing || exited) return
        _compareOriginal.value = false
        _imageOptions.value = _imageOptions.value.copy(intensity = (_imageOptions.value.intensity + 1) % 3)
        saveImageOptions()
    }
    fun toggleOriginalComparison() { if (!closing && !exited) _compareOriginal.value = !_compareOriginal.value }
    private fun saveImageOptions() {
        viewModelScope.launch {
            try { imageWrites.withLock { imagePreferences.save(comicId, _imageOptions.value) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _error.value = "Não foi possível salvar o filtro desta edição." }
        }
    }
    private fun cachedThumbnail(index: Int): File? {
        val file = thumbnailFiles[index] ?: return null
        if (previewFileValid(file)) return file
        thumbnailFiles.remove(index)
        return null
    }
    private suspend fun thumbnail(index: Int): File {
        cachedThumbnail(index)?.let { return it }
        val file = repository.thumbnail(comicId, pages[index])
        currentCoroutineContext().ensureActive()
        thumbnailFiles[index] = file
        while (thumbnailFiles.size > 64) thumbnailFiles.remove(thumbnailFiles.keys.first())
        try { thumbnailWarm(file) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        return file
    }
    fun pauseAnticipation() { prefetchJob?.cancel() }
    fun resumeAnticipation() { if (_image.value?.displayed == true) warmReadingWindow() }
    private fun warmReadingWindow() {
        prefetchJob?.cancel()
        if (narrationActive || closing || exited || _state.value.mode == ReaderMode.Timeline) return
        val expected = _state.value.page
        val next = expected + if (_layout.value == ReaderLayout.Book && hasBookPartner(expected)) 2 else 1
        prefetchJob = launchLoad {
            delay(150)
            val owner = currentCoroutineContext().job
            val warmed = mutableSetOf<Int>()
            // The visible page is already local: its timeline thumbnail must not wait for remote future pages.
            try { thumbnail(expected); warmed += expected } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            for (index in next..next + 1) {
                if (_state.value.page != expected) return@launchLoad
                if (index !in pages.indices || pages[index].key.startsWith("epub:text:")) continue
                try {
                    activePrefetch = owner to index
                    val file = try { repository.prefetch(comicId, pages[index]) }
                        finally { if (activePrefetch?.first === owner) activePrefetch = null }
                    val dimensions = withContext(ioDispatcher) { pageDimensions(file) }
                    ensureActive()
                    if (_state.value.page != expected) return@launchLoad
                    _anticipated.value = (_anticipated.value.filterNot { it.file == file } + PageImage(file, dimensions.first, dimensions.second)).takeLast(2)
                    try {thumbnail(index); warmed += index} catch(e: CancellationException) {throw e} catch(_: Exception) { }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            }
            for (index in (expected - 2..expected + 4).filter { it in pages.indices && it !in warmed }.sortedBy { kotlin.math.abs(it - expected) }) {
                if (_state.value.page != expected) return@launchLoad
                try { thumbnail(index) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            }
        }
    }

    fun imageDisplayFailed(file: File) {
        val image = _image.value ?: return
        if (image.file != file || image.text != null) return
        imageReadyNanos?.let { AppTimings.finish(LoadStage.PageDisplay, it, failed = true) }
        imageReadyNanos = null
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
            _loading.value || _error.value != null || _bookError.value != null || _image.value == null) return
        if (state.page >= state.pageCount - 1) { pauseAutomaticReading(); return }
        applyAction(if (state.rtl) ReaderAction.Left else ReaderAction.Right)
        val next = _state.value.page
        if ((next == state.page || next + (if (_layout.value == ReaderLayout.Book && hasBookPartner(next)) 1 else 0) == state.pageCount - 1) && pages.getOrNull(next)?.key?.startsWith("epub:text:") != true) pauseAutomaticReading()
    }

    private fun updatePreviews() {
        previewJob?.cancel()
        val generation = ++previewGeneration
        _bookPartner.value = null
        _bookError.value = null
        val timeline = _state.value.mode == ReaderMode.Timeline
        if (timeline) prefetchJob?.cancel()
        else if (_image.value?.displayed == true) warmReadingWindow()
        if (!timeline && _layout.value == ReaderLayout.Book) {
            _previews.value = emptyList()
            val partnerIndex = _state.value.page + 1
            if (!hasBookPartner(_state.value.page)) return
            previewJob = launchLoad {
                try {
                    val file = repository.prefetch(comicId, pages[partnerIndex])
                    val dimensions = withContext(ioDispatcher) { pageDimensions(file) }
                    ensureActive()
                    if (generation == previewGeneration) _bookPartner.value = PageImage(file, dimensions.first, dimensions.second)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { if (generation == previewGeneration) {
                    pauseAutomaticReading()
                    _bookError.value = "Não foi possível carregar a segunda página. Tente novamente nas ferramentas."
                } }
            }
            return
        }
        val cursor = if (timeline) _state.value.timelinePage else if (_layout.value == ReaderLayout.Gallery) _state.value.page else null
        if (cursor == null) { _previews.value = emptyList(); return }
        val indices = if (timeline) (cursor - 2..cursor + 2).filter { it in pages.indices }
            else listOf(cursor - 1, cursor + 1).filter { it in pages.indices && !pages[it].key.startsWith("epub:text:") }
        val direction = if (previousPreviewCursor?.let { cursor < it } == true) -1 else 1
        previousPreviewCursor = cursor
        _previews.value = indices.map { index -> if (timeline) cachedThumbnail(index)?.let { TimelinePreview(index, it, loading = false) } ?: TimelinePreview(index) else TimelinePreview(index) }
        previewJob = launchLoad {
            delay(180)
            for (index in indices.sortedBy { kotlin.math.abs(it - cursor) }) {
                val result = try { TimelinePreview(index, if (timeline) thumbnail(index) else repository.prefetch(comicId, pages[index]), loading = false) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { TimelinePreview(index, loading = false, failed = true) }
                ensureActive()
                if (generation != previewGeneration) return@launchLoad
                _previews.value = _previews.value.map { if (it.page == index) result else it }
            }
            if (timeline) for (index in listOf(cursor + direction * 3, cursor + direction * 4).filter { it in pages.indices }) {
                try { thumbnail(index) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            }
        }
    }

    private fun loadPage() {
        if (narrationActive) return
        loadJob?.cancel()
        val expected = _state.value.page
        val page = pages.getOrNull(expected) ?: return
        imageReadyNanos = null
        _image.value = null
        _loading.value = true
        _error.value = null
        loadJob = launchLoad {
            try {
                val file = repository.materialize(comicId, page)
                val prepared = AppTimings.measure(LoadStage.PagePrepare) {
                    val text = if (page.key.startsWith("epub:text:")) withContext(ioDispatcher) { file.readText(Charsets.UTF_8) } else null
                    val dimensions = if (text != null) 1 to 1 else withContext(ioDispatcher) { pageDimensions(file) }
                    PageImage(file, dimensions.first, dimensions.second, text)
                }
                ensureActive()
                if (_state.value.page == expected) {
                    imageReadyNanos = if (prepared.text == null) AppTimings.mark() else null
                    _image.value = prepared
                }
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
        prefetchJob?.cancel()
        _anticipated.value = emptyList()
        previewGeneration++
        _previews.value = emptyList()
        viewModelScope.launch {
            try {
                // Navigation may retain the outgoing composable during its animation. Finish
                // every source operation before another screen reloads the extension context.
                while (true) {
                    val pending = synchronized(sourceLoads) { sourceLoads.filterNot { it.isCompleted } }
                    if (pending.isEmpty()) break
                    pending.forEach { it.cancel() }
                    pending.joinAll()
                }
                initializationJob?.cancelAndJoin()
                loadJob?.cancelAndJoin()
                previewJob?.cancelAndJoin()
                prefetchJob?.cancelAndJoin()
                imageWrites.withLock { imagePreferences.save(comicId, _imageOptions.value) }
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
