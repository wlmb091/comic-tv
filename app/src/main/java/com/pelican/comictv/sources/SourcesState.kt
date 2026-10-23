package com.pelican.comictv.sources

data class SavedRepository(val baseUrl: String, val name: String, val fingerprint: String)
data class SourcesState(
    val repositories: List<SavedRepository> = emptyList(),
    val selectedRepository: String? = null,
    val extensions: List<ExtensionEntry> = emptyList(),
    val installed: List<InstalledExtension> = emptyList(),
    val sources: List<SourceHandle> = emptyList(),
    val activeSource: SourceHandle? = null,
    val titles: List<OnlineTitle> = emptyList(),
    val details: OnlineDetails? = null,
    val latest: Boolean = false,
    val query: String = "",
    val hasNextPage: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)
