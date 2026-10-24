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
}
