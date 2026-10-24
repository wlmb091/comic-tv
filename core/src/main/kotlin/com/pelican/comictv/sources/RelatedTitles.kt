package com.pelican.comictv.sources

import java.text.Normalizer
import java.util.Locale

/** Matches explicit metadata only: never infer credits from descriptions or substrings. */
fun relatedReasons(base: OnlineTitle, candidate: OnlineTitle, sameSource: Boolean): List<String> {
    if (sameSource && base.key == candidate.key) return listOf("Mesma série")
    fun names(value: String?): Map<String, String> = value.orEmpty().take(2000)
        .split(Regex("[,;|\\n]"))
        .map { it.trim() }.filter { it.isNotBlank() && it.lowercase(Locale.ROOT) !in setOf("unknown", "desconhecido", "n/a", "-", "não informado") }
        .associateBy { Normalizer.normalize(it, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT).replace(Regex("\\s+"), " ") }
    fun shared(a: String?, b: String?) = names(a).filterKeys { it in names(b) }.values.firstOrNull()
    return listOfNotNull(
        shared(base.author, candidate.author)?.let { "Escritor: " + it },
        shared(base.artist, candidate.artist)?.let { "Desenhista: " + it },
        shared(base.publisher, candidate.publisher)?.let { "Editora: " + it },
        shared(base.characters, candidate.characters)?.let { "Personagem: " + it },
        shared(base.director, candidate.director)?.let { "Direção: " + it },
    )
}
