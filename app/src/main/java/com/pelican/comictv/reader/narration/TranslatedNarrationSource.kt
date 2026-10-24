package com.pelican.comictv.reader.narration

import com.pelican.comictv.reader.narrationDigest
import com.pelican.comictv.reader.speechSegments
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException

class TranslatedNarrationSource(private val original: NarrationSource) : NarrationSource {
    override val count get() = original.count
    override val label get() = original.label
    private var translator: PageTranslation? = null
    private var identity = ""
    val configured get() = translator != null
    var translated = false
        private set
    override val variant get() = if (translated) "translated:$identity" else "original"
    private val cache = LinkedHashMap<String, String>(4, .75f, true)
    fun configure(value: PageTranslation, key: String) {
        translator?.close(); translator = value; identity = key; translated = false; cache.clear()
    }
    fun useTranslation(value: Boolean) {
        check(!value || translator != null) { "Configure o tradutor no computador primeiro." }
        translated = value
    }
    override suspend fun text(page: Int): String? {
        val enabled = translated
        val engine = translator
        val id = identity
        val text = original.text(page)
        currentCoroutineContext().ensureActive()
        if (!enabled || text.isNullOrBlank()) return text
        val key = "$id:$page:${narrationDigest(text)}"
        cache[key]?.let { return it }
        val output = StringBuilder()
        for (chunk in speechSegments(text)) {
            currentCoroutineContext().ensureActive()
            val result = engine?.translate(text.substring(chunk.start, chunk.end)) ?: throw IOException("Configure o tradutor local.")
            currentCoroutineContext().ensureActive()
            if (output.length.toLong() + result.length + 2 > 120_000) throw IOException("A tradução desta página excedeu o limite de texto.")
            if (output.isNotEmpty()) output.append("\n\n")
            output.append(result)
        }
        currentCoroutineContext().ensureActive()
        if (engine !== translator || id != identity || !translated) throw kotlinx.coroutines.CancellationException("Modo de tradução alterado")
        return output.toString().also {
            cache[key] = it
            while (cache.size > 3 || cache.values.sumOf { value -> value.length.toLong() * 2 } > 16L * 1024 * 1024) cache.remove(cache.keys.first())
        }
    }
    override suspend fun close() { try { translator?.close() } finally { cache.clear(); original.close() } }
}
