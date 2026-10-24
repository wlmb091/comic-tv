package com.pelican.comictv.reader.narration

import com.pelican.comictv.reader.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

/** Main-thread orchestration. Speech and source are replaceable in JVM tests. */
class NarrationController(
    private val book: String, val source: NarrationSource, private val speech: NarrationSpeech,
    private val store: NarrationStore, startPage: Int, private val onPage: (Int) -> Unit,
    context: CoroutineContext = Dispatchers.Main.immediate,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(context + SupervisorJob(context[Job]))
    private val _state = MutableStateFlow(NarrationUiState(page = startPage))
    val state = _state.asStateFlow()
    private var closed = false
    private var generation = 0L
    private var cursorEpoch = 0L
    private var voiceEpoch = 0L
    private var engineReady = false
    private var voiceError: String? = null
    private var loaded = false
    private var digest = ""
    private var positionBook = if (source.variant == "original") book else "$book|${source.variant}"
    private var cursor: SpeechCursor? = null
    private var loadJob: Job? = null
    private var prefetchJob: Job? = null
    private val cache = LinkedHashMap<Int, String?>(4, .75f, true)
    private val persistence = Mutex()
    private val writes = linkedSetOf<Job>()

    init {
        speech.onEvent = { event -> scope.launch { receive(event) } }
        speech.onFocusLost = { scope.launch { pause() } }
        val initialGeneration = generation
        scope.launch {
            try {
                val prefs = store.settings()
                _state.update { it.copy(voiceId = prefs.voiceId, rateIndex = prefs.rateIndex.coerceIn(NARRATION_RATES.indices)) }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { _state.update { it.copy(message = "Não foi possível recuperar o ponto da narração.") } }
            if (!closed) {
                initializeVoices()
                if (initialGeneration == generation) loadPage(startPage.coerceIn(0, (source.count - 1).coerceAtLeast(0)), restore = true)
            }
        }
    }

    fun retryVoices() { pause(); initializeVoices() }
    private fun initializeVoices() {
        val expected = ++voiceEpoch
        engineReady = false
        speech.initialize { voices, error -> scope.launch {
            if (closed || expected != voiceEpoch) return@launch
            engineReady = true
            voiceError = error
            val chosen = voices.firstOrNull { it.id == _state.value.voiceId } ?: voices.firstOrNull()
            _state.update { it.copy(voices = voices, voiceId = chosen?.id,
                status = if (loaded && it.text.isNotBlank()) { if (voices.isEmpty()) NarrationStatus.Unavailable else NarrationStatus.Ready } else it.status,
                message = error ?: it.message) }
        } }
    }
    private fun availableStatus() = if (_state.value.voices.isEmpty()) NarrationStatus.Unavailable else NarrationStatus.Ready

    private fun loadPage(page: Int, autoplay: Boolean = false, restore: Boolean = false) {
        if (closed || page !in 0 until source.count) return
        val previous = loadJob
        val previousPrefetch = prefetchJob
        previous?.cancel(); previousPrefetch?.cancel()
        speech.stop(); cursor?.pause()
        val expected = ++generation
        loaded = false
        _state.update { it.copy(page = page, text = "", start = 0, end = 0, status = NarrationStatus.Preparing, message = "Preparando texto…") }
        onPage(page)
        loadJob = scope.launch {
            previous?.join(); previousPrefetch?.join()
            try {
                val text = cachedText(page).orEmpty()
                val hash = withContext(io) { narrationDigest(text) }
                ensureActive()
                if (closed || expected != generation) return@launch
                val restored = if (restore) store.load(positionBook) else null
                ensureActive()
                if (closed || expected != generation) return@launch
                val offset = restored?.takeIf { it.page == page && it.digest == hash }?.offset ?: 0
                cursor = SpeechCursor("${++cursorEpoch}:$page", speechSegments(text), offset)
                digest = hash
                loaded = true
                val chunk = cursor?.current
                _state.update { it.copy(text = text, start = chunk?.start ?: 0, end = chunk?.end ?: 0,
                    status = when { text.isBlank() -> NarrationStatus.Paused; !engineReady -> NarrationStatus.Preparing; else -> availableStatus() },
                    message = if (text.isBlank()) "Esta página não tem texto extraível. Páginas digitalizadas precisam de OCR, ainda não disponível. Use Próxima página ou volte à imagem."
                        else if (!engineReady) "Preparando a voz do Android…" else voiceError) }
                if (autoplay && text.isNotBlank()) play()
                warm(page, expected)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (expected == generation && !closed) _state.update { it.copy(status = NarrationStatus.Error, message = e.message ?: "Não foi possível preparar o texto.") }
            }
        }
    }
    private suspend fun cachedText(page: Int): String? {
        if (cache.containsKey(page)) return cache[page]
        val text = source.text(page)
        currentCoroutineContext().ensureActive()
        require((text?.length ?: 0) <= 120_000) { "Esta página excede o limite de texto para narração." }
        cache[page] = text
        while (cache.size > 3 || cache.values.sumOf { (it?.length ?: 0).toLong() * 2 } > 16L * 1024 * 1024) cache.remove(cache.keys.first())
        return text
    }
    private fun warm(page: Int, expected: Long) {
        prefetchJob = scope.launch {
            for (next in page + 1..minOf(page + 2, source.count - 1)) {
                if (closed || expected != generation) break
                try { cachedText(next) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            }
        }
    }

    fun play() {
        if (closed) return
        if (!loaded) { loadPage(_state.value.page, autoplay = true, restore = true); return }
        val old = _state.value
        if (old.text.isBlank()) return
        if (!engineReady || old.voiceId == null || old.voices.isEmpty()) {
            _state.update { it.copy(status = if (engineReady) NarrationStatus.Unavailable else NarrationStatus.Preparing,
                message = voiceError ?: "Aguarde uma voz offline em português e tente novamente.") }
            return
        }
        if (cursor?.current == null) cursor = SpeechCursor("${++cursorEpoch}:${old.page}", speechSegments(old.text))
        speakCurrent()
    }
    private fun speakCurrent() {
        val value = _state.value
        val chunk = cursor?.current ?: return
        val id = cursor?.begin() ?: return
        _state.update { it.copy(start = chunk.start, end = chunk.end, status = NarrationStatus.Speaking, message = null) }
        savePosition()
        val error = speech.speak(value.text.substring(chunk.start, chunk.end), id, value.voiceId ?: return, value.rate)
        if (error != null) { cursor?.pause(); speech.stop(); _state.update { it.copy(status = NarrationStatus.Error, message = error) } }
    }
    private fun receive(event: SpeechEvent) {
        if (closed || !_state.value.speaking) return
        when (event) {
            is SpeechEvent.Range -> cursor?.range(event.id, event.start, event.end)?.let { range -> _state.update { it.copy(start = range.start, end = range.end) } }
            is SpeechEvent.Failed -> if (cursor?.range(event.id, 0, 1) != null) {
                pause(); _state.update { it.copy(status = NarrationStatus.Error, message = event.message) }
            }
            is SpeechEvent.Done -> when (cursor?.done(event.id)) {
                SpeechCompletion.Next -> speakCurrent()
                SpeechCompletion.Finished -> {
                    savePosition()
                    if (_state.value.page + 1 < source.count) loadPage(_state.value.page + 1, autoplay = true)
                    else { speech.stop(); _state.update { it.copy(status = NarrationStatus.Finished, message = "Narração concluída.") } }
                }
                else -> Unit
            }
        }
    }
    fun pause() {
        if (closed) return
        generation++
        loadJob?.cancel(); prefetchJob?.cancel()
        cursor?.pause(); speech.stop()
        _state.update { it.copy(status = if (it.status == NarrationStatus.Unavailable) it.status else NarrationStatus.Paused) }
        savePosition()
    }
    fun stop() {
        pause()
        cursor = SpeechCursor("${++cursorEpoch}:${_state.value.page}", speechSegments(_state.value.text))
        updateRange(); savePosition()
    }
    fun segmentStep(delta: Int) { pause(); cursor?.move(delta); updateRange(); savePosition() }
    fun pageStep(delta: Int) {
        pause()
        val next = _state.value.page + delta
        if (next in 0 until source.count) loadPage(next)
        else _state.update { it.copy(message = if (delta < 0) "Início do livro." else "Fim do livro.") }
    }
    fun refreshText() {
        pause()
        positionBook = if (source.variant == "original") book else "$book|${source.variant}"
        cache.clear()
        loadPage(_state.value.page, restore = true)
    }
    private fun updateRange() { cursor?.current?.let { chunk -> _state.update { it.copy(start = chunk.start, end = chunk.end) } } }
    fun cycleRate() { pause(); _state.update { it.copy(rateIndex = (it.rateIndex + 1) % NARRATION_RATES.size) }; saveSettings() }
    fun cycleVoice() {
        pause()
        val voices = _state.value.voices
        if (voices.isEmpty()) return
        val index = voices.indexOfFirst { it.id == _state.value.voiceId }
        _state.update { it.copy(voiceId = voices[(index + 1) % voices.size].id) }
        saveSettings()
    }
    private fun snapshot(): NarrationBookmark? = if (!loaded || _state.value.text.isBlank()) null
        else NarrationBookmark(_state.value.page, digest, cursor?.current?.start ?: _state.value.start)
    private fun write(block: suspend () -> Unit) {
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { persistence.withLock { block() } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (!closed) _state.update { it.copy(message = "Não foi possível salvar os ajustes da narração. Tente novamente antes de sair.") } }
        }
        synchronized(writes) { writes += job }
        job.invokeOnCompletion { synchronized(writes) { writes -= job } }
    }
    private fun savePosition() { snapshot()?.let { value -> val key = positionBook; write { store.save(key, value) } } }
    private fun saveSettings() { val prefs = NarrationPreferences(_state.value.voiceId, _state.value.rateIndex); write { store.saveSettings(prefs) } }
    suspend fun prepareExit(): Boolean {
        pause()
        synchronized(writes) { writes.toList() }.joinAll()
        return try { snapshot()?.let { value -> persistence.withLock { store.save(positionBook, value) } }; true }
        catch (_: Exception) { _state.update { it.copy(message = "Não foi possível salvar o ponto de narração. Tente Voltar novamente.") }; false }
    }
    fun release() {
        if (closed) return
        pause(); closed = true; voiceEpoch++
        speech.close()
        val pending = synchronized(writes) { writes.toList() }
        val loading = loadJob; val prefetch = prefetchJob
        scope.launch(NonCancellable) {
            try { loading?.cancelAndJoin(); prefetch?.cancelAndJoin(); pending.joinAll(); source.close() }
            catch (_: Exception) { /* Closing must never take down the visual reader. */ }
            finally { cache.clear(); scope.cancel() }
        }
    }
}
