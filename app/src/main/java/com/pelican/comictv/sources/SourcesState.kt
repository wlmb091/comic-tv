package com.pelican.comictv.sources

data class SavedRepository(val baseUrl: String, val name: String, val fingerprint: String)
data class SourceSelection(val packageName: String, val sourceId: Long) {
    fun resolve(sources: List<SourceHandle>): SourceHandle? = sources.firstOrNull { it.packageName == packageName && it.id == sourceId }
}
data class AvailableExtension(val repository: SavedRepository, val entry: ExtensionEntry)

internal fun AvailableExtension.isCurrentTrustedCandidate(
    available: List<AvailableExtension>, repositories: List<SavedRepository>,
): Boolean = this in available && repositories.any {
    it.baseUrl == repository.baseUrl && it.fingerprint == repository.fingerprint
}

enum class SourceManagerPage { SOURCES, REPOSITORIES, EXTENSIONS }
data class SourcesState(
    val managerPage: SourceManagerPage? = null,
    val repositories: List<SavedRepository> = emptyList(),
    val selectedRepository: String? = null,
    val extensions: List<ExtensionEntry> = emptyList(),
    val availableExtensions: List<AvailableExtension> = emptyList(),
    val extensionCatalogLoaded: Boolean = false,
    val installed: List<InstalledExtension> = emptyList(),
    val sources: List<SourceHandle> = emptyList(),
    val loadFailures: Map<String, String> = emptyMap(),
    val activeSource: SourceHandle? = null,
    val catalogLoaded: Boolean = false,
    val titles: List<OnlineTitle> = emptyList(),
    val details: OnlineDetails? = null,
    val latest: Boolean = false,
    val query: String = "",
    val hasNextPage: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val sourceRevision: Long = 0,
    val sourcesRefreshing: Boolean = false,
)
