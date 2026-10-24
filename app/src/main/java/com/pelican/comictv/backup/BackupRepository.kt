package com.pelican.comictv.backup

import android.content.Context
import android.util.AtomicFile
import androidx.room.withTransaction
import com.pelican.comictv.library.*
import com.pelican.comictv.reader.*
import com.pelican.comictv.sources.*
import com.pelican.comictv.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

data class RestoreResult(val added: Int, val updated: Int, val pending: Int)

class BackupRepository(private val context: Context, private val db: LibraryDatabase,
    private val library: LibraryRepository, private val online: OnlineReaderRepository,
    private val appearance: AppearancePreferences, private val images: ReaderImageStore,
    private val pending: RestorePendingStore) {
    private val directory = File(context.filesDir, "backup").apply { mkdirs() }
    private val journal = AtomicFile(File(directory, "restore-journal.json"))
    private val flags = context.getSharedPreferences("backup", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    suspend fun pendingCount() = withContext(Dispatchers.IO) { pending.snapshot().comics.size }
    suspend fun prepare(): File = withContext(Dispatchers.IO) {
        mutex.withLock { library.exclusiveStorage {
            recoverLocked()
            val dao = db.backups(); val rows = dao.comics()
            val pendingDoc = pending.snapshot()
            val entries = rows.map { row ->
                val settings = images.read(row.id)
                row.backup(settings).copy(sha256 = if (OnlineIdentity.isOnline(row.id)) null else sha256(library.originalFile(row.id)),
                    online = if (OnlineIdentity.isOnline(row.id)) online.backupMetadata(row.id) else null)
            }
            val ids = entries.map { it.id }.toSet()
            val all = entries + pendingDoc.comics.filter { it.id !in ids }
            val allIds = all.map {it.id}.toSet()
            val collections = dao.collections().map { BackupCollection(it.id, it.name) }
            val validCollections = collections.map { it.id }.toSet()
            val links = (dao.links().map { BackupLink(it.comicId, it.collectionId) } + pendingDoc.links)
                .filter { it.collectionId in validCollections && it.comicId in allIds }.distinct()
            val options = appearance.state.value
            val document = BackupDocument(createdAt = System.currentTimeMillis(), comics = all, collections = collections, links = links,
                appearance = BackupAppearance(options.preset.name, options.textSize, options.highContrast, options.coverBackground))
            val exportDirectory = File(context.cacheDir,"backup-exports").apply {mkdirs()}
            val date = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",java.util.Locale.ROOT).format(java.util.Date())
            val target = File.createTempFile("Comic-TV-${com.pelican.comictv.BuildConfig.VERSION_NAME}-$date-", ".ctvbackup",exportDirectory)
            val bytes = BackupCodec.encode(document)
            try { target.outputStream().use { it.write(bytes); it.fd.sync() }; target }
            catch (e: Exception) { target.delete(); throw e }
        } }
    }
    suspend fun preview(file: File): BackupDocument = withContext(Dispatchers.IO) { file.inputStream().use(BackupCodec::decode) }
    suspend fun restore(document: BackupDocument, restoreAppearance: Boolean): RestoreResult = withContext(Dispatchers.IO) {
        mutex.withLock { library.exclusiveStorage {
            recoverLocked(); BackupCodec.validate(document)
            val resolved = resolve(document)
            check(flags.edit().putBoolean("restore-appearance", restoreAppearance).commit())
            val bytes = BackupCodec.encode(resolved); val out = journal.startWrite()
            try { out.write(bytes); journal.finishWrite(out) } catch (e: Throwable) { journal.failWrite(out); throw e }
            withContext(NonCancellable) { val result = apply(resolved, restoreAppearance); journal.delete(); result }
        } }
    }
    suspend fun recover() = withContext(Dispatchers.IO) { mutex.withLock { library.exclusiveStorage { recoverLocked() } } }
    private suspend fun recoverLocked() {
        if (journal.baseFile.exists()) {
            val document = journal.openRead().use(BackupCodec::decode)
            withContext(NonCancellable) { apply(document, flags.getBoolean("restore-appearance", true)); journal.delete() }
        }
    }
    private suspend fun resolve(document: BackupDocument): BackupDocument {
        val dao = db.backups(); val current = dao.comics().associateBy { it.id }; val oldPending = pending.snapshot()
        val pendingById = oldPending.comics.associateBy { it.id }
        val resolved = document.copy(comics = document.comics.map { incoming ->
            val old = current[incoming.id]?.backup(images.read(incoming.id)) ?: pendingById[incoming.id]
            if (incoming.online == null && library.originalFile(incoming.id).isFile)
                require(sha256(library.originalFile(incoming.id)) == incoming.sha256) { "O original local não corresponde ao backup." }
            (old?.let { mergeBackupComic(it, incoming) } ?: incoming).copy(sha256 = incoming.sha256, online = incoming.online)
        })
        val (collections, map) = mergeBackupCollections(dao.collections().map { BackupCollection(it.id,it.name) }, resolved.collections)
        require((current.keys + pendingById.keys + resolved.comics.map { it.id }).toSet().size <= 20000) { "Limite de 20.000 edições na mesclagem." }
        val links = (dao.links().map { BackupLink(it.comicId,it.collectionId) } + oldPending.links + resolved.links.map { it.copy(collectionId = checkNotNull(map[it.collectionId])) }).distinct()
        require(links.size <= 100000) { "Limite de vínculos na mesclagem." }
        val deferred = (pendingById + resolved.comics.filter { it.online == null && !library.originalFile(it.id).isFile }.associateBy { it.id }).values.toList()
        val ids = deferred.map { it.id }.toSet(); val collectionIds = collections.map { it.id }.toSet()
        BackupCodec.encode(BackupDocument(createdAt = resolved.createdAt, comics = deferred, collections = collections,
            links = links.filter { it.comicId in ids && it.collectionId in collectionIds }))
        return resolved
    }
    private suspend fun apply(document: BackupDocument, restoreAppearance: Boolean): RestoreResult {
        val dao = db.backups()
        val current = dao.comics().associateBy { it.id }
        val oldPending = pending.snapshot()
        val oldPendingById = oldPending.comics.associateBy { it.id }
        val deferred = linkedMapOf<String, BackupComic>().apply { putAll(oldPendingById) }
        var added = 0; var updated = 0
        val live = arrayListOf<ComicEntity>()
        for (incoming in document.comics) {
            val old = current[incoming.id]
            if (!OnlineIdentity.isOnline(incoming.id) && !library.originalFile(incoming.id).isFile) {
                deferred[incoming.id] = oldPendingById[incoming.id]?.let { mergeBackupComic(it, incoming) } ?: incoming
            } else {
                val merged = old?.let { mergeBackupComic(it.backup(images.read(it.id)), incoming) } ?: incoming
                incoming.online?.let { online.restoreMetadata(incoming.id, it) }
                live += merged.entity()
                deferred.remove(incoming.id)
                if (old == null) added++ else updated++
            }
        }
        val collectionMap = hashMapOf<String, String>()
        db.withTransaction {
            val existing = dao.collections().toMutableList()
            for (row in document.collections) {
                val key = validatedCollectionName(row.name).second
                val match = existing.firstOrNull { it.id == row.id } ?: existing.firstOrNull { it.normalizedName == key }
                val target = match ?: CollectionEntity(row.id, row.name.trim(), key).also { db.collections().insert(it); existing += it }
                collectionMap[row.id] = target.id
            }
            dao.putComics(live)
            val liveIds = (current.keys + live.map { it.id }).toSet()
            dao.putLinks(document.links.filter { it.comicId in liveIds }.map { CollectionMembership(it.comicId, checkNotNull(collectionMap[it.collectionId])) })
        }
        val collections = dao.collections().map { BackupCollection(it.id, it.name) }
        val validIds = collections.map { it.id }.toSet()
        val deferredLinks = (oldPending.links + document.links.map { it.copy(collectionId = checkNotNull(collectionMap[it.collectionId])) })
            .filter { it.comicId in deferred && it.collectionId in validIds }.distinct()
        pending.write(BackupDocument(createdAt = document.createdAt, comics = deferred.values.toList(), collections = collections, links = deferredLinks))
        // Resolved journal choices survive date ties on replay; commit preferences in
        // one batch rather than thousands of disk commits for a large collection.
        images.saveAll(document.comics.associate { it.id to ReaderImageOptions.restore(it.imagePreset,it.imageIntensity) })
        if (restoreAppearance) {
            val a = document.appearance
            appearance.update(AppearanceOptions.restore(a.preset, a.textSize, a.highContrast, a.coverBackground))
        }
        return RestoreResult(added, updated, deferred.size)
    }
    private suspend fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(16384); while (true) {
            currentCoroutineContext().ensureActive(); val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count)
        } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

internal fun ComicEntity.backup(options: ReaderImageOptions) = BackupComic(id, title, pageCount, currentPage, lastRead, importedAt,
    fit, rtl, hiddenFromHistory, favorite, imagePreset = options.preset.name, imageIntensity = options.intensity)
internal fun BackupComic.entity() = ComicEntity(id, title, pageCount, currentPage, lastRead, importedAt, fit, rtl, hiddenFromHistory, favorite)
