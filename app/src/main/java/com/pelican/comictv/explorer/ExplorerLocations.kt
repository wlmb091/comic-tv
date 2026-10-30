package com.pelican.comictv.explorer

internal data class ExplorerPosition(val query: String = "", val filter: String = "Todos",
    val index: Int = 0, val offset: Int = 0)

/** Session-only view state; never contains file contents or credentials. */
internal class ExplorerLocations {
    private val entries = LinkedHashMap<String, ExplorerPosition>()
    fun save(key: String, position: ExplorerPosition) {
        entries.remove(key)
        entries[key] = position
        while (entries.size > 128) entries.remove(entries.keys.first())
    }
    fun restore(key: String) = entries[key] ?: ExplorerPosition()
}

internal fun explorerShouldRestoreFocus(editingQuery: Boolean, loading: Boolean) = !editingQuery && !loading
