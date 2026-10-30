package com.pelican.comictv.browser

import org.junit.Assert.*
import org.junit.Test

class BrowserSessionTest {
    @Test fun tabsAreBoundedAndClosingActiveSelectsNeighborWithoutLeavingZeroTabs() {
        val tabs = BrowserSession()
        val first = tabs.state.value.selectedId
        val second = tabs.newTab("https://example.org/second")!!
        assertEquals(second, tabs.state.value.selectedId)
        tabs.select(first); assertEquals(first, tabs.state.value.selectedId)
        tabs.close(first); assertEquals(second, tabs.state.value.selectedId)
        repeat(7) { assertNotNull(tabs.newTab()) }
        assertNull(tabs.newTab())
        tabs.state.value.tabs.map { it.id }.forEach(tabs::close)
        assertEquals(1, tabs.state.value.tabs.size); assertTrue(tabs.state.value.tabs.single().url.isBlank())
    }
    @Test fun historyAndFavoritesUseExactValidatedPageUrlsAndHaveLimits() {
        val tabs = BrowserSession(); val id = tabs.state.value.selectedId
        repeat(110) { tabs.page(id, "https://example.org/$it", "Page $it", true) }
        assertEquals(100, tabs.state.value.history.size)
        tabs.bookmark("https://example.org/one", "One"); assertEquals(1, tabs.state.value.bookmarks.size)
        tabs.bookmark("https://example.org/one", "One"); assertTrue(tabs.state.value.bookmarks.isEmpty())
        tabs.page(id, "javascript:alert(1)", "Bad", true)
        assertTrue(tabs.state.value.history.none { it.url.startsWith("javascript:") })
        assertEquals("https://example.org/109", tabs.state.value.tabs.single().url)
    }
    @Test fun restoredTabsAreSanitizedAndPreferencesStayBounded() {
        val session = BrowserSession(BrowserSessionState(tabs = listOf(BrowserTab("unsafe", "Bad", "file:///etc/passwd")), selectedId = "missing", textZoom = 900))
        assertEquals(1, session.state.value.tabs.size)
        assertTrue(session.state.value.tabs.single().url.isBlank())
        assertEquals(session.state.value.tabs.single().id, session.state.value.selectedId)
        assertEquals(150, session.state.value.textZoom)
    }
}
