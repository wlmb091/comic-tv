package com.pelican.comictv.sources

import org.junit.Assert.*
import org.junit.Test

class OnlineIdentityTest {
    private val source = SourceHandle("extension.pkg", 17L, "Source", "pt-BR", "https://old.example")
    private val title = OnlineTitle("/title/a", "Title", "https://old.cover")
    private val chapter = OnlineChapter("/chapter/1", "Chapter 1", 1f)
    @Test fun identitySurvivesMutablePresentationAndCoverChanges() {
        assertEquals(OnlineIdentity.id(source, title, chapter), OnlineIdentity.id(source.copy(name = "New", baseUrl = "https://new.example"), title.copy(title = "New title", coverUrl = "https://new.cover"), chapter.copy(title = "Renamed")))
    }
    @Test fun chaptersAndSourcesDoNotShareProgress() {
        val id = OnlineIdentity.id(source, title, chapter)
        assertNotEquals(id, OnlineIdentity.id(source, title, chapter.copy(key = "/chapter/2")))
        assertNotEquals(id, OnlineIdentity.id(source.copy(id = 18), title, chapter))
        assertNotEquals(id, OnlineIdentity.id(source.copy(packageName = "other.pkg"), title, chapter))
        assertTrue(OnlineIdentity.isOnline(id))
    }
    @Test fun fieldBoundariesCannotCollide() {
        assertNotEquals(OnlineIdentity.id(source, title.copy(key = "ab"), chapter.copy(key = "c")), OnlineIdentity.id(source, title.copy(key = "a"), chapter.copy(key = "bc")))
    }
    @Test fun invalidIdsCannotBecomeRecordPaths() {
        assertFalse(OnlineIdentity.isOnline("online-../record"))
        assertFalse(OnlineIdentity.isOnline("online-"))
    }
}
