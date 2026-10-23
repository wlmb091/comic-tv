package com.pelican.comictv.sources

data class SavedRepository(val baseUrl: String, val name: String, val fingerprint: String)
data class SourceSelection(val packageName: String, val sourceId: Long) {
    fun resolve(sources: List<SourceHandle>): SourceHandle? = sources.firstOrNull { it.packageName == packageName && it.id == sourceId }
}
enum class SourceManagerPage { SOURCES, REPOSITORIES }
data class SourcesState(
    val managerPage: SourceManagerPage? = null,
    val repositories: List<SavedRepository> = emptyList(),
    val selectedRepository: String? = null,
    val extensions: List<ExtensionEntry> = emptyList(),
    val installed: List<InstalledExtension> = emptyList(),
    val sources: List<SourceHandle> = emptyList(),
    val loadFailures: Map<String, String> = emptyMap(),
    val activeSource: SourceHandle? = null,
    val titles: List<OnlineTitle> = emptyList(),
    val details: OnlineDetails? = null,
    val latest: Boolean = false,
    val query: String = "",
    val hasNextPage: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)
