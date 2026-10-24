package com.pelican.comictv.reader.narration

interface NarrationSource {
    val count: Int
    val label: String
    val variant: String get() = "original"
    suspend fun text(page: Int): String?
    suspend fun close()
}

data class NarrationVoice(val id: String, val label: String)
sealed interface SpeechEvent {
    val id: String
    data class Range(override val id: String, val start: Int, val end: Int) : SpeechEvent
    data class Done(override val id: String) : SpeechEvent
    data class Failed(override val id: String, val message: String) : SpeechEvent
}
interface NarrationSpeech {
    var onEvent: (SpeechEvent) -> Unit
    var onFocusLost: () -> Unit
    fun initialize(onReady: (List<NarrationVoice>, String?) -> Unit)
    /** Returns an error, or null after successfully queuing speech and acquiring audio focus. */
    fun speak(text: String, id: String, voice: String, rate: Float): String?
    fun stop()
    fun close()
}
data class NarrationBookmark(val page: Int, val digest: String, val offset: Int)
data class NarrationPreferences(val voiceId: String? = null, val rateIndex: Int = 1)
interface NarrationStore {
    suspend fun load(book: String): NarrationBookmark?
    suspend fun save(book: String, position: NarrationBookmark)
    suspend fun settings(): NarrationPreferences
    suspend fun saveSettings(settings: NarrationPreferences)
}
enum class NarrationStatus { Preparing, Ready, Speaking, Paused, Finished, Error, Unavailable }
data class NarrationUiState(
    val page: Int = 0, val text: String = "", val start: Int = 0, val end: Int = 0,
    val status: NarrationStatus = NarrationStatus.Preparing, val message: String? = null,
    val voices: List<NarrationVoice> = emptyList(), val voiceId: String? = null, val rateIndex: Int = 1,
) {
    val speaking get() = status == NarrationStatus.Speaking
    val rate get() = NARRATION_RATES[rateIndex.coerceIn(NARRATION_RATES.indices)]
}
val NARRATION_RATES = listOf(.75f, 1f, 1.25f, 1.5f)
