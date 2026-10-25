package com.pelican.comictv.sources

import java.util.Locale

private fun languageKey(value: String) = value.trim().replace('_','-').lowercase(Locale.ROOT)
fun sourceLanguages(sources: List<SourceHandle>): List<String> = sources.map { languageKey(it.language) }.distinct().sorted()
fun filterSources(sources: List<SourceHandle>, query: String, language: String): List<SourceHandle> = sources.filter {
    (language.isBlank() || languageKey(it.language) == languageKey(language)) &&
        (query.isBlank() || it.name.contains(query.trim(),true) || it.language.contains(query.trim(),true) || sourceLanguageLabel(it.language).contains(query.trim(),true))
}
fun sourceLanguageLabel(code: String): String = when(languageKey(code)) {
    "" -> "Todos os idiomas"
    "all", "multi" -> "Vários idiomas"
    else -> Locale.forLanguageTag(code.replace('_','-')).getDisplayName(Locale.forLanguageTag("pt-BR")).ifBlank {code}
}
