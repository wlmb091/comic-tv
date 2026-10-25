package com.pelican.comictv.sources

enum class ChapterOrder(val label: String) {
    Ascending("Do início"), Descending("Do final"), Source("Ordem da fonte")
}

fun restoreChapterOrder(name: String?): ChapterOrder = ChapterOrder.entries.firstOrNull { it.name == name } ?: ChapterOrder.Ascending

private val chapterNumber = Regex("(?:\\b(?:chapter|cap[ií]tulo|ch\\.?|cap\\.?)\\s*|#\\s*)([0-9]+(?:[.,][0-9]+)?)",RegexOption.IGNORE_CASE)
private fun orderNumber(chapter: OnlineChapter): Float? {
    val title=chapter.title.trim()
    val hint=(chapterNumber.find(title)?.groupValues?.get(1)?.replace(',','.')?.toFloatOrNull()
        ?: title.replace(',','.').toFloatOrNull())?.takeIf {it.isFinite() && it >= 0}
    val provided=chapter.number.takeIf {it.isFinite() && it >= 0}
    // Zero is also the transport default. Without a numeric title it cannot identify an actual chapter 0.
    return if (provided != null && provided > 0f) provided else hint
}

/** Stable numeric order, original chapter objects retained; unknown extras stay last in both directions. */
fun orderedChapters(chapters: List<OnlineChapter>, order: ChapterOrder): List<OnlineChapter> {
    val unique=chapters.distinctBy {it.key}
    if (order == ChapterOrder.Source) return unique
    return unique.map {it to orderNumber(it)}.sortedWith(Comparator {a,b ->
        val left=a.second;val right=b.second
        when {
            left == null && right == null -> 0
            left == null -> 1
            right == null -> -1
            order == ChapterOrder.Ascending -> left.compareTo(right)
            else -> right.compareTo(left)
        }
    }).map {it.first}
}
