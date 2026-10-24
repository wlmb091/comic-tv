package com.pelican.comictv.library

internal fun featuredComic(comics: List<ComicEntity>, focusedId: String?): ComicEntity? =
    comics.firstOrNull { it.id == focusedId }
        ?: comics.filter { it.lastRead > 0 && !it.hiddenFromHistory }.maxByOrNull { it.lastRead }
        ?: comics.firstOrNull()
