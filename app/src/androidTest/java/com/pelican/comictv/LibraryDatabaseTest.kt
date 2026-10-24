package com.pelican.comictv

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pelican.comictv.library.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LibraryDatabaseTest {
    @Test fun hidingHistoryPreservesPositionAndReadingMakesItVisibleAgain() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            LibraryDatabase::class.java).build()
        try {
            val dao = db.comics()
            dao.insert(ComicEntity("history", "Título", 20, currentPage = 12, lastRead = 100, fit = 2, rtl = true))
            dao.setHistoryHidden("history", true)
            val hidden = dao.get("history")!!
            assertTrue(hidden.hiddenFromHistory)
            assertEquals(12, hidden.currentPage)
            assertEquals(100L, hidden.lastRead)
            assertEquals(2, hidden.fit)
            assertTrue(hidden.rtl)
            dao.setHistoryHidden("history", false)
            assertFalse(dao.get("history")!!.hiddenFromHistory)
            dao.setHistoryHidden("history", true)
            dao.saveProgress("history", 13, 200)
            assertFalse(dao.get("history")!!.hiddenFromHistory)
            assertEquals(13, dao.get("history")!!.currentPage)
        } finally { db.close() }
    }

    @Test fun migrationFromVersionOnePreservesLibraryAndHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "history-migration-" + java.util.UUID.randomUUID() + ".db"
        try {
            context.openOrCreateDatabase(name, 0, null).use { old ->
                old.execSQL("CREATE TABLE comics (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, pageCount INTEGER NOT NULL, currentPage INTEGER NOT NULL, lastRead INTEGER NOT NULL, importedAt INTEGER NOT NULL, fit INTEGER NOT NULL, rtl INTEGER NOT NULL)")
                old.execSQL("INSERT INTO comics VALUES ('old', 'Título', 30, 14, 123, 100, 2, 1)")
                old.version = 1
            }
            val db = Room.databaseBuilder(context, LibraryDatabase::class.java, name)
                .addMigrations(LibraryDatabase.MIGRATION_1_2).build()
            try {
                val comic = db.comics().get("old")!!
                assertEquals(14, comic.currentPage)
                assertEquals(123L, comic.lastRead)
                assertEquals(2, comic.fit)
                assertTrue(comic.rtl)
                assertFalse(comic.hiddenFromHistory)
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }

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
