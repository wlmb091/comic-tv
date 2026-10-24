package com.pelican.comictv.reader.narration

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice

class AndroidNarrationSpeech(context: Context) : NarrationSpeech {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var engine: TextToSpeech? = null
    private var generation = 0
    @Volatile private var closed = false
    private var voices = emptyMap<String, Voice>()
    private var focus: AudioFocusRequest? = null
    private var hasFocus = false
    override var onEvent: (SpeechEvent) -> Unit = {}
    override var onFocusLost: () -> Unit = {}
    override fun initialize(onReady: (List<NarrationVoice>, String?) -> Unit) {
        if (closed) return
        stop(); engine?.shutdown()
        val expected = ++generation
        val instance = TextToSpeech(app) { status -> handler.post {
            if (closed || expected != generation) return@post
            val current = engine
            if (status != TextToSpeech.SUCCESS || current == null) {
                voices = emptyMap(); onReady(emptyList(), "Não foi possível iniciar a voz do Android. Confira as configurações de voz e tente novamente.")
            } else try {
                val available = current.voices.orEmpty().filter {
                    it.locale.language == "pt" && !it.isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
                }.sortedWith(compareBy<Voice> { if (it.locale.country == "BR") 0 else 1 }.thenByDescending { it.quality }.thenBy { it.name })
                voices = available.associateBy { it.name }
                val labels = available.mapIndexed { index, voice -> NarrationVoice(voice.name,
                    "Português (${if (voice.locale.country == "BR") "Brasil" else voice.locale.displayCountry.ifBlank { "padrão" }}) · Voz ${index + 1}") }
                onReady(labels, if (labels.isEmpty()) "Nenhuma voz offline em português está disponível. Configure uma voz no Android e use Atualizar vozes." else null)
            } catch (_: Exception) { voices = emptyMap(); onReady(emptyList(), "Não foi possível consultar as vozes instaladas.") }
        } }
        engine = instance
        instance.setAudioAttributes(attributes)
        instance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { if (!closed && utteranceId != null) onEvent(SpeechEvent.Done(utteranceId)) }
            @Deprecated("Required by Android")
            override fun onError(utteranceId: String?) { fail(utteranceId) }
            override fun onError(utteranceId: String?, errorCode: Int) { fail(utteranceId) }
            private fun fail(id: String?) { if (!closed && id != null) onEvent(SpeechEvent.Failed(id, "A voz não conseguiu ler o trecho. Confira os dados da voz offline e tente novamente.")) }
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (!closed && utteranceId != null) onEvent(SpeechEvent.Range(utteranceId, start, end))
            }
        })
    }
    override fun speak(text: String, id: String, voice: String, rate: Float): String? {
        val current = engine ?: return "A voz do Android ainda não está pronta."
        val selected = voices[voice] ?: return "A voz selecionada não está disponível offline."
        if (closed) return "A narração foi encerrada."
        if (!hasFocus) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
                .setWillPauseWhenDucked(true).setOnAudioFocusChangeListener({ change ->
                    if (change < 0 && hasFocus) { hasFocus = false; onFocusLost() }
                }, handler).build()
            focus = request
            hasFocus = audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!hasFocus) return "O áudio está sendo usado por outro aplicativo. Tente novamente."
        }
        return try {
            if (current.setVoice(selected) != TextToSpeech.SUCCESS || current.setSpeechRate(rate) != TextToSpeech.SUCCESS)
                "Não foi possível selecionar a voz e a velocidade."
            else if (current.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS)
                "Não foi possível iniciar a fala. Tente outra voz offline."
            else null
        } catch (_: Exception) { "A voz do Android não está disponível neste momento." }
    }
    override fun stop() {
        runCatching { engine?.stop() }
        hasFocus = false
        focus?.let { runCatching { audio.abandonAudioFocusRequest(it) } }
        focus = null
    }
    override fun close() { if (closed) return; closed = true; generation++; stop(); engine?.shutdown(); engine = null; voices = emptyMap() }
}
