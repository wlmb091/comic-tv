package com.pelican.comictv.reader

enum class ReaderLayout(val label: String) {
    Normal("Normal"), Gallery("Galeria"), Book("Livro");
    fun next() = entries[(ordinal + 1) % entries.size]
    companion object {
        fun restore(name: String?, legacyGallery: Boolean = false) =
            entries.firstOrNull { it.name == name } ?: if (legacyGallery) Gallery else Normal
    }
}
