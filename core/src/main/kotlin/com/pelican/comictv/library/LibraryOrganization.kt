package com.pelican.comictv.library

import java.text.Normalizer
import java.util.Locale

fun normalizeLibraryText(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

fun validatedCollectionName(value: String): Pair<String, String> {
    val name = value.trim()
    require(name.codePointCount(0, name.length) in 1..40 && name.none { Character.isISOControl(it) }) {
        "Use um nome de 1 a 40 caracteres, sem quebras de linha."
    }
    val key = normalizeLibraryText(name)
    require(key !in setOf("todas", "favoritos")) { "Escolha outro nome; este é um filtro da biblioteca." }
    return name to key
}

data class LibrarySearchEntry(val id: String, val title: String, val favorite: Boolean,
    val normalizedTitle: String = normalizeLibraryText(title))

fun filterLibrary(entries: List<LibrarySearchEntry>, query: String, filter: String,
    memberships: Map<String, Set<String>> = emptyMap()): List<String> {
    val term = normalizeLibraryText(query)
    return entries.filter { entry -> entry.normalizedTitle.contains(term) && when {
        filter == "favorites" -> entry.favorite
        filter.startsWith("collection:") -> entry.id in memberships[filter.removePrefix("collection:")].orEmpty()
        else -> true
    } }.map { it.id }
}
