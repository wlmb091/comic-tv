package com.pelican.comictv.sources

internal data class CatalogSnapshot(val titles: List<OnlineTitle>, val hasNextPage: Boolean, val page: Int,
    val storedAt: Long)
internal data class SnapshotMatch(val snapshot: CatalogSnapshot, val fresh: Boolean)

/** Even a populated same-mode list must consult TTL when a new navigation entry arrives. */
internal fun catalogQueryOnEntry(state: SourcesState, latest: Boolean, initialLoading: Boolean): CatalogSearchQuery? =
    if (initialLoading) null else state.activeSource?.let { CatalogSearchQuery(it, "", latest) }

internal fun appendCatalogPage(current: SourcesState, expected: CatalogSearchQuery, result: CatalogResult): SourcesState {
    if (current.activeSource != expected.source || current.query != expected.text || current.latest != expected.latest) return current
    return current.copy(titles = (current.titles + result.titles).distinctBy { it.key }, hasNextPage = result.hasNextPage)
}

/** Session-only metadata, never page URLs/trust. Oversized lists stay usable but are not retained. */
internal class CatalogSnapshots(private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val maxEntries: Int = 6, private val maxTitles: Int = 200, private val maxBytes: Long = 2L * 1024 * 1024) {
    private val entries = LinkedHashMap<CatalogSearchQuery, CatalogSnapshot>(8, .75f, true)
    @Synchronized fun get(query: CatalogSearchQuery): SnapshotMatch? = entries[query]?.let {
        SnapshotMatch(it, (clockMillis() - it.storedAt).coerceAtLeast(0) < if (query.latest) 45_000 else 180_000)
    }
    @Synchronized fun put(query: CatalogSearchQuery, result: CatalogResult, page: Int = 1): Boolean {
        require(page > 0)
        val value = CatalogSnapshot(result.titles.toList(), result.hasNextPage, page, clockMillis())
        entries.remove(query)
        if (value.titles.size > maxTitles || weight(query, value) > maxBytes) return false
        entries[query] = value
        trim()
        return entries.containsKey(query)
    }
    @Synchronized fun updateTitle(query: CatalogSearchQuery, title: OnlineTitle) {
        val old = entries[query] ?: return
        entries[query] = old.copy(titles = old.titles.map { if (it.key == title.key) title else it })
        trim()
    }
    @Synchronized fun clear() = entries.clear()
    private fun trim() {
        while (entries.isNotEmpty() && (entries.size > maxEntries || entries.entries.sumOf { weight(it.key, it.value) } > maxBytes)) {
            entries.remove(entries.keys.first())
        }
    }
    private fun weight(query: CatalogSearchQuery, snapshot: CatalogSnapshot): Long =
        512L + query.text.length * 2L + query.source.toString().length * 2L + snapshot.titles.sumOf { title ->
            256L + listOf(title.key, title.title, title.coverUrl, title.description, title.author, title.artist,
                title.publisher, title.characters, title.director).sumOf { (it?.length ?: 0) * 2L }
        }
}
