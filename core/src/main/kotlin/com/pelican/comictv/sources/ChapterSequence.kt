package com.pelican.comictv.sources

data class ChapterSequence(
    val chapters: List<OnlineChapter>,
    val index: Int,
    val previous: OnlineChapter?,
    val next: OnlineChapter?,
)

/** Source order is authoritative; duplicates keep their first entry even when reversed. */
fun chapterSequence(
    chapters: List<OnlineChapter>,
    currentKey: String,
    reversed: Boolean = false,
): ChapterSequence {
    require(chapters.size <= 10_000) { "Lista de edições excede o limite." }
    val unique = chapters.distinctBy { it.key }
    val ordered = if (reversed) unique.reversed() else unique
    val index = ordered.indexOfFirst { it.key == currentKey }
    return ChapterSequence(
        chapters = ordered,
        index = index,
        previous = if (index > 0) ordered[index - 1] else null,
        next = if (index >= 0) ordered.getOrNull(index + 1) else null,
    )
}
