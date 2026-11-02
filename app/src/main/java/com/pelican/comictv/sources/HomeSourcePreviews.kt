package com.pelican.comictv.sources

/** Most titles per row the phone panel can list through "Ver mais"; keeps refs short and memory bounded. */
internal const val COMPANION_MORE_LIMIT = 300

/** Empty lists are real empty/unavailable samples; full sections remain accessible. */
data class HomeSourcePreviews(
    val source: SourceHandle? = null,
    val catalog: List<OnlineTitle> = emptyList(),
    val latest: List<OnlineTitle> = emptyList(),
)
