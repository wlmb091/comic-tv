package com.pelican.comictv.library

import androidx.lifecycle.SavedStateHandle

class LibraryBrowseState(private val saved: SavedStateHandle) {
    val query = saved.getStateFlow("libraryQuery", "")
    val filter = saved.getStateFlow("libraryFilter", "all")
    fun setQuery(value: String) { saved["libraryQuery"] = value.take(200) }
    fun setFilter(value: String) { saved["libraryFilter"] = value }
    fun reconcile(ids: Set<String>) {
        if (filter.value.startsWith("collection:") && filter.value.removePrefix("collection:") !in ids) setFilter("all")
    }
}

fun toggleCollection(selected: Set<String>, id: String): Set<String> =
    if (id in selected) selected - id else selected + id
