package com.pelican.comictv.sources

data class SourceHandle(val packageName: String, val id: Long, val name: String, val language: String, val baseUrl: String)
data class InstalledExtension(val packageName: String, val name: String, val versionCode: Long, val versionName: String,
    val fingerprints: Set<String>, val compatible: Boolean)
data class OnlineTitle(val key: String, val title: String, val coverUrl: String? = null, val description: String? = null)
data class OnlineChapter(val key: String, val title: String, val number: Float = 0f)
data class OnlinePage(val index: Int, val pageUrl: String, val imageUrl: String? = null)
data class OnlineDetails(val title: OnlineTitle, val chapters: List<OnlineChapter>)
data class CatalogResult(val titles: List<OnlineTitle>, val hasNextPage: Boolean)

data class ExtensionEntry(val packageName: String, val name: String, val versionName: String, val versionCode: Long,
    val libVersion: String, val apkUrl: String, val iconUrl: String? = null,
    val languages: List<String> = emptyList(), val sourceNames: List<String> = emptyList())
data class ExtensionRepository(val baseUrl: String, val name: String, val fingerprint: String, val extensions: List<ExtensionEntry>)
