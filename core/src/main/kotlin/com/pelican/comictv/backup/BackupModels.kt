package com.pelican.comictv.backup

import com.pelican.comictv.sources.*

data class BackupOnline(val source: SourceHandle, val title: OnlineTitle, val chapter: OnlineChapter, val pin: String)
data class BackupComic(val id: String, val title: String, val pageCount: Int, val currentPage: Int = 0,
    val lastRead: Long = 0, val importedAt: Long = 0, val fit: Int = 0, val rtl: Boolean = false,
    val hiddenFromHistory: Boolean = false, val favorite: Boolean = false, val sha256: String? = null,
    val online: BackupOnline? = null, val imagePreset: String = "Original", val imageIntensity: Int = 1)
data class BackupCollection(val id: String, val name: String)
data class BackupLink(val comicId: String, val collectionId: String)
data class BackupAppearance(val preset: String = "Amber", val textSize: Int = 0, val highContrast: Boolean = false,
    val coverBackground: Boolean = false, val textPercent: Int? = null)
data class BackupDocument(val format: String = "ComicTVBackup", val version: Int = 1, val createdAt: Long,
    val comics: List<BackupComic>, val collections: List<BackupCollection> = emptyList(),
    val links: List<BackupLink> = emptyList(), val appearance: BackupAppearance = BackupAppearance())

fun mergeBackupComic(current: BackupComic, incoming: BackupComic): BackupComic {
    require(current.id == incoming.id)
    val latest = if (incoming.lastRead > current.lastRead) incoming else current
    return current.copy(currentPage = latest.currentPage.coerceIn(0, (current.pageCount - 1).coerceAtLeast(0)),
        lastRead = latest.lastRead, fit = latest.fit, rtl = latest.rtl, hiddenFromHistory = latest.hiddenFromHistory,
        favorite = current.favorite || incoming.favorite,
        imagePreset = latest.imagePreset, imageIntensity = latest.imageIntensity)
}

fun mergeBackupCollections(existing: List<BackupCollection>, incoming: List<BackupCollection>): Pair<List<BackupCollection>, Map<String, String>> {
    val all = existing.toMutableList(); val mapping = hashMapOf<String,String>()
    val byId = existing.associateBy {it.id}.toMutableMap()
    val byName = existing.associateBy {com.pelican.comictv.library.normalizeLibraryText(it.name)}.toMutableMap()
    for (row in incoming) {
        val key = com.pelican.comictv.library.validatedCollectionName(row.name).second
        val match = byId[row.id] ?: byName[key]
        val target = match ?: row.also { all += it;byId[it.id] = it;byName[key] = it }
        mapping[row.id] = target.id
    }
    require(all.size <= 2000) { "A mesclagem excederia 2.000 coleções. Nenhum dado foi restaurado." }
    return all to mapping
}
