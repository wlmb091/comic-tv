package com.pelican.comictv.sources

data class RelatedComic(val libraryId: String?, val source: SourceHandle, val title: OnlineTitle,
    val pin: String, val reasons: List<String>)
data class RelatedResult(val comics: List<RelatedComic>, val message: String)

internal fun selectRelated(currentId: String, candidates: List<RelatedComic>): List<RelatedComic> = candidates
    .filter { it.libraryId != currentId && it.reasons.isNotEmpty() }
    .groupBy { Triple(it.source.packageName, it.source.id, it.title.key) }.values
    .map { group -> group.sortedWith(compareByDescending<RelatedComic> { it.libraryId != null }.thenByDescending { it.reasons.size }).first() }
    .sortedWith(compareByDescending<RelatedComic> { "Mesma série" in it.reasons }
        .thenByDescending { it.reasons.size }.thenByDescending { it.libraryId != null }.thenBy { it.title.title })
    .take(18)
