package com.pelican.comictv

import android.content.Context
import androidx.room.Room
import com.pelican.comictv.library.LibraryDatabase
import com.pelican.comictv.library.LibraryRepository
import com.pelican.comictv.mihon.MihonEngine
import com.pelican.comictv.sources.*

class AppContainer(context: Context) {
    private val app = context.applicationContext
    private val database = Room.databaseBuilder(app, LibraryDatabase::class.java, "comic-tv.db").addMigrations(LibraryDatabase.MIGRATION_1_2).build()
    val appearance = com.pelican.comictv.ui.AppearancePreferences(app)
    val library = LibraryRepository(app, database.comics())
    val sourcePreferences = SourcePreferences(app)
    val mihon = MihonEngine(app)
    val extensionInstaller = ExtensionInstaller(app)
    val online = OnlineReaderRepository(app, database.comics(), library, mihon, sourcePreferences)
}
