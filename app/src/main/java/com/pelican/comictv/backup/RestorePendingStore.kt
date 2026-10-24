package com.pelican.comictv.backup

import android.util.AtomicFile
import java.io.File

class RestorePendingStore(directory: File) {
    private val file = AtomicFile(File(directory, "restore-pending.json"))
    @Synchronized fun snapshot(): BackupDocument = if (file.baseFile.exists()) file.openRead().use(BackupCodec::decode)
        else BackupDocument(createdAt = 0, comics = emptyList())
    @Synchronized fun write(document: BackupDocument) {
        val bytes = BackupCodec.encode(document)
        val out = file.startWrite()
        try { out.write(bytes); file.finishWrite(out) } catch (e: Throwable) { file.failWrite(out); throw e }
    }
    @Synchronized fun find(hash: String) = snapshot().comics.filter { it.sha256 == hash }.maxByOrNull { it.lastRead }
    @Synchronized fun finish(id: String) {
        val old = snapshot()
        write(old.copy(comics = old.comics.filterNot { it.id == id }, links = old.links.filterNot { it.comicId == id }))
    }
}
