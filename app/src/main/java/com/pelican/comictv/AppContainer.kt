package com.pelican.comictv

import android.content.Context
import androidx.room.Room
import com.pelican.comictv.library.LibraryDatabase
import com.pelican.comictv.library.LibraryRepository
import com.pelican.comictv.mihon.MihonEngine
import com.pelican.comictv.sources.*

class AppContainer(context: Context) {
    private val app = context.applicationContext
    private val database = Room.databaseBuilder(app, LibraryDatabase::class.java, "comic-tv.db")
        .addMigrations(LibraryDatabase.MIGRATION_1_2, LibraryDatabase.MIGRATION_2_3).build()
    val readerImages = com.pelican.comictv.reader.ReaderImagePreferences(app)
    val appearance = com.pelican.comictv.ui.AppearancePreferences(app)
    val pendingRestores = com.pelican.comictv.backup.RestorePendingStore(java.io.File(app.filesDir, "backup"))
    val library = LibraryRepository(app, database.comics(), collectionDao = database.collections(), pendingRestores = pendingRestores)
    val sourcePreferences = SourcePreferences(app)
    val mihon = MihonEngine(app)
    val extensionInstaller = ExtensionInstaller(app)
    suspend fun warmReaderThumbnail(file: java.io.File) {
        coil3.SingletonImageLoader.get(app).execute(com.pelican.comictv.reader.readerThumbnailRequest(app, file))
    }
    val online = OnlineReaderRepository(app, database.comics(), library, mihon, sourcePreferences)
    val backup = com.pelican.comictv.backup.BackupRepository(app, database, library, online, appearance, readerImages, pendingRestores)
    val offlineStore = com.pelican.comictv.offline.OfflineStore(java.io.File(app.filesDir,"offline"))
    val downloads = com.pelican.comictv.offline.DownloadRepository(java.io.File(app.filesDir,"offline-queue.json"),offlineStore,
        online::prepareOffline,{id,index,file ->
            online.downloadOffline(id,index,file)
            if (index == 0) runCatching {library.writeOnlineCover(id,file)}
            Unit
        })
    val onlineReader = com.pelican.comictv.offline.OfflineFirstReaderRepository(offlineStore,online,library,onOfflineOpened = library::updatePageCount) {id,index,file ->
        library.externalImage(id,index,true,foreground = false) { target -> file.copyTo(target,overwrite = true); Unit }
    }
}
