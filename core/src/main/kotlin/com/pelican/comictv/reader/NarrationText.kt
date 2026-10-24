package com.pelican.comictv.reader

import java.security.MessageDigest

data class SpeechSegment(val start: Int, val end: Int)

/** Offsets address the unchanged source text and never split a UTF-16 surrogate pair. */
fun speechSegments(text: String, maxChars: Int = 1800): List<SpeechSegment> {
    require(maxChars >= 2)
    val result = mutableListOf<SpeechSegment>()
    var start = 0
    while (start < text.length) {
        while (start < text.length && text[start].isWhitespace()) start++
        if (start == text.length) break
        var end = minOf(start + maxChars, text.length)
        val newline = text.indexOf('\n', start)
        if (newline in start + 1 until end) end = newline
        else if (end < text.length) {
            val space = (end - 1 downTo start + maxChars / 3).firstOrNull { text[it].isWhitespace() }
            if (space != null) end = space
        }
        if (end < text.length && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
        while (end > start && text[end - 1].isWhitespace()) end--
        result += SpeechSegment(start, end)
        start = end
    }
    return result
}

fun narrationDigest(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

enum class SpeechCompletion { Stale, Next, Finished }

class SpeechCursor(private val key: String, private val segments: List<SpeechSegment>, offset: Int = 0) {
    var index: Int = segments.indexOfFirst { offset < it.end }.let { if (it < 0) 0 else it }
        private set
    private var generation = 0L
    private var active: String? = null
    val current: SpeechSegment? get() = segments.getOrNull(index)
    fun begin(): String? {
        if (segments.isEmpty()) return null
        if (index >= segments.size) index = 0
        return "$key:${++generation}:$index".also { active = it }
    }
    fun pause() { generation++; active = null }
    fun move(delta: Int) { pause(); index = (index + delta).coerceIn(0, (segments.size - 1).coerceAtLeast(0)) }
    fun range(id: String, start: Int, end: Int): SpeechSegment? {
        val chunk = current ?: return null
        if (id != active || start < 0 || end <= start || start >= chunk.end - chunk.start) return null
        return SpeechSegment(chunk.start + start, minOf(chunk.start + end, chunk.end))
    }
    fun done(id: String): SpeechCompletion {
        if (id != active || active == null) return SpeechCompletion.Stale
        active = null
        index++
        return if (index < segments.size) SpeechCompletion.Next else SpeechCompletion.Finished
    }
}
