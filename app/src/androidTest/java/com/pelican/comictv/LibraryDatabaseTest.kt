package com.pelican.comictv

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pelican.comictv.library.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LibraryDatabaseTest {
    @Test fun refreshedOnlinePageCountPreservesProgressAndPreferences() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            LibraryDatabase::class.java).build()
        try {
            val dao = db.comics()
            dao.insert(ComicEntity("online-record", "Capítulo", 20, currentPage = 12, lastRead = 123L, fit = 2, rtl = true))
            dao.updatePageCount("online-record", 30)
            val expanded = dao.get("online-record")!!
            assertEquals(12, expanded.currentPage)
            assertEquals(123L, expanded.lastRead)
            assertEquals(2, expanded.fit)
            assertTrue(expanded.rtl)
            dao.updatePageCount("online-record", 5)
            assertEquals(4, dao.get("online-record")!!.currentPage)
            assertEquals(123L, dao.get("online-record")!!.lastRead)
        } finally { db.close() }
    }
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
