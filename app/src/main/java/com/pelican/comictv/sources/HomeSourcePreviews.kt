package com.pelican.comictv.sources

/** Empty lists are real empty/unavailable samples; full sections remain accessible. */
data class HomeSourcePreviews(
    val source: SourceHandle? = null,
    val catalog: List<OnlineTitle> = emptyList(),
    val latest: List<OnlineTitle> = emptyList(),
)
