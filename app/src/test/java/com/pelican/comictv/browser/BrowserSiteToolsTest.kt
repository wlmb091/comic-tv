package com.pelican.comictv.browser

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class BrowserSiteToolsTest {
    @Test fun adblockMatchesDomainsAndSubdomainsOnly() {
        assertTrue(BrowserAdblock.blocks("doubleclick.net"))
        assertTrue(BrowserAdblock.blocks("ads.g.doubleclick.net"))
        assertTrue(BrowserAdblock.blocks("PAGEAD2.GOOGLESYNDICATION.COM"))
        assertFalse(BrowserAdblock.blocks("notdoubleclick.net"))
        assertFalse(BrowserAdblock.blocks("doubleclick.net.example.org"))
        assertFalse(BrowserAdblock.blocks("net"))
        assertFalse(BrowserAdblock.blocks(""))
        assertFalse(BrowserAdblock.blocks(null))
    }
    @Test fun adblockLeavesTheSitesAndServicesTheAppDependsOn() {
        listOf("www.hq-now.com", "static.hq-now.com", "admin.hq-now.com", "mega.nz", "site.ds-club.net", "site.soquadrinhos.com",
            "dcinfinita.blogspot.com", "archive.org", "www.google.com", "www.gstatic.com", "fonts.googleapis.com",
            "www.youtube.com", "adf.ly", "ouo.io", "shrinkme.io").forEach { assertFalse(it, BrowserAdblock.blocks(it)) }
    }
    @Test fun darkModeScriptAddsAndRemovesOneStyleAndKeepsImagesInverted() {
        val on = BrowserDarkMode.script(true)
        assertTrue(on.contains("if(e)return"))
        assertTrue(on.contains("appendChild"))
        assertTrue(BrowserDarkMode.CSS.startsWith("html{"))
        assertTrue(BrowserDarkMode.CSS.contains("img,video,"))
        assertTrue(BrowserDarkMode.script(false).contains("e.remove()"))
        assertFalse(BrowserDarkMode.script(false).contains("appendChild"))
    }
    @Test fun perSiteChoicesToggleByHostAndCoverSubdomains() {
        val session = BrowserSession()
        session.toggleDark("https://www.example.org/a")
        assertEquals(listOf("example.org"), session.state.value.darkSites)
        assertTrue(BrowserPolicy.siteMatches("https://blog.example.org/b", session.state.value.darkSites))
        assertFalse(BrowserPolicy.siteMatches("https://notexample.org/", session.state.value.darkSites))
        session.toggleDark("https://example.org/other")
        assertTrue(session.state.value.darkSites.isEmpty())
        session.toggleDark(null); session.toggleDark("file:///etc/passwd")
        assertTrue(session.state.value.darkSites.isEmpty())
    }
    @Test fun chosenSitesAllowHttpImagesOnlyOverHttps() {
        val session = BrowserSession()
        session.toggleHttpImages("https://quadrinhos.example/")
        val sites = session.state.value.httpImageSites
        assertTrue(BrowserPolicy.mixedImagesAllowed("https://quadrinhos.example/pagina", sites))
        assertFalse(BrowserPolicy.mixedImagesAllowed("http://quadrinhos.example/pagina", sites))
        assertFalse(BrowserPolicy.mixedImagesAllowed("https://outro.example/", sites))
        assertTrue(BrowserPolicy.mixedImagesAllowed("https://www.hq-now.com/", emptyList()))
    }
    @Test fun savedStatesFromOlderVersionsKeepWorking() {
        val old = Gson().fromJson("""{"textZoom":100,"cursorSpeed":1}""", BrowserSessionState::class.java)
        val session = BrowserSession(old)
        assertTrue(session.state.value.adblock)
        assertTrue(session.state.value.darkSites.isEmpty())
        assertTrue(session.state.value.httpImageSites.isEmpty())
        assertEquals(100, session.state.value.cursorSize)
    }
}
