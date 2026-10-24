package com.pelican.comictv.sources

/** Cached edition metadata; reading it never reloads an extension or changes progress. */
data class OnlineEditionInfo(val sourceName: String, val language: String, val title: String,
    val chapter: String, val description: String?)

internal fun OnlineReadingRecord.editionInfo() = OnlineEditionInfo(
    source.name.orEmpty().take(300), source.language.orEmpty().take(30), title.title.orEmpty().take(300),
    chapter.title.orEmpty().take(300), title.description?.trim()?.take(12_000)?.takeIf { it.isNotBlank() },
)
