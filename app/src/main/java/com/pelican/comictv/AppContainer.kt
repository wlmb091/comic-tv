package com.pelican.comictv

import android.content.Context
import androidx.room.Room
import com.pelican.comictv.library.LibraryDatabase
import com.pelican.comictv.library.LibraryRepository

class AppContainer(context: Context) {
    private val app = context.applicationContext
    private val database = Room.databaseBuilder(app, LibraryDatabase::class.java, "comic-tv.db").build()
    val library = LibraryRepository(app, database.comics())
}
