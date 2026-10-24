package com.pelican.comictv.reader.narration

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NarrationControllerTest {
    @Test fun pauseDuringBookmarkRestorationCanRecoverThroughExplicitPlay() = runTest {
        val source = Source(listOf("Text ready to speak."))
        val gate = CompletableDeferred<Unit>()
        var restoring = false
        val store = object : NarrationStore {
            override suspend fun load(book: String): NarrationBookmark? {
                if (source.reads.isNotEmpty() && !restoring) { restoring = true; gate.await() }
                return null
            }
            override suspend fun save(book: String, position: NarrationBookmark) = Unit
            override suspend fun settings() = NarrationPreferences()
            override suspend fun saveSettings(settings: NarrationPreferences) = Unit
        }
        val engine = Engine()
        val controller = NarrationController("book", source, engine, store, 0, {}, coroutineContext, StandardTestDispatcher(testScheduler))
        runCurrent(); assertTrue(restoring)
        controller.pause(); gate.complete(Unit); runCurrent()
        controller.play(); advanceUntilIdle()
        assertEquals("Text ready to speak.", controller.state.value.text)
        assertEquals(1, engine.requests.size)
        controller.release(); advanceUntilIdle()
    }
    @Test fun delayedInitializationCannotLoadOrPrefetchAfterPauseButPlayStillWorks() = runTest {
        val source = Source(listOf("First page.", "Second page.", "Third page."))
        val gate = CompletableDeferred<Unit>()
        var initializing = false
        val store = object : NarrationStore {
            override suspend fun load(book: String): NarrationBookmark? = null
            override suspend fun save(book: String, position: NarrationBookmark) = Unit
            override suspend fun settings(): NarrationPreferences { initializing = true; gate.await(); return NarrationPreferences() }
            override suspend fun saveSettings(settings: NarrationPreferences) = Unit
        }
        val engine = Engine()
        val controller = NarrationController("book", source, engine, store, 0, {}, coroutineContext, StandardTestDispatcher(testScheduler))
        runCurrent(); assertTrue(initializing)
        controller.pause(); gate.complete(Unit); advanceUntilIdle()
        assertTrue("Source loaded after pause: ${source.reads}", source.reads.isEmpty())
        assertTrue(engine.requests.isEmpty())
        controller.play(); advanceUntilIdle()
        assertEquals("First page.", controller.state.value.text)
        assertEquals(1, engine.requests.size)
        controller.release(); advanceUntilIdle()
    }
    @Test fun togglingTranslationDoesNotReuseOriginalCacheOrPlayAutomatically() = runTest {
        val original = Source(listOf("Original.\nSecond paragraph."))
        val source = TranslatedNarrationSource(original)
        source.configure(object : PageTranslation {
            override suspend fun translate(text: String) = "Traduzido: $text"
            override fun close() = Unit
        }, "local-en")
        val positions = mutableMapOf<String, NarrationBookmark>()
        val store = object : NarrationStore {
            override suspend fun load(book: String) = positions[book]
            override suspend fun save(book: String, position: NarrationBookmark) { positions[book] = position }
            override suspend fun settings() = NarrationPreferences()
            override suspend fun saveSettings(settings: NarrationPreferences) = Unit
        }
        val engine = Engine()
        val controller = NarrationController("book", source, engine, store, 0, {}, coroutineContext, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        controller.segmentStep(1); advanceUntilIdle()
        val originalStart = controller.state.value.start
        assertTrue(originalStart > 0)
        source.useTranslation(true); controller.refreshText(); advanceUntilIdle()
        assertTrue(controller.state.value.text.startsWith("Traduzido:"))
        assertEquals(0, controller.state.value.start)
        assertTrue(engine.requests.isEmpty())
        controller.pause(); advanceUntilIdle()
        assertTrue(positions.containsKey("book|translated:local-en"))
        source.useTranslation(false); controller.refreshText(); advanceUntilIdle()
        assertEquals("Original.\nSecond paragraph.", controller.state.value.text)
        assertEquals(originalStart, controller.state.value.start)
        assertTrue(engine.requests.isEmpty())
        controller.release(); advanceUntilIdle()
    }
    private class Source(val pages: List<String?>) : NarrationSource {
        override val count get() = pages.size
        override val label = "Página"
        var closed = false
        val reads = mutableListOf<Int>()
        override suspend fun text(page: Int): String? { reads += page; return pages[page] }
        override suspend fun close() { closed = true }
    }
    private class Engine(val available: Boolean = true) : NarrationSpeech {
        override var onEvent: (SpeechEvent) -> Unit = {}
        override var onFocusLost: () -> Unit = {}
        val requests = mutableListOf<Pair<String, String>>()
        var closed = false
        override fun initialize(onReady: (List<NarrationVoice>, String?) -> Unit) {
            onReady(if (available) listOf(NarrationVoice("local", "Português")) else emptyList(), if (available) null else "Sem voz offline")
        }
        override fun speak(text: String, id: String, voice: String, rate: Float): String? { requests += id to text; return null }
        override fun stop() = Unit
        override fun close() { closed = true }
    }
    private class Store : NarrationStore {
        var bookmark: NarrationBookmark? = null
        var prefs = NarrationPreferences()
        override suspend fun load(book: String) = bookmark
        override suspend fun save(book: String, position: NarrationBookmark) { bookmark = position }
        override suspend fun settings() = prefs
        override suspend fun saveSettings(settings: NarrationPreferences) { prefs = settings }
    }
    @Test fun explicitPlayAdvancesButPausedAndOldCallbacksNeverDo() = runTest {
        val source = Source(listOf("Primeiro.\nOutro trecho.", "Segunda página."))
        val engine = Engine()
        val store = Store()
        var page = 0
        val controller = NarrationController("book", source, engine, store, 0, { page = it }, coroutineContext, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        assertTrue(engine.requests.isEmpty())
        controller.play()
        val first = engine.requests.last().first
        controller.pause()
        engine.onEvent(SpeechEvent.Done(first))
        advanceUntilIdle()
        assertEquals(0, page)
        assertEquals(NarrationStatus.Paused, controller.state.value.status)
        controller.play()
        engine.onEvent(SpeechEvent.Done(engine.requests.last().first))
        runCurrent()
        assertEquals("Outro trecho.", engine.requests.last().second)
        engine.onEvent(SpeechEvent.Done(engine.requests.last().first))
        advanceUntilIdle()
        assertEquals(1, page)
        assertEquals("Segunda página.", engine.requests.last().second)
        engine.onFocusLost()
        runCurrent()
        assertEquals(NarrationStatus.Paused, controller.state.value.status)
        controller.release()
        advanceUntilIdle()
        assertTrue(source.closed)
        assertTrue(engine.closed)
        assertEquals(1, store.bookmark!!.page)
        assertTrue(source.reads.size <= 2)
    }
    @Test fun missingVoiceAndImageOnlyPageNeverStartSpeech() = runTest {
        val source = Source(listOf(null, "Texto."))
        val engine = Engine(false)
        val controller = NarrationController("book", source, engine, Store(), 0, {}, coroutineContext, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        controller.play()
        assertTrue(engine.requests.isEmpty())
        controller.pageStep(1)
        advanceUntilIdle()
        assertEquals(NarrationStatus.Unavailable, controller.state.value.status)
        controller.play()
        assertTrue(engine.requests.isEmpty())
        controller.release()
        advanceUntilIdle()
    }
    @Test fun reopeningRestoresSegmentButNotPlaybackAndChangedTextResetsOffset() = runTest {
        val store = Store().apply { bookmark = NarrationBookmark(0, com.pelican.comictv.reader.narrationDigest("Um.\nDois."), 4) }
        val engine = Engine()
        val controller = NarrationController("book", Source(listOf("Um.\nDois.")), engine, store, 0, {}, coroutineContext, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        assertEquals(4, controller.state.value.start)
        assertTrue(engine.requests.isEmpty())
        controller.release(); advanceUntilIdle()
        val changed = NarrationController("book", Source(listOf("Novo texto.")), Engine(), store, 0, {}, coroutineContext, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        assertEquals(0, changed.state.value.start)
        changed.release(); advanceUntilIdle()
    }
}
