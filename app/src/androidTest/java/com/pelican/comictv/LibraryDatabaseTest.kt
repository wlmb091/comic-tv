package com.pelican.comictv

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pelican.comictv.library.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LibraryDatabaseTest {
    @Test fun progressSurvivesReloadAndIsClamped() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            LibraryDatabase::class.java).build()
        try {
            db.comics().insert(ComicEntity("stable", "Título", 3))
            db.comics().saveProgress("stable", 99, 1)
            assertEquals(2, db.comics().get("stable")!!.currentPage)
            assertEquals("stable", db.comics().get("stable")!!.id)
        } finally { db.close() }
    }
}
